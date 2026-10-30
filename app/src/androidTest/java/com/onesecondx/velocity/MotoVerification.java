package com.onesecondx.velocity;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/** Device instrumentation with no third-party test runtime. Only installed via androidTest. */
public class MotoVerification extends Instrumentation {
    private MainActivity activity;
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private Object call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(activity);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private Location fix(double longitude,long age,float speed){Location l=new Location("gps");l.setLatitude(13.7563);l.setLongitude(longitude);l.setAccuracy(5);l.setAltitude(10);l.setSpeed(speed);l.setSpeedAccuracyMetersPerSecond(.2f);l.setBearing(90);l.setTime(System.currentTimeMillis()-age);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-age*1000000);return l;}
    @Override public void onStart(){
        Bundle result=new Bundle();final AtomicReference<Throwable> failure=new AtomicReference<>();
        try{
            Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)startActivitySync(intent);waitForIdleSync();
            runOnMainSync(()->{try{
                call("pause");call("resetTrip");set("miles",false);set("alerts",false);set("sound",false);set("running",true);set("segmentStart",SystemClock.elapsedRealtime()-10000);
                activity.onLocationChanged(fix(100.5018,2000,22.222222f));
                check(Math.abs((double)get("maxSpeed")*3.6-80)<.1,"GPS speed conversion");
                set("lastSample",0L);activity.onLocationChanged(fix(100.5022,0,25));
                check((double)get("distance")>40&&(double)get("distance")<45,"Two GPS points accumulate geodesic distance");
                check(((JSONArray)get("route")).length()==2,"Route samples persisted in memory");
                double maximum=(double)get("maxSpeed"),meters=(double)get("distance");
                activity.onLocationChanged(fix(100.6,10000,80));check((double)get("maxSpeed")==maximum&&(double)get("distance")==meters,"Stale GPS fix rejected");
                Location poor=fix(100.7,0,90);poor.setAccuracy(100);activity.onLocationChanged(poor);check((double)get("maxSpeed")==maximum,"Inaccurate GPS fix rejected");
                call("recordLap");check(((JSONArray)get("laps")).length()==1,"Manual lap recorded");check(((JSONArray)get("laps")).getLong(0)>=10000,"Lap duration uses trip clock");
                call("pause");long duration=(long)call("duration");check(!(boolean)get("running"),"Pause stops running state");call("recordLap");check(((JSONArray)get("laps")).length()==1,"Paused lap not recorded");check((long)call("duration")==duration,"Paused duration stable");
                call("exportGpx");String gpx=(String)get("exportBody");check(gpx.contains("http://www.topografix.com/GPX/1/1")&&gpx.contains("lat=\"13.7563\""),"GPX contains namespace and real route points");
                javax.xml.parsers.DocumentBuilderFactory factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);org.w3c.dom.Document document=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(gpx.getBytes(java.nio.charset.StandardCharsets.UTF_8)));check(document.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1","trkpt").getLength()==2,"GPX XML parses with two route points");
                File output=new File(getTargetContext().getFilesDir(),"verified-route.gpx");activity.onActivityResult(80,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(output)));check(new String(java.nio.file.Files.readAllBytes(output.toPath()),java.nio.charset.StandardCharsets.UTF_8).equals(gpx),"GPX document writer writes exact file bytes");
                JSONArray original=(JSONArray)get("history");org.json.JSONObject trip=new org.json.JSONObject();trip.put("date",System.currentTimeMillis());trip.put("bike","CBR650R");trip.put("meters",1000);trip.put("seconds",60);trip.put("max",25);trip.put("laps",new JSONArray().put(30000));set("history",new JSONArray().put(trip));call("exportCsv");String csv=(String)get("exportBody");check(csv.contains("CBR650R,1000.0,60,25.0,")&&csv.contains("average_mps,laps"),"CSV uses explicit SI units and lap count");File csvOutput=new File(getTargetContext().getFilesDir(),"verified-trips.csv");activity.onActivityResult(80,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(csvOutput)));check(java.nio.file.Files.size(csvOutput.toPath())==csv.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,"CSV document writer creates file");set("history",original);
                set("demo",false);call("resetTrip");
            }catch(Throwable t){failure.set(t);}});
            if(failure.get()!=null)throw new RuntimeException(failure.get());
            File dir=new File(getTargetContext().getFilesDir(),"moto-verification");dir.mkdirs();
            for(int i=0;i<BikeDashboard.NAMES.length;i++){for(int appearance=0;appearance<2;appearance++){
                final int model=i,mode=appearance;final AtomicReference<Bitmap> rendered=new AtomicReference<>();
                runOnMainSync(()->{BikeDashboard view=new BikeDashboard(activity,model);view.preview=true;view.data.demo=true;view.data.day=mode==1;view.data.battery=96;view.data.time="10:30";view.data.lap=93700;view.data.bestLap=91200;view.data.distance=12.8;view.data.maximum=120;view.data.average=62;view.layout(0,0,1600,768);Bitmap bitmap=Bitmap.createBitmap(1600,768,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));rendered.set(bitmap);});
                Bitmap bitmap=rendered.get();try(FileOutputStream out=new FileOutputStream(new File(dir,"bike-"+model+(mode==1?"-day":"-night")+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();checks++;
            }}
            result.putString("stream","PASS: "+checks+" checks; GPS, stale/poor fix rejection, pause, laps, GPX XML, all 12 models × day/night renders.\n");finish(-1,result);
        }catch(Throwable t){result.putString("stream","FAIL: "+t+"\n");finish(0,result);}
    }
}

