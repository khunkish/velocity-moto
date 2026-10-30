package com.onesecondx.velocity;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Offline, foreground-only GPS speedometer. No server or location upload. */
public class MainActivity extends Activity implements LocationListener {
    private int BG=Color.rgb(12,17,21), PANEL=Color.rgb(22,29,34), INK=Color.rgb(242,245,241), MUTED=Color.rgb(139,153,159);
    private int accent=Color.rgb(185,247,107);
    private android.content.SharedPreferences prefs;
    private LocationManager gps;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root, body;
    private int page=0, style=0, limit=100;
    private boolean miles=false, keepAwake=true, alerts=true, running=false, demo=false, subscribed=false;
    private double speed=0, distance=0, maxSpeed=0;
    private long elapsed=0, segmentStart=0, lastFix=0, lastAlert=0, demoStart=0;
    private Location previous;
    private float accuracy=0;
    private JSONArray history=new JSONArray();
    private BikeDashboard bikeView;
    private int bike=0, category=0, brightness=80;
    private boolean focus=false, day=false, sound=false;
    private double heading=Double.NaN, altitude=Double.NaN;
    private int batteryPercent=0;
    private long batteryReadAt=0;
    private boolean position=false;
    private long lapStart=0, lastRouteSaved=0, lastSample=0;
    private JSONArray laps=new JSONArray(), route=new JSONArray(), lastRoute=new JSONArray();
    private String exportBody="";
    private TextView toolsInfo,lapInfo;
    private View traceView;
    private final DriveModel virtualDrive=new DriveModel();
    private long driveUpdatedAt;
    private boolean swipeEnabled=true,swipeHaptics=true,smoothMotion=true,gearboxEnabled=true,revDropEnabled=true,shiftFx=true,ignitionEnabled=false,starterEnabled=false;
    private int bassLevel=50,textureLevel=40,ignitionLevel=10,revDropLevel=45,themeFilter=0;
    private CockpitView cockpit;
    private EngineAudio engineAudio;
    private Toast dialToast;
    private Dialog controlSheet;
    private ScrollView sheetScroll;
    private int displayTheme=0,displayLayout=0,engineProfile=0,exhaustProfile=0,engineVolume=25,orientationMode=0;
    private boolean showRpm=false,showGear=false,showStats=false,engineEnabled=false,inForeground=false,validSpeedFresh=false,autoDemo=true;
    private float demoKmh=60;
    private long lastTick=0;
    private final ArrayList<Float> speedTrace=new ArrayList<>();
    private final Runnable ticker=new Runnable(){ public void run(){
        if(demo && running){
            double t=(SystemClock.elapsedRealtime()-demoStart)/1000.0;
            speed=(autoDemo?48+27*Math.sin(t/8)+7*Math.sin(t/2):demoKmh)/3.6;
            distance+=speed*(lastTick==0?.25:Math.min(1,(SystemClock.elapsedRealtime()-lastTick)/1000.0)); maxSpeed=Math.max(maxSpeed,speed);
        }else if(lastFix>0 && SystemClock.elapsedRealtime()-lastFix>5000){ speed=0; previous=null; }
        lastTick=SystemClock.elapsedRealtime();
        updateLive(); handler.postDelayed(this,250);
    }};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("velocity",MODE_PRIVATE);
        displayTheme=prefs.getInt("displayTheme",0);displayLayout=prefs.getInt("displayLayout",0);showRpm=prefs.getBoolean("showRpm",false);showGear=prefs.getBoolean("showGear",false);showStats=prefs.getBoolean("showStats",false);engineEnabled=prefs.getBoolean("engineEnabled",false);engineProfile=prefs.getInt("engineProfile",0);exhaustProfile=prefs.getInt("exhaustProfile",0);engineVolume=prefs.getInt("engineVolume",25);orientationMode=prefs.getInt("orientationMode",0);
        swipeEnabled=prefs.getBoolean("swipeEnabled",true);swipeHaptics=prefs.getBoolean("swipeHaptics",true);smoothMotion=prefs.getBoolean("smoothMotion",true);gearboxEnabled=prefs.getBoolean("gearboxEnabled",true);revDropEnabled=prefs.getBoolean("revDropEnabled",true);shiftFx=prefs.getBoolean("shiftFx",true);ignitionEnabled=prefs.getBoolean("ignitionEnabled",false);starterEnabled=prefs.getBoolean("starterEnabled",false);bassLevel=prefs.getInt("bassLevel",50);textureLevel=prefs.getInt("textureLevel",40);ignitionLevel=prefs.getInt("ignitionLevel",10);revDropLevel=prefs.getInt("revDropLevel",45);
        if(prefs.getInt("polishEdition",0)<4){if(displayTheme==0)displayTheme=21;else if(displayTheme==1)displayTheme=20;prefs.edit().putBoolean("swipeEnabled",swipeEnabled).putBoolean("swipeHaptics",swipeHaptics).putBoolean("smoothMotion",smoothMotion).putBoolean("gearboxEnabled",gearboxEnabled).putBoolean("revDropEnabled",revDropEnabled).putBoolean("shiftFx",shiftFx).putBoolean("ignitionEnabled",ignitionEnabled).putBoolean("starterEnabled",starterEnabled).putInt("bassLevel",bassLevel).putInt("textureLevel",textureLevel).putInt("ignitionLevel",ignitionLevel).putInt("revDropLevel",revDropLevel).putInt("displayTheme",displayTheme).putInt("polishEdition",4).apply();}
        if(state!=null)exportBody=state.getString("exportBody","");
        style=prefs.getInt("style",0); miles=prefs.getBoolean("miles",false); limit=prefs.getInt("limit",100);
        accent=prefs.getInt("accent",accent); keepAwake=prefs.getBoolean("awake",true); alerts=prefs.getBoolean("alerts",true);
        bike=prefs.getInt("bike",0); focus=prefs.getBoolean("focus",false);day=prefs.getBoolean("day",false);sound=prefs.getBoolean("sound",false);brightness=prefs.getInt("brightness",80);
        if(prefs.getInt("edition",1)<2){style=3;prefs.edit().putInt("edition",2).putInt("style",3).apply();}
        try{route=new JSONArray(prefs.getString("route","[]"));lastRoute=new JSONArray(prefs.getString("lastRoute","[]"));laps=new JSONArray(prefs.getString("laps","[]"));lapStart=prefs.getLong("lapStart",0);}catch(JSONException ignored){}
        try { history=new JSONArray(prefs.getString("history","[]")); } catch(JSONException ignored){}
        distance=Double.longBitsToDouble(prefs.getLong("distance",0)); maxSpeed=Double.longBitsToDouble(prefs.getLong("maximum",0)); elapsed=prefs.getLong("elapsed",0);
        gps=(LocationManager)getSystemService(LOCATION_SERVICE);
        engineAudio=new EngineAudio(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        applyOrientation();preferRefreshRate();applyBrightness();render(); handler.post(ticker);
    }
    private int dp(float x){ return (int)(x*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable shape(int color,int radius){ GradientDrawable d=new GradientDrawable(); d.setColor(color);if(color==PANEL){d.setColors(CockpitView.isLight(displayTheme)?new int[]{0xffffffff,0xfff8fafc}:new int[]{0xff242d39,0xff1b222c});d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);d.setStroke(dp(1),CockpitView.isLight(displayTheme)?0xffdfe6ed:0xff303b48);} d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String s,float size,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setFontFeatureSettings("tnum"); return t; }
    private TextView bold(String s,float size,int color){ TextView t=text(s,size,color); t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",Typeface.NORMAL)); return t; }
    private LinearLayout column(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void gap(LinearLayout l,int h){ View v=new View(this); l.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private void line(LinearLayout l){ View v=new View(this); v.setBackgroundColor(CockpitView.isLight(displayTheme)?0xffd6dee7:0xff2d3847); l.addView(v,new LinearLayout.LayoutParams(-1,dp(1))); }
    private void add(LinearLayout l,View v,int h){ l.addView(v,new LinearLayout.LayoutParams(-1,h<0?h:dp(h))); }
    private TextView button(String title,boolean primary,Runnable click){ TextView t=bold(title,15,primary?BG:INK); t.setGravity(Gravity.CENTER); t.setBackground(shape(primary?accent:PANEL,18)); t.setPadding(dp(12),dp(12),dp(12),dp(12)); t.setMinHeight(dp(52));t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END); t.setOnClickListener(v->click.run()); return t; }
    private void saveSettings(){ prefs.edit().putBoolean("swipeEnabled",swipeEnabled).putBoolean("swipeHaptics",swipeHaptics).putBoolean("smoothMotion",smoothMotion).putBoolean("gearboxEnabled",gearboxEnabled).putBoolean("revDropEnabled",revDropEnabled).putBoolean("shiftFx",shiftFx).putBoolean("ignitionEnabled",ignitionEnabled).putBoolean("starterEnabled",starterEnabled).putInt("bassLevel",bassLevel).putInt("textureLevel",textureLevel).putInt("ignitionLevel",ignitionLevel).putInt("revDropLevel",revDropLevel).putInt("displayTheme",displayTheme).putInt("displayLayout",displayLayout).putBoolean("showRpm",showRpm).putBoolean("showGear",showGear).putBoolean("showStats",showStats).putBoolean("engineEnabled",engineEnabled).putInt("engineProfile",engineProfile).putInt("exhaustProfile",exhaustProfile).putInt("engineVolume",engineVolume).putInt("orientationMode",orientationMode).putInt("style",style).putInt("bike",bike).putBoolean("focus",focus).putBoolean("day",day).putBoolean("sound",sound).putInt("brightness",brightness).putBoolean("miles",miles).putInt("limit",limit).putInt("accent",accent).putBoolean("awake",keepAwake).putBoolean("alerts",alerts).apply(); awake();applyBrightness(); }
    private void persistTrip(){ if(demo)return; prefs.edit().putLong("distance",Double.doubleToLongBits(distance)).putLong("maximum",Double.doubleToLongBits(maxSpeed)).putLong("elapsed",duration()).apply(); }
    private void awake(){ if(keepAwake&&running)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
    private long duration(){ return elapsed+(running?SystemClock.elapsedRealtime()-segmentStart:0); }
    private String unit(){ return miles?"mph":"km/h"; }
    private double converted(double metersPerSecond){ return metersPerSecond*(miles?2.236936292:3.6); }
    private String fixed(double n,int places){ return String.format(Locale.US,"%."+places+"f",n); }
    private String clock(){ long s=duration()/1000; return String.format(Locale.US,"%02d:%02d:%02d",s/3600,s/60%60,s%60); }

    private void render(){
        applyUiPalette();
        int requestedPage=page;int scrollY=sheetScroll==null?0:sheetScroll.getScrollY();int oldPage=controlSheet==null?-1:controlSheet.getWindow().getDecorView().getId();
        if(controlSheet!=null){controlSheet.setOnDismissListener(null);controlSheet.dismiss();controlSheet=null;}
        page=requestedPage;toolsInfo=null;lapInfo=null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        root=column();root.setBackgroundColor(CockpitView.BASES[displayTheme]);cockpit=new CockpitView(this);root.addView(cockpit,new LinearLayout.LayoutParams(-1,-1));setContentView(root);
        if(prefs.getBoolean("controlsSeen",false)&&prefs.getBoolean("swipeHintSeen",false))cockpit.hideHint();cockpit.swipeEnabled=swipeEnabled;cockpit.smoothMotion=smoothMotion;cockpit.setSwipeListener(direction->cycleDial(direction));cockpit.setOnClickListener(v->{prefs.edit().putBoolean("controlsSeen",true).apply();page=7;render();});updateLive();
        if(page!=0){showSheet(page);if(oldPage==page&&sheetScroll!=null)sheetScroll.post(()->sheetScroll.scrollTo(0,scrollY));}
    }
    private int parentPage(int section){return section==9||section==1?3:section==11||section==12||section==13?5:section==10||section==6?8:7;}
    private int tabFor(int section){return section==1||section==9?3:section>=11&&section<=13?5:section==6||section==10?8:section==4?2:section;}
    private void showSheet(int section){
        controlSheet=new Dialog(this);LinearLayout panel=column();panel.setPadding(dp(18),dp(12),dp(18),dp(8));panel.setBackground(shape(BG,26));panel.setElevation(dp(18));
        LinearLayout head=row();if(section!=7){TextView back=button("‹ กลับ",false,()->{page=parentPage(section);render();});back.setMinHeight(0);back.setPadding(dp(8),0,dp(8),0);back.setTextSize(12);head.addView(back,new LinearLayout.LayoutParams(dp(72),dp(40)));}
        TextView name=bold("VELOCITY / CONTROL",12,INK);name.setGravity(Gravity.CENTER);name.setSingleLine(true);head.addView(name,new LinearLayout.LayoutParams(0,dp(40),1));TextView close=button("ปิด ×",false,()->{page=0;render();});close.setMinHeight(0);close.setPadding(dp(8),0,dp(8),0);close.setTextSize(12);head.addView(close,new LinearLayout.LayoutParams(dp(62),dp(40)));add(panel,head,40);gap(panel,12);
        LinearLayout tabs=row();String[] labels={"เริ่ม","หน้าปัด","เสียง","ทริป","ระบบ"};int[] ids={7,3,5,2,8};for(int i=0;i<labels.length;i++){final int target=ids[i];TextView t=bold(labels[i],12,tabFor(section)==target?INK:MUTED);t.setGravity(Gravity.CENTER);t.setSingleLine(true);if(tabFor(section)==target)t.setBackground(shape(PANEL,12));t.setOnClickListener(v->{page=target;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(dp(2),0,dp(2),0);tabs.addView(t,lp);}add(panel,tabs,42);gap(panel,8);line(panel);
        sheetScroll=new ScrollView(this);sheetScroll.setVerticalScrollBarEnabled(false);body=column();body.setPadding(0,dp(18),0,dp(22));sheetScroll.addView(body);panel.addView(sheetScroll,new LinearLayout.LayoutParams(-1,0,1));
        if(section==7)controls();else if(section==1)bikeStyles();else if(section==2)trips();else if(section==3)settings();else if(section==4)rideTools();else if(section==5)audioSettings();else if(section==6)credits();else if(section==8)systemSettings();else if(section==9)themeSettings();else if(section==10)alertSettings();else if(section==11)exhaustSettings();else if(section==12)gearSettings();else if(section==13)ignitionSettings();
        controlSheet.setContentView(panel);controlSheet.setOnDismissListener(d->{controlSheet=null;sheetScroll=null;page=0;});controlSheet.show();Window window=controlSheet.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=.35f;window.setAttributes(params);window.setLayout(Math.min(dp(620),getResources().getDisplayMetrics().widthPixels),(int)(getResources().getDisplayMetrics().heightPixels*.94));window.getDecorView().setId(section);
    }
    private void menuRow(String title,String description,int destination){menuRow(title,description,()->{page=destination;render();});}
    private void menuRow(String title,String description,Runnable click){LinearLayout card=column();card.setPadding(dp(15),dp(13),dp(15),dp(13));card.setBackground(shape(PANEL,17));TextView heading=bold(title+"  ›",16,INK);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(heading);gap(card,4);TextView subtitle=text(description,12,MUTED);subtitle.setMaxLines(2);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(subtitle);card.setOnClickListener(v->click.run());add(body,card,-2);gap(body,10);}
    private void twoButtons(String a,Runnable first,String b,Runnable second){LinearLayout r=row();TextView left=button(a,false,first),right=button(b,false,second);left.setTextSize(13);right.setTextSize(13);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1);lp.setMargins(0,0,dp(5),0);r.addView(left,lp);lp=new LinearLayout.LayoutParams(0,dp(50),1);lp.setMargins(dp(5),0,0,0);r.addView(right,lp);add(body,r,50);gap(body,10);}
    private void controls(){
        title("พร้อมออกเดินทาง",demo?"DEMO · ข้อมูลจำลอง":running?"กำลังบันทึก GPS · แตะพักได้ทุกเมื่อ":"ปัดซ้าย–ขวาเปลี่ยนหน้าปัด · แตะเพื่อเปิดเมนู");
        add(body,button(running?"พักการเดินทาง":"เริ่มจับความเร็ว GPS",true,()->{toggle();if(running){page=0;render();}}),54);gap(body,12);
        twoButtons("หน้าปัดก่อนหน้า",()->{page=0;render();cycleDial(-1);},"หน้าปัดถัดไป",()->{page=0;render();cycleDial(1);});
        twoButtons(engineEnabled?"ปิดเสียงเครื่องยนต์":"เปิดเสียงเครื่องยนต์",()->{engineEnabled=!engineEnabled;saveSettings();render();},"ความเร็วล้วน",()->{displayLayout=0;showGear=showRpm=showStats=false;saveSettings();page=0;render();});
        section("ปรับแต่งอย่างง่าย");menuRow("หน้าปัดและธีม",CockpitView.LAYOUTS[displayLayout]+" · "+CockpitView.THEME_NAMES[displayTheme],3);menuRow("ห้องเสียง",EngineSynth.ENGINES[engineProfile]+" · "+EngineSynth.EXHAUSTS[exhaustProfile],5);menuRow("ทริปและเครื่องมือ","ประวัติ / LAP / เส้นทาง / ส่งออกไฟล์",2);
        twoButtons(demo?"หยุดสาธิต":"ทดลอง / DEMO",()->toggleDemo(),"จบและบันทึกทริป",()->finishTrip());
    }
    private void applyUiPalette(){boolean light=CockpitView.isLight(displayTheme);BG=light?0xffedf1f5:0xff11161d;PANEL=light?0xffffffff:0xff1c232d;INK=light?0xff1a2736:0xffedf3f8;MUTED=light?0xff6b7a8b:0xff94a3b3;accent=CockpitView.ACCENTS[displayTheme];}
    private void selectDisplayTheme(int selected){displayTheme=selected;saveSettings();render();}
    private void cycleDial(int direction){int current=displayLayout==3?3+bike:displayLayout;int next=Math.floorMod(current+direction,15);if(next<3)displayLayout=next;else{displayLayout=3;bike=next-3;}prefs.edit().putBoolean("swipeHintSeen",true).apply();saveSettings();if(cockpit!=null){updateLive();cockpit.transitionDial(direction);if(swipeHaptics)cockpit.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}if(dialToast!=null)dialToast.cancel();dialToast=Toast.makeText(this,displayLayout==3?BikeDashboard.NAMES[bike]:CockpitView.LAYOUTS[displayLayout],Toast.LENGTH_SHORT);dialToast.show();}
    private void applyOrientation(){setRequestedOrientation(orientationMode==1?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:orientationMode==2?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);}
    private void preferRefreshRate(){Display display=getWindowManager().getDefaultDisplay();Display.Mode current=display.getMode(),best=current;for(Display.Mode mode:display.getSupportedModes())if(mode.getPhysicalWidth()==current.getPhysicalWidth()&&mode.getPhysicalHeight()==current.getPhysicalHeight()&&mode.getRefreshRate()>best.getRefreshRate())best=mode;WindowManager.LayoutParams p=getWindow().getAttributes();p.preferredDisplayModeId=best.getModeId();getWindow().setAttributes(p);}
    private void title(String en,String thai){ add(body,bold(en,24,INK),-2); gap(body,4); add(body,text(thai,13,MUTED),-2); gap(body,24); }
    private void trips(){
        title("ประวัติการเดินทาง","การเดินทางของคุณ เก็บไว้เฉพาะบนเครื่อง");
        if(history.length()>0){add(body,button("ส่งออกประวัติ CSV",false,()->exportCsv()),48);gap(body,12);add(body,button("ส่งออกเส้นทางทริปล่าสุด GPX",false,()->exportGpx()),48);gap(body,18);}
        if(history.length()==0){ gap(body,28); add(body,bold("↗",64,accent),-2); gap(body,20); add(body,bold("เส้นทางใหม่รออยู่",24,INK),-2); gap(body,10); add(body,text("เริ่มบันทึกจากหน้าปัด แล้วกดจบทริป\nเพื่อเก็บระยะทาง เวลา และสถิติความเร็ว",14,MUTED),-2); gap(body,28); add(body,button("ไปเริ่มทริปแรก  →",true,()->{page=0;render();}),56); }
        for(int i=history.length()-1;i>=0;i--){JSONObject item=history.optJSONObject(i); if(item==null)continue; LinearLayout card=column(); card.setPadding(dp(18),dp(18),dp(18),dp(18)); card.setBackground(shape(PANEL,18));
            card.addView(text(new SimpleDateFormat("dd MMM yyyy · HH:mm",new Locale("th","TH")).format(new Date(item.optLong("date"))),12,MUTED)); gap(card,12);
            card.addView(bold(fixed(item.optDouble("meters")/(miles?1609.344:1000),2)+" "+(miles?"mi":"km"),29,INK)); gap(card,8);
            card.addView(text("เวลา "+item.optLong("seconds")/60+" นาที  ·  สูงสุด "+fixed(converted(item.optDouble("max")),0)+" "+unit(),13,MUTED));card.addView(text(item.optString("bike","VELOCITY")+" · แตะดูรายละเอียด",11,accent));card.setOnClickListener(v->tripDetails(item));add(body,card,-2); gap(body,12);
        }
        if(history.length()>0){ gap(body,10); add(body,button("ล้างประวัติทริป",false,()->new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle("ล้างประวัติทั้งหมด?").setMessage("ข้อมูลทริปที่บันทึกจะถูกลบจากเครื่อง").setNegativeButton("ยกเลิก",null).setPositiveButton("ลบ",(d,w)->{history=new JSONArray();lastRoute=new JSONArray();prefs.edit().putString("history","[]").putString("lastRoute","[]").apply();render();}).show()),52); }
    }
    private void settings(){
        title("หน้าปัดและท่าทาง","เลือกหน้าตาและข้อมูลที่ต้องการเห็น");
        twoButtons("Focus / ความเร็วล้วน",()->{displayLayout=0;saveSettings();render();},"Orbit / วงแหวน",()->{displayLayout=1;saveSettings();render();});twoButtons("Circuit / สปอร์ต",()->{displayLayout=2;saveSettings();render();},"Moto / รุ่นรถ",()->chooseBike());
        menuRow("เลือกหน้าปัดรถ",BikeDashboard.NAMES[bike]+" · ปัดวนครบ 15 หน้าปัดได้",()->chooseBike());menuRow("ธีมและโหมดสี",CockpitView.THEME_NAMES[displayTheme]+" · 24 ธีม รวมขาวเงา / ดำเงา",9);
        section("ข้อมูลบนหน้าปัด");toggleSetting("แสดงรอบเครื่องจำลอง","ซ่อนแถบ SIM RPM ได้โดยไม่ปิดเสียง",showRpm,v->{showRpm=v;saveSettings();});toggleSetting("แสดงเกียร์จำลอง","แยกจากการจำลองเสียงเข้าเกียร์",showGear,v->{showGear=v;saveSettings();});toggleSetting("แสดงสถิติด้านล่าง","ระยะทาง / สูงสุด / เวลา",showStats,v->{showStats=v;saveSettings();});
        section("การปัดหน้าจอ");toggleSetting("ปัดเปลี่ยนหน้าปัด","ปัดแนวนอน · แตะเปิดเมนู · ปัดแนวตั้งไม่เปิดเมนู",swipeEnabled,v->{swipeEnabled=v;saveSettings();updateLive();});toggleSetting("สั่นตอบรับเมื่อปัด","แรงสั่นสั้น ๆ เมื่อเปลี่ยนหน้าปัด",swipeHaptics,v->{swipeHaptics=v;saveSettings();});menuRow("แกลเลอรีหน้าปัดรถ","ดูตัวอย่างครบ 12 รุ่น",1);
    }
    private void themeSettings(){
        title("ธีมละมุน","เลือกโหมดสว่าง มืด หรือโทนสีที่ชอบ");twoButtons("ขาวเงา / Pearl",()->selectDisplayTheme(20),"ดำเงา / Obsidian",()->selectDisplayTheme(21));
        LinearLayout filters=row();String[] labels={"ทั้งหมด","สว่าง","มืด"};for(int i=0;i<3;i++){final int selected=i;TextView t=button(labels[i],themeFilter==i,()->{themeFilter=selected;render();});t.setTextSize(12);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(2),0,dp(2),0);filters.addView(t,lp);}add(body,filters,44);gap(body,14);
        ArrayList<Integer> colors=new ArrayList<>();for(int i=0;i<CockpitView.THEME_NAMES.length;i++)if(themeFilter==0||themeFilter==1&&CockpitView.isLight(i)||themeFilter==2&&!CockpitView.isLight(i))colors.add(i);
        for(int r=0;r<colors.size();r+=3){LinearLayout palette=row();for(int col=0;col<3;col++){if(r+col>=colors.size()){palette.addView(new View(this),new LinearLayout.LayoutParams(0,dp(84),1));continue;}final int pick=colors.get(r+col);LinearLayout tile=column();tile.setPadding(dp(4),dp(4),dp(4),dp(4));TextView swatch=bold(displayTheme==pick?"✓":"",22,CockpitView.ACCENTS[pick]);swatch.setGravity(Gravity.CENTER);swatch.setBackground(shape(CockpitView.BASES[pick],14));tile.addView(swatch,new LinearLayout.LayoutParams(-1,dp(48)));TextView name=text(CockpitView.THEME_NAMES[pick],10,displayTheme==pick?INK:MUTED);name.setGravity(Gravity.CENTER);name.setMaxLines(2);tile.addView(name);tile.setContentDescription(CockpitView.THEME_NAMES[pick]);tile.setOnClickListener(v->selectDisplayTheme(pick));palette.addView(tile,new LinearLayout.LayoutParams(0,dp(84),1));}add(body,palette,84);}
    }
    private void systemSettings(){
        title("ระบบและการใช้งาน","ตั้งค่าพื้นฐานและการแจ้งเตือนแยกเป็นหมวด");
        menuRow("การวางหน้าจอ",new String[]{"อัตโนมัติ","แนวนอน","แนวตั้ง"}[orientationMode],()->new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle("การวางหน้าจอ").setSingleChoiceItems(new String[]{"อัตโนมัติ","แนวนอน","แนวตั้ง"},orientationMode,(d,i)->{orientationMode=i;saveSettings();d.dismiss();page=0;applyOrientation();render();}).setNegativeButton("ปิด",null).show());
        menuRow("หน่วยความเร็ว",miles?"mph / ไมล์ต่อชั่วโมง":"km/h / กิโลเมตรต่อชั่วโมง",()->{miles=!miles;saveSettings();render();});settingLabel("ความสว่าง (%)","ใช้เฉพาะในแอป");slider(brightness,10,100,v->{brightness=v;saveSettings();});toggleSetting("เปิดหน้าจอค้าง","ระหว่างบันทึกทริป",keepAwake,v->{keepAwake=v;saveSettings();});toggleSetting("แอนิเมชันนุ่มนวล","ตัวเลข / วงแหวน / การเปลี่ยนหน้าปัด",smoothMotion,v->{smoothMotion=v;saveSettings();updateLive();});
        menuRow("GPS และเตือนความเร็ว",limit+" km/h · เสียงเตือน / สั่น",10);menuRow("เครดิตและเกี่ยวกับแอป","@khunkish · One Second Hub Co., Ltd.",6);
    }
    private void alertSettings(){title("GPS และการเตือน","การเตือนแยกจากเสียงเครื่องยนต์จำลอง");toggleSetting("เตือนด้วยการสั่น","เมื่อเกินความเร็วที่ตั้งไว้",alerts,v->{alerts=v;saveSettings();});toggleSetting("เสียงเตือนความเร็ว","เสียงสั้นเมื่อเกินค่าที่ตั้ง",sound,v->{sound=v;saveSettings();});settingLabel("ความเร็วเตือน (km/h)","ใช้หน่วย km/h สำหรับค่าขีดจำกัดนี้");slider(limit,20,200,v->{limit=v;saveSettings();});gap(body,14);add(body,button("เปิดการตั้งค่า GPS ของเครื่อง",false,()->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))),52);gap(body,16);add(body,text("GPS ทำงานขณะเปิดแอป · พักทริปเมื่อออกจากแอป\nความถี่และความแม่นยำขึ้นกับอุปกรณ์และสัญญาณ",12,MUTED),-2);}
    private interface NumberChange{void set(int value);}
    private void slider(int initial,int minimum,int maximum,NumberChange change){TextView valueLabel=text(Integer.toString(initial),12,MUTED);add(body,valueLabel,-2);SeekBar seek=new SeekBar(this);seek.setMax(maximum-minimum);seek.setProgress(initial-minimum);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){valueLabel.setText(Integer.toString(p+minimum));change.set(p+minimum);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});add(body,seek,48);}
    private void audioSettings(){
        title("ห้องเสียงขับขี่","8 คาแรกเตอร์เครื่องยนต์ × 12 ท่อ · เสียงสังเคราะห์");toggleSetting("เปิดเสียงเครื่องยนต์","ใช้ความเร็ว GPS สด หรือความเร็ว DEMO",engineEnabled,v->{engineEnabled=v;saveSettings();updateLive();});
        settingLabel("ระดับเสียง (%)","ปรับร่วมกับเสียง Media ของเครื่อง");slider(engineVolume,0,70,v->{engineVolume=v;saveSettings();});
        menuRow("เครื่องยนต์",EngineSynth.ENGINES[engineProfile],()->new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle("เลือกคาแรกเตอร์เครื่องยนต์").setSingleChoiceItems(EngineSynth.ENGINES,engineProfile,(d,i)->{engineProfile=i;saveSettings();d.dismiss();render();}).setNegativeButton("ปิด",null).show());menuRow("ท่อและโทนเบส",EngineSynth.EXHAUSTS[exhaustProfile],11);menuRow("เกียร์และรอบตก",gearboxEnabled?"อัตโนมัติ · ตั้งจังหวะและรอบตกได้":"รอบต่อเนื่อง · ไม่เข้าเกียร์อัตโนมัติ",12);menuRow("การจุดระเบิดเบา ๆ",ignitionEnabled?"เปิด · ระดับ "+ignitionLevel+"%":"ปิด · เปิดได้เฉพาะเมื่ออยากได้เสียงตอนผ่อน",13);
        section("เลือกโทนอย่างรวดเร็ว");twoButtons("นุ่มลึก",()->audioPreset(0),"เงียบอุ่น",()->audioPreset(1));twoButtons("สปอร์ต",()->audioPreset(2),"สนาม / คมชัด",()->audioPreset(3));
        add(body,button(demo?"หยุดโหมดทดลอง":"ทดลองเสียง / ความเร็วจำลอง",false,()->toggleDemo()),52);gap(body,14);
        if(demo){toggleSetting("สาธิตความเร็วอัตโนมัติ","ปิดแล้วปรับความเร็วเองได้",autoDemo,v->autoDemo=v);settingLabel("ความเร็วสาธิต (km/h)","ใช้เมื่อปิดความเร็วอัตโนมัติ");slider(Math.round(demoKmh),0,220,v->demoKmh=v);}
        add(body,text("เป็นเสียงจำลองตามความเร็ว ไม่ใช่ไฟล์เสียงจริงหรือค่าจาก ECU\nหยุดเสียงเมื่อ GPS ขาดหาย เสีย Audio focus หรือออกจากแอป",12,MUTED),-2);if(engineAudio!=null&&!engineAudio.error.isEmpty())add(body,text(engineAudio.error,12,MUTED),-2);
    }
    private void audioPreset(int preset){if(preset==0){exhaustProfile=8;bassLevel=75;textureLevel=20;}else if(preset==1){exhaustProfile=11;bassLevel=55;textureLevel=15;}else if(preset==2){exhaustProfile=1;bassLevel=50;textureLevel=60;}else{exhaustProfile=7;bassLevel=45;textureLevel=75;}saveSettings();render();}
    private void exhaustSettings(){title("ท่อและโทนเบส","เลือกท่อก่อน แล้วค่อยปรับความนุ่มและรายละเอียด");menuRow("ลักษณะท่อ",EngineSynth.EXHAUSTS[exhaustProfile],()->new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle("เลือกท่อ / 12 คาแรกเตอร์").setSingleChoiceItems(EngineSynth.EXHAUSTS,exhaustProfile,(d,i)->{exhaustProfile=i;saveSettings();d.dismiss();render();}).setNegativeButton("ปิด",null).show());settingLabel("น้ำหนักเบส (%)","ต่ำ = โปร่ง · สูง = เบสแน่นนุ่มลึก");slider(bassLevel,0,100,v->{bassLevel=v;saveSettings();});settingLabel("รายละเอียดเสียง (%)","ต่ำ = ละมุน · สูง = คมและมีเนื้อเสียง");slider(textureLevel,0,100,v->{textureLevel=v;saveSettings();});gap(body,16);twoButtons("ท่อนุ่มลึก",()->audioPreset(0),"เงียบละมุน",()->audioPreset(1));add(body,text("โทนเบสต่ำที่ได้ยินขึ้นกับลำโพงหรือหูฟังที่ใช้",12,MUTED),-2);}
    private void gearSettings(){title("เกียร์และรอบตก","แยกการจำลองเสียงออกจากการแสดงเกียร์บนหน้าปัด");toggleSetting("จำลองเข้าเกียร์อัตโนมัติ","มีช่วงหน่วงเพื่อไม่สลับไปมาจาก GPS ที่แกว่ง",gearboxEnabled,v->{gearboxEnabled=v;saveSettings();render();});toggleSetting("รอบตกตอนเปลี่ยนเกียร์","ปิดเพื่อให้รอบเพิ่มแบบต่อเนื่อง",revDropEnabled,v->{revDropEnabled=v;saveSettings();});toggleSetting("จังหวะคั่นเมื่อเข้าเกียร์","ลดเสียงสั้น ๆ เพื่อให้รู้สึกถึงการเปลี่ยนเกียร์",shiftFx,v->{shiftFx=v;saveSettings();});settingLabel("น้ำหนักรอบตก (%)","มีผลเมื่อเปิดเกียร์อัตโนมัติและรอบตก");slider(revDropLevel,0,100,v->{revDropLevel=v;saveSettings();});menuRow("แสดง / ซ่อนเกียร์และรอบ","ตั้งค่าที่หน้าปัด โดยไม่ต้องเปิดเสียง",3);}
    private void ignitionSettings(){title("จุดระเบิดแบบละมุน","เอฟเฟกต์เบา ๆ ตอนผ่อนความเร็ว ไม่เล่นตลอดเวลา");toggleSetting("เสียงจุดระเบิดขณะผ่อน","จำลองเฉพาะช่วงรอบสูงที่ความเร็วลดลง",ignitionEnabled,v->{ignitionEnabled=v;saveSettings();});settingLabel("ความเข้มจุดระเบิด (%)","เริ่มเบา ๆ ได้ที่ 5–10% · สูงสุด 30%");slider(ignitionLevel,0,30,v->{ignitionLevel=v;saveSettings();});toggleSetting("เริ่มเสียงเครื่องแบบนุ่ม","ค่อย ๆ รับเสียงช่วงเริ่มทำงาน",starterEnabled,v->{starterEnabled=v;saveSettings();});gap(body,14);add(body,text("เป็นเอฟเฟกต์สังเคราะห์ ไม่ใช่การควบคุมการจุดระเบิดของรถจริง",12,MUTED),-2);}
    private int soundOptions(){return(gearboxEnabled?EngineSynth.AUTO_SHIFT:0)|(revDropEnabled?EngineSynth.REV_DROP:0)|(shiftFx?EngineSynth.SHIFT_FX:0)|(ignitionEnabled?EngineSynth.IGNITION:0)|(starterEnabled?EngineSynth.STARTER:0);}
    private void toggleDemo(){if(!demo&&(running||distance>0||duration()>0)){Toast.makeText(this,"กรุณาจบทริปปัจจุบันก่อนทดลอง",Toast.LENGTH_LONG).show();return;}pause();demo=!demo;resetTrip();if(demo){running=true;segmentStart=demoStart=SystemClock.elapsedRealtime();lastTick=0;}awake();page=5;render();}
    private void credits(){
        View logoView=new View(this){final Paint p=new Paint(3);final Path path=new Path();@Override protected void onDraw(Canvas c){super.onDraw(c);CockpitView.drawLogo(c,p,path,(getWidth()-dp(90))/2,dp(18),dp(90),accent);}};add(body,logoView,120);title("VELOCITY / MOTO","Version 4.0 · Soft motion. Deep sound.");
        add(body,bold("@khunkish",26,INK),-2);gap(body,8);add(body,text("One Second Hub Co., Ltd.",15,MUTED),-2);gap(body,24);
        add(body,button("GitHub · khunkish ↗",false,()->openLink("https://github.com/khunkish")),52);gap(body,10);add(body,button("Instagram · @khunkish ↗",false,()->openLink("https://www.instagram.com/khunkish")),52);gap(body,10);add(body,button("YouTube · @khunkish ↗",false,()->openLink("https://www.youtube.com/@khunkish")),52);gap(body,24);
        add(body,text("Native Android · Offline GPS · Procedural audio\nข้อมูลทริปเก็บเฉพาะในเครื่อง · ไม่มี ECU connection\nหน้าปัดรถเป็นการออกแบบที่ได้แรงบันดาลใจ ไม่ใช่จอ OEM",12,MUTED),-2);
    }
    private void openLink(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(ActivityNotFoundException e){Toast.makeText(this,url,Toast.LENGTH_LONG).show();}}
    private interface Change { void set(boolean value); }
    private void toggleSetting(String label,String desc,boolean checked,Change change){ LinearLayout r=row(); LinearLayout c=column(); c.addView(bold(label,16,INK)); c.addView(text(desc,12,MUTED)); r.addView(c,new LinearLayout.LayoutParams(0,-2,1)); Switch s=new Switch(this);s.setChecked(checked);s.setThumbTintList(android.content.res.ColorStateList.valueOf(checked?accent:MUTED));s.setContentDescription(label);s.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{(accent&0x00ffffff)|0x55000000,CockpitView.isLight(displayTheme)?0xffcbd4df:0xff3b4756}));s.setOnCheckedChangeListener((b,value)->{s.setThumbTintList(android.content.res.ColorStateList.valueOf(value?accent:MUTED));change.set(value);});r.addView(s);r.setMinimumHeight(dp(72));r.setPadding(0,dp(10),0,dp(10));add(body,r,-2);gap(body,12); }
    private void settingLabel(String label,String value){add(body,bold(label,16,INK),-2);gap(body,3);add(body,text(value,12,MUTED),-2);gap(body,12);}
    private void section(String name){gap(body,10);add(body,bold(name,11,accent),-2);gap(body,20);}

    private void toggle(){
        if(running){pause();render();return;}
        if(demo){running=true;segmentStart=demoStart=SystemClock.elapsedRealtime();awake();render();return;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7);return;}
        if(!gps.isProviderEnabled(LocationManager.GPS_PROVIDER)){new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle("เปิด GPS เพื่อเริ่มเดินทาง").setMessage("กรุณาเปิดตำแหน่งของเครื่อง แล้วกลับมากดเริ่มอีกครั้ง").setNegativeButton("ภายหลัง",null).setPositiveButton("เปิดการตั้งค่า",(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).show();return;}
        try{gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,500,0,this,Looper.getMainLooper());subscribed=true;running=true;segmentStart=SystemClock.elapsedRealtime();previous=null;lastFix=0;awake();render();}catch(SecurityException e){Toast.makeText(this,"ต้องอนุญาตตำแหน่งที่แม่นยำเพื่อใช้ GPS",Toast.LENGTH_LONG).show();}
    }
    private void pause(){if(engineAudio!=null)engineAudio.stop();if(running){elapsed=duration();running=false;} if(subscribed){gps.removeUpdates(this);subscribed=false;} speed=0;previous=null;awake();persistTrip();persistExtras();}
    private void resetTrip(){speed=distance=maxSpeed=0;elapsed=0;previous=null;lastFix=0;route=new JSONArray();laps=new JSONArray();lapStart=0;lastSample=0;speedTrace.clear();persistTrip();persistExtras();}
    private void finishTrip(){
        if(duration()==0&&distance==0){Toast.makeText(this,"เริ่มทริปก่อนบันทึก",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle(demo?"จบการสาธิต?":"บันทึกและจบทริป?").setMessage(demo?"ค่าจำลองจะไม่ถูกเก็บในประวัติ":"บันทึกระยะทาง เวลา และความเร็วสูงสุดไว้บนเครื่อง").setNegativeButton("เดินทางต่อ",null).setPositiveButton("จบทริป",(d,w)->{
            pause();if(!demo){try{JSONObject item=new JSONObject();item.put("date",System.currentTimeMillis());item.put("meters",distance);item.put("seconds",elapsed/1000);item.put("max",maxSpeed);item.put("laps",laps);item.put("bike",BikeDashboard.NAMES[bike]);history.put(item);if(history.length()>100)history.remove(0);lastRoute=route;prefs.edit().putString("lastRoute",lastRoute.toString()).putString("history",history.toString()).apply();}catch(JSONException ignored){}}
            demo=false;resetTrip();page=2;render();
        }).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results); if(code==7){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)toggle();else new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle("ต้องใช้ตำแหน่งที่แม่นยำ").setMessage("อนุญาตตำแหน่งแบบแม่นยำในการตั้งค่าแอป เพื่อวัดความเร็วจาก GPS").setNegativeButton("ปิด",null).setPositiveButton("การตั้งค่าแอป",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())))).show();}}
    @Override public void onLocationChanged(Location location){
        if(!running||demo)return;
        long now=SystemClock.elapsedRealtime(); long age=SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos();
        if(age<0||age>5_000_000_000L||!location.hasAccuracy()||location.getAccuracy()>35){previous=null;return;}
        if(previous!=null&&location.getElapsedRealtimeNanos()<=previous.getElapsedRealtimeNanos())return;
        accuracy=location.getAccuracy();lastFix=now;position=true;heading=location.hasBearing()&&location.hasSpeed()&&location.getSpeed()>1?location.getBearing():Double.NaN;altitude=location.hasAltitude()?location.getAltitude():Double.NaN;
        boolean validSpeed=location.hasSpeed()&&(!location.hasSpeedAccuracy()||location.getSpeedAccuracyMetersPerSecond()<=3);
        validSpeedFresh=validSpeed;speed=validSpeed?Math.max(0,location.getSpeed()):0; if(speed<0.5)speed=0;
        if(previous!=null){double seconds=(location.getElapsedRealtimeNanos()-previous.getElapsedRealtimeNanos())/1e9;float delta=previous.distanceTo(location);
            if(seconds>0&&seconds<=5&&delta/seconds<100&&speed>0.5&&delta>Math.max(2,Math.min(accuracy,previous.getAccuracy())*.35))distance+=delta;
        }
        previous=new Location(location);maxSpeed=Math.max(maxSpeed,speed);
        if(now-lastSample>=5000&&route.length()<4000){try{JSONObject point=new JSONObject();point.put("lat",location.getLatitude());point.put("lon",location.getLongitude());if(location.hasAltitude())point.put("alt",location.getAltitude());point.put("time",location.getTime());route.put(point);lastSample=now;}catch(JSONException ignored){}}
        speedTrace.add((float)(speed*3.6));if(speedTrace.size()>120)speedTrace.remove(0);
        if(now-lastRouteSaved>10000){persistExtras();lastRouteSaved=now;}
        if((alerts||sound)&&speed*3.6>limit&&now-lastAlert>10000){lastAlert=now;if(alerts){Vibrator vib=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(vib!=null&&vib.hasVibrator())vib.vibrate(VibrationEffect.createOneShot(250,VibrationEffect.DEFAULT_AMPLITUDE));}if(sound){final android.media.ToneGenerator tone=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,75);tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,180);handler.postDelayed(()->tone.release(),250);}}
        persistTrip();updateLive();
    }
    @Override public void onProviderDisabled(String provider){speed=0;lastFix=0;previous=null;updateLive();}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onStatusChanged(String p,int s,Bundle extras){}
    @Override protected void onStop(){super.onStop();if(running)pause();}
    @Override protected void onDestroy(){handler.removeCallbacks(ticker);if(subscribed)gps.removeUpdates(this);if(engineAudio!=null)engineAudio.stop();super.onDestroy();}
    private void updateLive(){
        boolean fresh=running&&lastFix>0&&SystemClock.elapsedRealtime()-lastFix<=5000;
        if(cockpit!=null){populateCockpit();cockpit.updateText();}
        if(engineAudio!=null)engineAudio.configure(soundOptions(),bassLevel/100f,textureLevel/100f,ignitionLevel/100f,revDropLevel/100f);
        if(engineAudio!=null)engineAudio.update(inForeground&&engineEnabled&&running&&(demo||fresh&&validSpeedFresh),engineProfile,exhaustProfile,speed*3.6,engineVolume/100f);
        if(page==4)updateTools();
    }
    private void populateCockpit(){
        if(bikeView==null)bikeView=new BikeDashboard(this,bike);populate(bikeView);BikeDashboard.Data source=bikeView.data,target=cockpit.data;
        target.speed=source.speed;target.distance=source.distance;target.maximum=source.maximum;target.average=source.average;target.elapsed=source.elapsed;target.demo=source.demo;target.miles=source.miles;target.running=source.running;target.accuracy=source.accuracy;target.fresh=source.fresh;target.time=source.time;target.battery=source.battery;target.overspeed=source.overspeed;target.day=CockpitView.isLight(displayTheme);target.focus=!showStats;target.position=source.position;target.altitude=source.altitude;target.bearing=source.bearing;target.lap=source.lap;target.lapCount=source.lapCount;target.bestLap=source.bestLap;
        cockpit.layout=displayLayout;cockpit.theme=displayTheme;cockpit.model=bike;cockpit.showRpm=showRpm;cockpit.showGear=showGear;cockpit.showStats=showStats;long now=SystemClock.elapsedRealtime();double dt=driveUpdatedAt==0?.25:Math.min(.3,(now-driveUpdatedAt)/1000.0);driveUpdatedAt=now;if(running)virtualDrive.update(speed*3.6,dt,engineProfile,gearboxEnabled,revDropEnabled,revDropLevel/100f);else virtualDrive.reset();cockpit.virtualRpm=running?(engineAudio!=null&&engineAudio.isActive()?engineAudio.currentRpm:virtualDrive.targetRpm):0;cockpit.virtualGear=running?(engineAudio!=null&&engineAudio.isActive()?engineAudio.currentGear:virtualDrive.gear()):0;cockpit.swipeEnabled=swipeEnabled;cockpit.smoothMotion=smoothMotion;cockpit.audio=engineEnabled&&running&&(demo||source.fresh&&validSpeedFresh);target.virtualEngine=showRpm||showGear;target.virtualRpm=cockpit.virtualRpm;target.virtualGear=cockpit.virtualGear;
    }
    @Override protected void onResume(){super.onResume();inForeground=true;if(cockpit!=null)render();updateLive();}
    @Override protected void onPause(){inForeground=false;if(engineAudio!=null)engineAudio.stop();super.onPause();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);render();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("exportBody",exportBody);persistTrip();persistExtras();}
    @Override public void onBackPressed(){if(controlSheet!=null){controlSheet.dismiss();return;}super.onBackPressed();}
    private void applyBrightness(){WindowManager.LayoutParams params=getWindow().getAttributes();params.screenBrightness=brightness/100f;getWindow().setAttributes(params);}
    private void persistExtras(){if(!demo)prefs.edit().putString("route",route.toString()).putString("laps",laps.toString()).putLong("lapStart",lapStart).apply();}
    private void populate(BikeDashboard v){
        BikeDashboard.Data d=v.data;d.speed=converted(speed);d.distance=distance/(miles?1609.344:1000);d.maximum=converted(maxSpeed);d.average=converted(duration()>0?distance/(duration()/1000.0):0);d.elapsed=duration();d.demo=demo;d.miles=miles;d.running=running;d.accuracy=accuracy;d.fresh=running&&lastFix>0&&SystemClock.elapsedRealtime()-lastFix<=5000;d.time=new SimpleDateFormat("HH:mm",Locale.US).format(new Date());
        if(SystemClock.elapsedRealtime()-batteryReadAt>10000){Intent batteryIntent=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));batteryPercent=batteryIntent==null?0:Math.round(batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL,0)*100f/Math.max(1,batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE,100)));batteryReadAt=SystemClock.elapsedRealtime();}d.battery=batteryPercent;d.overspeed=!demo&&(alerts||sound)&&speed*3.6>limit;
        d.focus=focus;d.day=day;d.position=position&&d.fresh;d.altitude=altitude;d.bearing=heading;d.lap=Math.max(0,duration()-lapStart);d.lapCount=String.format(Locale.US,"%02d",laps.length()+1);d.bestLap=0;for(int i=0;i<laps.length();i++){long value=laps.optLong(i);if(value>0&&(d.bestLap==0||value<d.bestLap))d.bestLap=value;}
        v.setContentDescription(BikeDashboard.NAMES[bike]+" ความเร็ว "+fixed(d.speed,0)+" "+unit()+(demo?" ค่าจำลอง":" GPS"));
    }
    private void chooseBike(){new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle("เลือกรุ่นหน้าปัด").setSingleChoiceItems(BikeDashboard.NAMES,bike,(dialog,index)->{bike=index;style=3;displayLayout=3;saveSettings();dialog.dismiss();page=0;render();}).setNegativeButton("ปิด",null).show();}
    private void bikeStyles(){
        title("Moto collection.","12 รุ่น · แตะเลือก / หมุนแนวนอนเพื่อขยายเต็มจอ");
        add(body,text("ออกแบบตามลักษณะหน้าปัดของแต่ละรุ่น ไม่ใช่จอ OEM\nใช้ GPS เท่านั้น ไม่มีระบบเชื่อมต่อ ECU · รอบ/เกียร์แสดง —",12,MUTED),-2);gap(body,16);
        LinearLayout filters=row();String[] categories={"ทั้งหมด","Sport / Naked","City / Touring"};for(int i=0;i<3;i++){final int pick=i;TextView t=button(categories[i],category==i,()->{category=pick;page=1;render();});t.setTextSize(11);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(dp(2),0,dp(2),0);filters.addView(t,lp);}add(body,filters,46);gap(body,18);
        for(int i=0;i<BikeDashboard.NAMES.length;i++){if(category==1&&i>=8||category==2&&i<8)continue;final int pick=i;LinearLayout card=column();card.setPadding(dp(12),dp(14),dp(12),dp(14));card.setBackground(shape(PANEL,18));LinearLayout heading=row();heading.addView(bold(BikeDashboard.NAMES[i],19,INK),new LinearLayout.LayoutParams(0,-2,1));heading.addView(bold(style==3&&bike==i?"เลือกแล้ว ✓":"เลือก →",12,BikeDashboard.COLORS[i]));card.addView(heading);card.addView(text(BikeDashboard.FAMILY[i],11,MUTED));gap(card,10);BikeDashboard preview=new BikeDashboard(this,i);preview.preview=true;preview.data.demo=true;preview.data.battery=100;preview.data.time="10:30";card.addView(preview,new LinearLayout.LayoutParams(-1,dp(150)));card.setOnClickListener(v->{bike=pick;style=3;displayLayout=3;saveSettings();page=0;render();});add(body,card,-2);gap(body,14);}
    }
    private void recordLap(){
        if(!running){Toast.makeText(this,"เริ่มเดินทางก่อนบันทึกรอบ",Toast.LENGTH_SHORT).show();return;}
        long value=duration()-lapStart;if(value<1000){Toast.makeText(this,"รอบต้องนานกว่า 1 วินาที",Toast.LENGTH_SHORT).show();return;}
        if(laps.length()>=100){Toast.makeText(this,"ครบ 100 รอบ กรุณาจบทริป",Toast.LENGTH_SHORT).show();return;}
        laps.put(value);lapStart=duration();persistExtras();Toast.makeText(this,"LAP "+laps.length()+" · "+BikeDashboard.duration(value),Toast.LENGTH_SHORT).show();updateTools();
    }
    private void rideTools(){
        title("เครื่องมือเดินทาง","จับเวลารอบแบบกดเอง · เส้นทางและสถิติ GPS");
        add(body,button("กลับหน้าปัด / เต็มจอ →",true,()->{page=0;render();}),52);gap(body,18);
        lapInfo=bold("",24,INK);add(body,lapInfo,-2);gap(body,12);add(body,button("บันทึก LAP +",false,()->recordLap()),52);gap(body,12);
        add(body,button("ดูเวลารอบทั้งหมด",false,()->showLaps(laps)),48);gap(body,24);
        section("GPS / TELEMETRY");toolsInfo=text("",14,MUTED);add(body,toolsInfo,-2);gap(body,18);
        traceView=new View(this){final Paint pen=new Paint(3);final Path track=new Path();@Override protected void onDraw(Canvas c){super.onDraw(c);pen.setColor(PANEL);c.drawColor(PANEL);pen.setColor(MUTED);pen.setTextSize(dp(11));c.drawText("OFFLINE ROUTE · "+route.length()+" GPS points",dp(14),dp(22),pen);if(route.length()<2){c.drawText("รอข้อมูลเส้นทาง GPS",dp(14),dp(60),pen);return;}double loX=180,hiX=-180,loY=90,hiY=-90;for(int i=0;i<route.length();i++){JSONObject q=route.optJSONObject(i);if(q==null)continue;loX=Math.min(loX,q.optDouble("lon"));hiX=Math.max(hiX,q.optDouble("lon"));loY=Math.min(loY,q.optDouble("lat"));hiY=Math.max(hiY,q.optDouble("lat"));}double cos=Math.cos(Math.toRadians((loY+hiY)/2));double span=Math.max((hiX-loX)*cos,hiY-loY);double factor=Math.min(getWidth()-dp(40),getHeight()-dp(70))/Math.max(.00001,span);track.reset();for(int i=0;i<route.length();i++){JSONObject q=route.optJSONObject(i);if(q==null)continue;float x=getWidth()/2f+(float)((q.optDouble("lon")-(loX+hiX)/2)*cos*factor),y=(getHeight()+dp(25))/2f-(float)((q.optDouble("lat")-(loY+hiY)/2)*factor);if(i==0)track.moveTo(x,y);else track.lineTo(x,y);}pen.setColor(accent);pen.setStrokeWidth(dp(3));pen.setStyle(Paint.Style.STROKE);c.drawPath(track,pen);pen.setStyle(Paint.Style.FILL);}};add(body,traceView,200);gap(body,12);
        add(body,new View(this){final Paint pen=new Paint(3);@Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(PANEL);pen.setColor(MUTED);pen.setTextSize(dp(11));c.drawText("SPEED / GPS · ล่าสุด 120 ตัวอย่าง (km/h)",dp(14),dp(22),pen);if(speedTrace.size()<2){c.drawText("เริ่มทริปเพื่อแสดงกราฟความเร็วจริง",dp(14),dp(56),pen);return;}float top=1;for(float v:speedTrace)top=Math.max(top,v);pen.setColor(accent);pen.setStrokeWidth(dp(2));for(int i=1;i<speedTrace.size();i++)c.drawLine(dp(15)+(i-1)*(getWidth()-dp(30))/(speedTrace.size()-1f),getHeight()-dp(15)-speedTrace.get(i-1)/top*(getHeight()-dp(50)),dp(15)+i*(getWidth()-dp(30))/(speedTrace.size()-1f),getHeight()-dp(15)-speedTrace.get(i)/top*(getHeight()-dp(50)),pen);}},120);gap(body,20);
        add(body,button("ส่งออกเส้นทางปัจจุบัน / ล่าสุด .GPX",false,()->exportGpx()),52);gap(body,12);add(body,button("ส่งออกประวัติทริป .CSV",false,()->exportCsv()),52);gap(body,12);add(body,button("จบทริปและบันทึก",false,()->finishTrip()),52);gap(body,18);
        add(body,text("GPX ใช้จุดจริงจาก GPS ทุกอย่างน้อย 5 วินาที สูงสุด 4,000 จุด/ทริป\nกราฟเส้นทางออฟไลน์ ไม่ใช่แผนที่นำทาง · GPS ทำงานเมื่อเปิดแอป\nเปิดตัวเลือกบันทึกไฟล์จะพักทริป\nเวลารอบนับจากเริ่มทริปและไม่นับช่วงพัก",11,MUTED),-2);updateTools();
    }
    private void updateTools(){if(lapInfo!=null)lapInfo.setText("LAP "+(laps.length()+1)+"   "+BikeDashboard.duration(Math.max(0,duration()-lapStart)));if(toolsInfo!=null){boolean fresh=running&&lastFix>0&&SystemClock.elapsedRealtime()-lastFix<=5000;toolsInfo.setText((demo?"DEMO · ค่าจำลอง":fresh?"GPS LIVE · ±"+fixed(accuracy,0)+" m":"GPS ไม่มีข้อมูลสด")+"\nทิศทาง "+(fresh&&position&&Double.isFinite(heading)?fixed(heading,0)+"°":"—")+"  ·  ความสูง "+(fresh&&position&&Double.isFinite(altitude)?fixed(altitude,0)+" m":"—")+"\nเวลา "+clock()+"  ·  จุดเส้นทาง "+route.length()+"/4000");}if(traceView!=null)traceView.invalidate();if(page==4&&body!=null)for(int i=0;i<body.getChildCount();i++)body.getChildAt(i).invalidate();}
    private void showLaps(JSONArray items){StringBuilder s=new StringBuilder();long best=Long.MAX_VALUE;for(int i=0;i<items.length();i++)best=Math.min(best,items.optLong(i));for(int i=0;i<items.length();i++){long t=items.optLong(i);s.append(String.format(Locale.US,"LAP %02d   %s%s\n",i+1,BikeDashboard.duration(t),t==best?"  ★ BEST":""));}new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle("เวลารอบ / Manual laps").setMessage(s.length()==0?"ยังไม่มีรอบที่บันทึก":s.toString()).setPositiveButton("ปิด",null).show();}
    private void tripDetails(JSONObject item){long seconds=item.optLong("seconds");String s="ระยะทาง "+fixed(item.optDouble("meters")/(miles?1609.344:1000),2)+(miles?" mi":" km")+"\nเวลา "+BikeDashboard.duration(seconds*1000)+"\nสูงสุด "+fixed(converted(item.optDouble("max")),0)+" "+unit()+"\nเฉลี่ย "+fixed(converted(seconds>0?item.optDouble("meters")/seconds:0),0)+" "+unit();new AlertDialog.Builder(new ContextThemeWrapper(this,CockpitView.isLight(displayTheme)?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert)).setTitle(item.optString("bike","Trip")).setMessage(s).setNeutralButton("เวลารอบ",(d,w)->showLaps(item.optJSONArray("laps")==null?new JSONArray():item.optJSONArray("laps"))).setPositiveButton("ปิด",null).show();}
    private void exportGpx(){JSONArray points=route.length()>0?route:lastRoute;if(points.length()==0){Toast.makeText(this,"ยังไม่มีเส้นทาง GPS จริงสำหรับส่งออก",Toast.LENGTH_LONG).show();return;}StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Velocity Moto\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>Velocity GPS Trip</name><trkseg>");SimpleDateFormat utc=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);utc.setTimeZone(TimeZone.getTimeZone("UTC"));for(int i=0;i<points.length();i++){JSONObject q=points.optJSONObject(i);if(q==null)continue;s.append("<trkpt lat=\"").append(q.optDouble("lat")).append("\" lon=\"").append(q.optDouble("lon")).append("\">");if(q.has("alt"))s.append("<ele>").append(q.optDouble("alt")).append("</ele>");s.append("<time>").append(utc.format(new Date(q.optLong("time")))).append("</time></trkpt>");}s.append("</trkseg></trk></gpx>");exportFile("velocity-route.gpx","application/gpx+xml",s.toString());}
    private void exportCsv(){if(history.length()==0){Toast.makeText(this,"บันทึกทริปก่อนส่งออก CSV",Toast.LENGTH_SHORT).show();return;}StringBuilder s=new StringBuilder("date_utc,bike,distance_m,duration_s,max_mps,average_mps,laps\r\n");SimpleDateFormat utc=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);utc.setTimeZone(TimeZone.getTimeZone("UTC"));for(int i=0;i<history.length();i++){JSONObject q=history.optJSONObject(i);if(q==null)continue;long seconds=q.optLong("seconds");s.append(utc.format(new Date(q.optLong("date")))).append(',').append(q.optString("bike","Velocity").replace(","," ")).append(',').append(q.optDouble("meters")).append(',').append(seconds).append(',').append(q.optDouble("max")).append(',').append(seconds>0?q.optDouble("meters")/seconds:0).append(',').append(q.optJSONArray("laps")==null?0:q.optJSONArray("laps").length()).append("\r\n");}exportFile("velocity-trips.csv","text/csv",s.toString());}
    private void exportFile(String name,String mime,String contents){exportBody=contents;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.setType(mime);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,80);}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(request==80&&result==RESULT_OK&&intent!=null&&intent.getData()!=null){try(OutputStream output=getContentResolver().openOutputStream(intent.getData(),"wt")){if(output==null)throw new java.io.IOException("No output stream");output.write(exportBody.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"บันทึกไฟล์เรียบร้อย",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"บันทึกไม่สำเร็จ กรุณาเลือกโฟลเดอร์ใหม่",Toast.LENGTH_LONG).show();}}exportBody="";}
}





