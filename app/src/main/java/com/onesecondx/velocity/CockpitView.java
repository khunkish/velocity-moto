package com.onesecondx.velocity;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import java.util.Locale;

/** Full-screen instruments. Static artwork is cached; only telemetry animates at vsync. */
public final class CockpitView extends View implements Choreographer.FrameCallback {
    public static final String[] LAYOUTS={"Focus · เน้นความเร็ว","Orbit · วงแหวน","Circuit · สปอร์ต","Moto · หน้าปัดรถ 12 รุ่น"};
    public static final String[] THEME_NAMES={"Onyx Black","Pure White","Graphite","Midnight Blue","Arctic White","Electric Cyan","Sakura Pink","Ember Orange","Ultraviolet","Lime Racing","Cobalt","Sandstone","Monochrome","Mint White","Solar Yellow","Crimson","Ocean","Rose White","Forest","Ice Blue","Pearl Gloss","Obsidian Gloss","Cloud Silver","Moon Violet"};
    public static final int[] BASES={0xff070a0d,0xffffffff,0xff191c21,0xff080f20,0xfff2f8fc,0xff04181d,0xff210f1c,0xff1f110b,0xff150f28,0xff10170a,0xff0c1742,0xfffaf3e8,0xff090909,0xfff2fff9,0xff1c1904,0xff20090e,0xff051c29,0xfffff5f7,0xff071c15,0xffedf7ff,0xfff5f6f8,0xff101216,0xffe9edf2,0xff151522};
    public static final int[] ACCENTS={0xffc8ff72,0xff111820,0xfff2c879,0xff7f9cff,0xff1677ad,0xff50e5f5,0xffff87c4,0xffffa05e,0xffb794ff,0xffb4f453,0xff91b4ff,0xff986c36,0xffffffff,0xff167858,0xffffdd51,0xffff657c,0xff5fd6ff,0xffb34070,0xff6cdbac,0xff2073b3,0xff667487,0xffc4d1df,0xff5e7188,0xffc3b9e5};
    public static boolean isLight(int theme){return theme==1||theme==4||theme==11||theme==13||theme==17||theme==19||theme==20||theme==22;}
    private final Paint paint=new Paint(3);
    private final RectF rect=new RectF();
    private final Path logo=new Path();
    private final Typeface digits=Typeface.create("sans-serif-condensed",Typeface.BOLD),ui=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    private Bitmap backdrop;
    private final BikeDashboard vehicle;
    public final BikeDashboard.Data data;
    public int theme=0,layout=0,model=0;
    public boolean showRpm=false,showGear=false,showStats=false;
    public float virtualRpm=0;
    public int virtualGear=0;
    public boolean audio=false;
    private int cachedTheme=-1,cachedLayout=-1,cachedModel=-1;
    private float downX,downY;
    private boolean multiTouch;
    public boolean swipeEnabled=true,smoothMotion=true;
    public interface SwipeListener{void swipe(int direction);}
    private SwipeListener swipeListener;
    public void setSwipeListener(SwipeListener listener){swipeListener=listener;}
    public void transitionDial(int direction){invalidateBackdrop();if(smoothMotion){animate().cancel();setTranslationX(direction*getResources().getDisplayMetrics().density*12);setAlpha(.8f);animate().translationX(0).alpha(1).setDuration(180).start();}}
    private static final int[] BIKE_LAYOUTS={2,1,2,2,2,1,1,2,0,1,0,1};
    private int visualLayout(){return layout==3?BIKE_LAYOUTS[Math.max(0,Math.min(11,model))]:layout;}
    private float value,rpm;
    private String speedText="0",rpmText="0",tripText="0.00",maxText="0",elapsedText="00:00",statusText="GPS / READY";
    private int lastSpeed=-1,lastRpm=-1;
    private long lastFrame,hintUntil=SystemClock.uptimeMillis()+8000;
    private boolean attached;
    private boolean scheduled;
    public long frames=0,lateFrames=0;
    public double averageFrameMs=0;
    public CockpitView(Context context){super(context);vehicle=new BikeDashboard(context,0);data=vehicle.data;setClickable(true);setFocusable(true);setContentDescription("หน้าปัดความเร็ว แตะเพื่อเปิดเมนู");}
    private int fg(){return isLight(theme)?0xff111c28:0xfff4f6f6;}
    private int muted(){return isLight(theme)?0xff667585:0xff80919e;}
    private void label(Canvas c,String s,float x,float y,float size,int color,boolean number){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTypeface(number?digits:ui);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(size);c.drawText(s,x,y,paint);}
    private void stroke(Canvas c,float x,float y,float x2,float y2,int color,float width){paint.setStyle(Paint.Style.STROKE);paint.setColor(color);paint.setStrokeWidth(width);c.drawLine(x,y,x2,y2,paint);paint.setStyle(Paint.Style.FILL);}
    public static void drawLogo(Canvas c,Paint p,Path shape,float x,float y,float size,int color){shape.reset();shape.moveTo(x,y);shape.lineTo(x+size*.24f,y);shape.lineTo(x+size*.49f,y+size*.55f);shape.lineTo(x+size*.75f,y);shape.lineTo(x+size,y);shape.lineTo(x+size*.59f,y+size*.88f);shape.lineTo(x+size*.39f,y+size*.88f);shape.close();p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawPath(shape,p);p.setStrokeWidth(size*.055f);c.drawLine(x+size*.77f,y+size*.61f,x+size*1.04f,y+size*.61f,p);c.drawLine(x+size*.7f,y+size*.78f,x+size*.96f,y+size*.78f,p);}
    public void updateText(){
        tripText=String.format(Locale.US,"%.2f",data.distance);maxText=String.format(Locale.US,"%.0f",data.maximum);elapsedText=BikeDashboard.duration(data.elapsed);
        statusText=data.demo?"DEMO / ค่าจำลอง":data.overspeed?"SPEED LIMIT":data.fresh?"GPS LIVE":"GPS / "+(data.running?"SEARCHING":"READY");
        setContentDescription("ความเร็ว "+Math.round(data.speed)+(data.miles?" ไมล์ต่อชั่วโมง":" กิโลเมตรต่อชั่วโมง")+(data.demo?" ค่าจำลอง":"")+" แตะเพื่อเปิดเมนู");
    }
    public void hideHint(){hintUntil=0;}
    public void snapToData(){value=(float)data.speed;rpm=virtualRpm;lastSpeed=Math.round(value);lastRpm=Math.round(rpm/100)*100;speedText=Integer.toString(lastSpeed);rpmText=Integer.toString(lastRpm);}
    private void schedule(){if(attached&&!scheduled&&getWindowVisibility()==VISIBLE){scheduled=true;Choreographer.getInstance().postFrameCallback(this);}}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE){lastFrame=0;schedule();}else{Choreographer.getInstance().removeFrameCallback(this);scheduled=false;}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;lastFrame=0;schedule();}
    @Override protected void onDetachedFromWindow(){attached=false;scheduled=false;Choreographer.getInstance().removeFrameCallback(this);if(backdrop!=null){backdrop.recycle();backdrop=null;}super.onDetachedFromWindow();}
    @Override public void doFrame(long nanos){scheduled=false;if(!attached||getWindowVisibility()!=VISIBLE)return;if(lastFrame==0)snapToData();float dt=lastFrame==0?1/60f:Math.min(.08f,(nanos-lastFrame)/1e9f);if(lastFrame!=0){double ms=(nanos-lastFrame)/1e6;frames++;averageFrameMs+=(ms-averageFrameMs)/Math.min(frames,120);if(ms>25)lateFrames++;}lastFrame=nanos;
        float blend=smoothMotion?1-(float)Math.exp(-dt*15):1;value+=(float)(data.speed-value)*blend;rpm+=(virtualRpm-rpm)*blend;
        int n=(int)Math.round(value);if(n!=lastSpeed){speedText=Integer.toString(n);lastSpeed=n;}int r=Math.round(rpm/100)*100;if(r!=lastRpm){rpmText=Integer.toString(r);lastRpm=r;}
        invalidate();schedule();
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);invalidateBackdrop();vehicle.layout(0,0,w,h);}
    public void invalidateBackdrop(){cachedTheme=-1;invalidate();}
    private void staticArt(){
        if(getWidth()==0||getHeight()==0)return;
        if(backdrop==null||backdrop.getWidth()!=getWidth()||backdrop.getHeight()!=getHeight()){if(backdrop!=null)backdrop.recycle();backdrop=Bitmap.createBitmap(getWidth(),getHeight(),Bitmap.Config.ARGB_8888);cachedTheme=-1;}
        if(cachedTheme==theme&&cachedLayout==layout&&cachedModel==model)return;cachedTheme=theme;cachedLayout=layout;cachedModel=model;
        Canvas c=new Canvas(backdrop);c.drawColor(BASES[theme]);boolean wide=getWidth()>getHeight();float sx=getWidth()/(wide?1600f:1000f),sy=getHeight()/(wide?900f:1600f);c.scale(sx,sy);
        if(theme>=20){paint.setStyle(Paint.Style.FILL);paint.setShader(new LinearGradient(0,0,wide?1600:1000,wide?900:1600,isLight(theme)?new int[]{0xffffffff,BASES[theme],0xffe4e9ef}:new int[]{0xff292d36,BASES[theme],0xff080a0e},new float[]{0,.45f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,wide?1600:1000,wide?900:1600,paint);paint.setShader(null);}
        drawLogo(c,paint,logo,wide?80:70,wide?62:90,wide?36:48,ACCENTS[theme]);paint.setTextAlign(Paint.Align.LEFT);paint.setTypeface(ui);paint.setTextSize(wide?24:28);paint.setColor(fg());c.drawText(layout==3?BikeDashboard.NAMES[model]:"VELOCITY",wide?137:144,wide?91:126,paint);
        if(visualLayout()==1){float cx=wide?800:500,cy=wide?454:755,rad=wide?326:390;rect.set(cx-rad,cy-rad,cx+rad,cy+rad);paint.setStyle(Paint.Style.STROKE);paint.setColor(isLight(theme)?0xffdbe3e9:0xff24303a);paint.setStrokeWidth(5);c.drawArc(rect,140,260,false,paint);for(int i=0;i<=40;i++){double a=Math.toRadians(140+i*6.5);float x=(float)Math.cos(a),y=(float)Math.sin(a);stroke(c,cx+x*(rad-14),cy+y*(rad-14),cx+x*(rad-(i%5==0?39:26)),cy+y*(rad-(i%5==0?39:26)),muted(),i%5==0?3:1.5f);}}
        if(visualLayout()==2){stroke(c,wide?170:110,wide?190:380,wide?1430:890,wide?190:380,isLight(theme)?0xffd7e2e8:0xff2a353d,10);label(c,"GPS SPEED",wide?800:500,wide?255:450,wide?17:22,muted(),false);}
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);theme=Math.max(0,Math.min(theme,BASES.length-1));
        if(layout==3&&(showRpm||showGear)){vehicle.model=model;data.customTheme=true;data.customBase=BASES[theme];data.customAccent=ACCENTS[theme];data.day=isLight(theme);data.showRpm=showRpm;data.showGear=showGear;data.showStats=showStats;data.virtualEngine=showRpm||showGear;data.virtualRpm=virtualRpm;data.virtualGear=virtualGear;double raw=data.speed;data.speed=value;vehicle.draw(c);data.speed=raw;drawMenu(c);return;}
        staticArt();if(backdrop!=null)c.drawBitmap(backdrop,0,0,null);
        boolean wide=getWidth()>getHeight();c.save();c.scale(getWidth()/(wide?1600f:1000f),getHeight()/(wide?900f:1600f));int color=data.overspeed?0xffff536e:ACCENTS[theme];float center=wide?800:500;
        label(c,statusText,wide?1320:770,wide?90:125,wide?17:23,data.demo?0xffc78b41:data.fresh?color:muted(),false);
        if(visualLayout()==1){float cy=wide?454:755,rad=wide?326:390;rect.set(center-rad,cy-rad,center+rad,cy+rad);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7);paint.setColor(color);c.drawArc(rect,140,Math.min(260,value/(data.miles?186f:300f)*260),false,paint);paint.setStyle(Paint.Style.FILL);}
        float baseline=wide?570:885,size=wide?390:470;if(visualLayout()==1){baseline=wide?555:870;size=wide?280:350;}if(speedText.length()>2)size*=.83f;
        label(c,speedText,center,baseline,size,fg(),true);label(c,data.miles?"mph":"km/h",center,baseline+(wide?67:88),wide?30:39,color,false);
        if(showRpm){float x=wide?330:175,y=wide?166:300,w=wide?940:650;for(int i=0;i<32;i++){float xx=x+i*w/32;paint.setColor(rpm/15000>i/32f?color:(isLight(theme)?0xffd4dde5:0xff25323c));rect.set(xx,y,xx+w/32-5,y+(wide?15:20));c.drawRoundRect(rect,2,2,paint);}label(c,"SIM RPM  "+rpmText,center,y+(wide?45:57),wide?15:20,muted(),false);}
        if(showGear){label(c,"SIM GEAR",wide?1350:790,wide?414:1100,wide?15:20,muted(),false);label(c,virtualGear==0?"N":Integer.toString(virtualGear),wide?1350:790,wide?495:1197,wide?85:105,color,true);}
        if(showStats){float y=wide?775:1330;label(c,"TRIP",wide?360:220,y,wide?15:20,muted(),false);label(c,tripText+(data.miles?" mi":" km"),wide?360:220,y+46,wide?28:34,fg(),true);label(c,"MAX",center,y,wide?15:20,muted(),false);label(c,maxText,center,y+46,wide?28:34,fg(),true);label(c,"TIME",wide?1240:780,y,wide?15:20,muted(),false);label(c,elapsedText,wide?1240:780,y+46,wide?28:34,fg(),true);}
        if(!showStats&&SystemClock.uptimeMillis()<hintUntil)label(c,"ปัดซ้าย–ขวาเปลี่ยนหน้าปัด · แตะเพื่อเปิดเมนู",center,wide?805:1420,wide?17:23,muted(),false);
        if(audio)label(c,"SYNTH AUDIO",wide?180:190,wide?851:1512,wide?12:16,muted(),false);
        c.restore();drawMenu(c);
    }
    private void drawMenu(Canvas c){float density=getResources().getDisplayMetrics().density;float x=getWidth()-30*density,y=getHeight()-30*density;paint.setColor(muted());paint.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)c.drawCircle(x+(i-1)*6*density,y,1.7f*density,paint);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=event.getX();downY=event.getY();multiTouch=false;return true;
            case MotionEvent.ACTION_POINTER_DOWN:multiTouch=true;return true;
            case MotionEvent.ACTION_MOVE:if(Math.abs(event.getX()-downX)>ViewConfiguration.get(getContext()).getScaledTouchSlop()&&getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_UP:float dx=event.getX()-downX,dy=event.getY()-downY;float minimum=Math.max(64*getResources().getDisplayMetrics().density,getWidth()*.12f);if(!multiTouch&&Math.abs(dx)>=minimum&&Math.abs(dx)>Math.abs(dy)*1.4f){if(swipeEnabled&&swipeListener!=null)swipeListener.swipe(dx<0?1:-1);}else if(!multiTouch&&Math.hypot(dx,dy)<ViewConfiguration.get(getContext()).getScaledTouchSlop())performClick();return true;
            case MotionEvent.ACTION_CANCEL:multiTouch=true;return true;
            default:return true;
        }
    }
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle arguments){if(swipeEnabled&&swipeListener!=null&&(action==4096||action==8192)){swipeListener.swipe(action==4096?1:-1);return true;}return super.performAccessibilityAction(action,arguments);}
}
