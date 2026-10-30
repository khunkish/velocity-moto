package com.onesecondx.velocity;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Isolated emulator verification. Resets only the active test session. */
public final class V3Verification extends Instrumentation {
    private MainActivity activity;
    private int checks;
    private boolean performance;
    private Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private boolean contains(View view,String value){if(view instanceof TextView&&((TextView)view).getText().toString().contains(value))return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),value))return true;}return false;}
    @Override public void onCreate(Bundle args){super.onCreate(args);performance=args!=null&&"performance".equals(args.getString("mode"));start();}
    @Override public void onStart(){Bundle output=new Bundle();AtomicReference<Throwable> failure=new AtomicReference<>();try{
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        if(performance){measurePerformance(output,failure);return;}
        runOnMainSync(()->{try{
            call("pause");set("demo",false);set("engineEnabled",false);set("page",0);call("render");
            ViewGroup root=(ViewGroup)get("root");check(root.getChildCount()==1&&root.getChildAt(0)instanceof CockpitView,"Only cockpit on home; no nested menus");
            ((CockpitView)get("cockpit")).performClick();check(((Dialog)get("controlSheet")).isShowing(),"Tap opens hidden controls");
            set("page",6);call("render");ViewGroup credit=(ViewGroup)get("body");check(contains(credit,"@khunkish")&&contains(credit,"One Second Hub Co., Ltd."),"Author and company credits");check(contains(credit,"GitHub")&&contains(credit,"Instagram")&&contains(credit,"YouTube"),"All three social link controls");
            set("page",0);call("render");check(get("controlSheet")==null,"Closing controls leaves no sheet");
            check(EngineSynth.rpmForSpeed(100,0)>EngineSynth.rpmForSpeed(0,0),"Simulated RPM responds to speed");check(EngineSynth.gearForSpeed(0)==0&&EngineSynth.gearForSpeed(200)==6,"Virtual gearbox bounds");
        }catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new RuntimeException(failure.get());
        File dir=new File(getTargetContext().getFilesDir(),"v3-verification");dir.mkdirs();
        for(int theme=0;theme<20;theme++){
            final int selected=theme;AtomicReference<Bitmap> result=new AtomicReference<>();
            runOnMainSync(()->{CockpitView view=new CockpitView(activity);view.theme=selected;view.layout=selected%3;view.data.speed=88;view.data.demo=true;view.hideHint();view.updateText();view.layout(0,0,1600,900);view.snapToData();Bitmap b=Bitmap.createBitmap(1600,900,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));result.set(b);});
            Bitmap bitmap=result.get();try(FileOutputStream file=new FileOutputStream(new File(dir,"theme-"+theme+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();checks++;
        }
        AtomicReference<long[]> hashes=new AtomicReference<>();runOnMainSync(()->{long[] values=new long[4];for(int flags=0;flags<4;flags++){CockpitView view=new CockpitView(activity);view.layout=3;view.model=1;view.data.speed=120;view.showRpm=(flags&1)!=0;view.showGear=(flags&2)!=0;view.virtualRpm=9000;view.virtualGear=4;view.layout(0,0,1280,720);view.snapToData();Bitmap b=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));long hash=1;for(int y=0;y<720;y+=5)for(int x=0;x<1280;x+=5)hash=hash*31+b.getPixel(x,y);values[flags]=hash;b.recycle();}hashes.set(values);});Set<Long> distinct=new HashSet<>();for(long h:hashes.get())distinct.add(h);check(distinct.size()==4,"RPM/gear independently alter rendered output");
        Set<Long> soundHashes=new HashSet<>();short[] samples=new short[2048];long start=System.nanoTime();
        for(int engine=0;engine<8;engine++)for(int exhaust=0;exhaust<8;exhaust++){EngineSynth synth=new EngineSynth();long sum=0,hash=1;for(int chunk=0;chunk<16;chunk++){synth.render(samples,engine,exhaust,70,.35f,true,48000);for(short sample:samples){checkSample(sample);sum+=(long)sample*sample;hash=hash*31+sample;}}check(sum>0,"Non-silent engine/exhaust "+engine+"/"+exhaust);soundHashes.add(hash);}
        check(soundHashes.size()==64,"All 64 engine/exhaust combinations differ");double synthesisSeconds=(System.nanoTime()-start)/1e9;
        EngineSynth mute=new EngineSynth();mute.render(samples,0,0,90,.4f,false,48000);boolean silent=true;for(short sample:samples)if(sample!=0)silent=false;check(silent,"Disabled audio produces digital silence");
        runOnMainSync(()->{try{set("demo",true);set("running",true);set("segmentStart",SystemClock.elapsedRealtime());set("speed",80/3.6);set("engineEnabled",true);call("updateLive");}catch(Throwable t){failure.set(t);}});
        SystemClock.sleep(1200);
        runOnMainSync(()->{try{EngineAudio audio=(EngineAudio)get("engineAudio");check(audio.error.isEmpty()&&audio.isActive(),"AudioTrack streams simulated engine");call("pause");check(!audio.isActive(),"Pause stops AudioTrack");set("engineEnabled",false);set("demo",false);call("resetTrip");set("page",0);call("render");}catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new RuntimeException(failure.get());
        output.putString("stream","PASS: "+checks+" checks; hidden UI, credits, 20 themes, independent RPM/gear, 64 unique bounded PCM engines/exhausts, mute, AudioTrack start/stop. DSP generation "+String.format(Locale.US,"%.2f",synthesisSeconds)+" s for 43.69 s of PCM.\n");finish(-1,output);
    }catch(Throwable t){output.putString("stream","FAIL: "+t+"\n");finish(0,output);}}
    private void checkSample(short sample){if(Math.abs((int)sample)>32760)throw new AssertionError("PCM out of bounds");}
    private void measurePerformance(Bundle output,AtomicReference<Throwable> failure)throws Exception{
        HandlerThread thread=new HandlerThread("frame-metrics");thread.start();java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();long[] totals=new long[2048],draws=new long[2048];
        Window.OnFrameMetricsAvailableListener listener=(window,metrics,dropped)->{int index=count.getAndIncrement();if(index<totals.length){totals[index]=metrics.getMetric(FrameMetrics.TOTAL_DURATION);draws[index]=metrics.getMetric(FrameMetrics.DRAW_DURATION);}};
        runOnMainSync(()->{try{set("page",0);set("displayLayout",1);set("showRpm",true);set("showGear",true);set("demo",true);set("running",true);set("segmentStart",SystemClock.elapsedRealtime());set("demoStart",SystemClock.elapsedRealtime());set("engineEnabled",true);call("render");activity.getWindow().addOnFrameMetricsAvailableListener(listener,new Handler(thread.getLooper()));}catch(Throwable t){failure.set(t);}});
        SystemClock.sleep(2000);count.set(0);SystemClock.sleep(8000);
        AtomicReference<String> summary=new AtomicReference<>();runOnMainSync(()->{try{activity.getWindow().removeOnFrameMetricsAvailableListener(listener);CockpitView view=(CockpitView)get("cockpit");summary.set(String.format(Locale.US,"Display %.1f Hz; Choreographer interval %.2f ms (%.1f callbacks/s)",activity.getWindowManager().getDefaultDisplay().getRefreshRate(),view.averageFrameMs,1000/view.averageFrameMs));call("pause");set("engineEnabled",false);set("demo",false);set("showRpm",false);set("showGear",false);set("displayLayout",0);call("resetTrip");call("render");}catch(Throwable t){failure.set(t);}});thread.quitSafely();if(failure.get()!=null)throw new RuntimeException(failure.get());
        int n=Math.min(count.get(),totals.length);Arrays.sort(totals,0,n);Arrays.sort(draws,0,n);String result=summary.get()+String.format(Locale.US,"; %d frames/8 seconds; p95 total %.2f ms, p95 onDraw %.2f ms. Emulator measurements, not a hardware guarantee.",n,n>0?totals[(int)((n-1)*.95)]/1e6:0,n>0?draws[(int)((n-1)*.95)]/1e6:0);
        try(FileOutputStream file=new FileOutputStream(new File(getTargetContext().getFilesDir(),"v3-performance.txt"))){file.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));}output.putString("stream",result+"\n");finish(-1,output);
    }
}
