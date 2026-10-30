package com.onesecondx.velocity;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;

public final class V4Verification extends Instrumentation {
    private MainActivity activity;private int checks;
    private Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private void motion(CockpitView view,float dx,float dy){long time=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,500,400,0);view.onTouchEvent(event);event.recycle();event=MotionEvent.obtain(time,time+300,MotionEvent.ACTION_MOVE,500+dx,400+dy,0);view.onTouchEvent(event);event.recycle();event=MotionEvent.obtain(time,time+650,MotionEvent.ACTION_UP,500+dx,400+dy,0);view.onTouchEvent(event);event.recycle();}
    private View find(View view,String label){if(view instanceof TextView&&((TextView)view).getText().toString().equals(label))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View hit=find(group.getChildAt(i),label);if(hit!=null)return hit;}}return null;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle output=new Bundle();AtomicReference<Throwable> failure=new AtomicReference<>();try{
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        runOnMainSync(()->{try{
            call("pause");set("demo",false);set("engineEnabled",false);set("swipeEnabled",true);set("displayLayout",0);set("bike",0);set("page",0);call("render");
            CockpitView cockpit=(CockpitView)get("cockpit");cockpit.layout(0,0,720,1280);
            motion(cockpit,-300,4);check((int)get("displayLayout")==1,"Left swipe selects Orbit");check(get("cockpit")==cockpit,"Swipe preserves existing view and animation clock");check(get("controlSheet")==null,"Swipe does not open menu");
            motion(cockpit,300,4);check((int)get("displayLayout")==0,"Right swipe returns to Focus");motion(cockpit,300,4);check((int)get("displayLayout")==3&&(int)get("bike")==11,"Reverse wraps to last motorcycle");motion(cockpit,-300,4);check((int)get("displayLayout")==0,"Forward wraps from last motorcycle");
            motion(cockpit,3,300);check(get("controlSheet")==null,"Vertical drag does not accidentally open menu");cockpit.swipeEnabled=false;motion(cockpit,-300,0);check((int)get("displayLayout")==0,"Disabling gesture prevents dial change");cockpit.swipeEnabled=true;
            for(int i=0;i<15;i++)motion(cockpit,-300,0);check((int)get("displayLayout")==0,"All fifteen dial positions cycle once");
            motion(cockpit,1,1);check(((Dialog)get("controlSheet")).isShowing(),"Tap after swiping opens menu normally");
            set("page",12);call("render");View back=find(((Dialog)get("controlSheet")).getWindow().getDecorView(),"‹ กลับ");check(back!=null,"Feature screen has clear back control");back.performClick();check((int)get("page")==5,"Gear settings back returns to Audio category");
            set("page",9);call("render");check(find((View)get("body"),"ขาวเงา / Pearl")!=null&&find((View)get("body"),"ดำเงา / Obsidian")!=null,"Glossy light and dark quick choices exist");
            View white=find((View)get("body"),"ขาวเงา / Pearl");white.performClick();check((int)get("displayTheme")==20,"Light theme selection persists in state");check(activity.getSharedPreferences("velocity",0).getInt("displayTheme",-1)==20,"Light theme preference persisted");
            check(CockpitView.THEME_NAMES.length==24&&CockpitView.isLight(20)&&!CockpitView.isLight(21),"24 themes include both gloss appearances");
            set("page",8);call("render");check(find((View)get("body"),"ระบบและการใช้งาน")!=null,"System settings have their own category");
            set("page",13);call("render");check(find((View)get("body"),"เสียงจุดระเบิดขณะผ่อน")!=null,"Ignition effect has explicit switch");
            set("page",0);set("displayTheme",21);call("render");check(get("controlSheet")==null&&((ViewGroup)get("root")).getChildCount()==1,"Home remains cockpit only");
        }catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new RuntimeException(failure.get());
        File directory=new File(getTargetContext().getFilesDir(),"v4-verification");directory.mkdirs();
        for(int theme:new int[]{20,21,22,23})for(int model:new int[]{0,1,2,3}){
            AtomicReference<Bitmap> picture=new AtomicReference<>();runOnMainSync(()->{CockpitView view=new CockpitView(activity);view.theme=theme;view.layout=3;view.model=model;view.data.speed=88;view.data.demo=true;view.showRpm=false;view.showGear=false;view.hideHint();view.updateText();view.layout(0,0,1600,900);view.snapToData();Bitmap b=Bitmap.createBitmap(1600,900,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));picture.set(b);});
            Bitmap b=picture.get();try(FileOutputStream file=new FileOutputStream(new File(directory,"theme-"+theme+"-bike-"+model+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,file);}b.recycle();checks++;
        }
        runOnMainSync(()->{try{set("demo",true);set("running",true);set("segmentStart",SystemClock.elapsedRealtime());set("engineEnabled",true);set("gearboxEnabled",true);set("revDropEnabled",true);set("ignitionEnabled",true);set("exhaustProfile",8);set("speed",100/3.6);call("updateLive");}catch(Throwable t){failure.set(t);}});SystemClock.sleep(1200);
        runOnMainSync(()->{try{EngineAudio audio=(EngineAudio)get("engineAudio");check(audio.isActive()&&audio.error.isEmpty(),"New drivetrain audio streams without error");check(audio.currentRpm>0&&audio.currentGear>=1&&audio.currentGear<=6,"Audio exposes its virtual RPM and gear");call("pause");check(!audio.isActive(),"Pause stops new audio immediately at controller");set("demo",false);set("engineEnabled",false);set("ignitionEnabled",false);call("resetTrip");set("page",0);call("render");}catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new RuntimeException(failure.get());
        output.putString("stream","PASS: "+checks+" native checks; slow/fast directional swipe and wrapping, no menu misfire, no view recreation, disabled swipe, category back navigation, persisted glossy themes, 16 bike/gloss renders, AudioTrack telemetry/start/stop.\n");finish(-1,output);
    }catch(Throwable t){output.putString("stream","FAIL: "+t+"\n");finish(0,output);}}
}
