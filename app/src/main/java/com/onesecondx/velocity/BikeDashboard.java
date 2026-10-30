package com.onesecondx.velocity;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

/** Original code-native instruments, inspired by the instrument families named below.
 * Engine channels are deliberately unavailable without an ECU connection. */
public final class BikeDashboard extends View {
    public static final String[] NAMES={"S 1000 RR","Ninja H2R","CBR650R","Ninja ZX-10R","YZF-R1","MT-09","Z900","YZF-R15","PCX160","NMAX","ADV160","Wave125i"};
    public static final String[] FAMILY={"BMW · Race TFT","Kawasaki · Analog / LCD","Honda · Sport TFT","Kawasaki · Circuit TFT","Yamaha · Racing TFT","Yamaha · Hyper naked","Kawasaki · Street TFT","Yamaha · Compact sport","Honda · Urban LCD","Yamaha · Touring TFT","Honda · Adventure LCD","Honda · Classic analog"};
    public static final int[] COLORS={0xff42a5ff,0xff82ec4d,0xffff4f51,0xffa0f345,0xff71a3ff,0xff75a5ff,0xff69e999,0xff55d9ef,0xff80d9fa,0xff65b5ff,0xffffb95c,0xffffdf88};
    public static class Data {
        public double speed,distance,maximum,average,altitude,bearing;
        public float accuracy;
        public int battery;
        public long elapsed,lap,bestLap;
        public boolean demo,fresh,running,miles,position,focus,day,overspeed;
        public String time="00:00",lapCount="01";
        public boolean showRpm=true,showGear=true,showStats=true,virtualEngine=false,customTheme=false;
        public int customBase,customAccent,virtualGear;
        public float virtualRpm;
    }
    public final Data data=new Data();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final Path path=new Path();
    private final Typeface medium=Typeface.create("sans-serif-medium",Typeface.NORMAL), digits=Typeface.create("sans-serif-condensed",Typeface.BOLD);
    public int model;
    public boolean preview;
    private int fg,muted,base,accent;
    public BikeDashboard(Context context,int model){super(context);this.model=model;setContentDescription(NAMES[model]+" GPS dashboard");}
    private String number(double n,int decimals){return String.format(Locale.US,"%."+decimals+"f",n);}
    private void fill(Canvas c,float x,float y,float w,float h,int color,float radius){paint.setColor(color);paint.setStyle(Paint.Style.FILL);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,radius,radius,paint);}
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean big){if(!data.showRpm&&(value.contains("r/min")||value.contains("RPM")||value.contains("ECU")||value.contains("BOOST")||value.contains("VVA")))return;paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTypeface(big?digits:medium);paint.setTextSize(size);paint.setTextAlign(Paint.Align.LEFT);c.drawText(value,x,y,paint);}
    private void stroke(Canvas c,float x,float y,float x2,float y2,int color,float width){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(color);c.drawLine(x,y,x2,y2,paint);paint.setStyle(Paint.Style.FILL);}
    private String speed(){return number(preview?88:data.speed,0);}
    private float speedSize(float size){return speed().length()>2?size*.8f:size;}
    private String gear(){if(data.virtualEngine)return data.virtualGear==0?"N":Integer.toString(data.virtualGear);return data.demo||preview?Integer.toString(Math.max(1,Math.min(6,(int)((preview?88:data.speed)/28)+1))):"—";}
    private float rev(){if(data.virtualEngine)return data.virtualRpm/15000f;return data.demo||preview?(float)(.23+((preview?88:data.speed)%100)/145):0;}
    private String unit(){return data.miles?"mph":"km/h";}
    public static String duration(long ms){long tenths=ms/100;return String.format(Locale.US,"%02d:%02d.%d",tenths/600,tenths/10%60,tenths%10);}
    private void engine(Canvas c){if(!data.showGear)return;text(c,data.virtualEngine?"SIM GEAR":"GEAR",815,210,17,muted,false);text(c,gear(),805,319,120,accent,true);text(c,data.virtualEngine||data.demo||preview?"SIMULATED":"ECU ไม่ได้เชื่อมต่อ",785,377,13,muted,false);}
    private void bar(Canvas c,int max,float x,float y,float w,float h,boolean slope){
        if(!data.showRpm)return;
        for(int i=0;i<max;i++){
            float xx=x+i*w/max,yy=y-(slope?Math.min(i,6)*7:0);int color=i>=max-3?0xffff4c54:accent;
            text(c,Integer.toString(i),xx,yy-13,18,i>=max-3?0xffff6e72:muted,false);
            fill(c,xx,yy,w/max-5,h,rev()>i/(float)max?color:(data.day?0xffcbd3db:0xff24313e),2);
        }
        text(c,"×1000 r/min"+(data.virtualEngine||data.demo||preview?" · SIM":" · ECU —"),x,y+h+22,16,muted,false);
    }
    private void ring(Canvas c,float cx,float cy,float radius,int max,boolean speedometer){
        if(!speedometer&&!data.showRpm)return;
        rect.set(cx-radius,cy-radius,cx+radius,cy+radius);paint.setColor(data.day?0xffb9c3cd:0xff26333d);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(10);c.drawArc(rect,140,260,false,paint);
        for(int i=0;i<=max;i++){
            double a=Math.toRadians(140+i*260f/max);float co=(float)Math.cos(a),si=(float)Math.sin(a);
            stroke(c,cx+co*(radius-12),cy+si*(radius-12),cx+co*(radius-25),cy+si*(radius-25),i>max*.78?0xffff575c:muted,3);
            text(c,Integer.toString(speedometer?i*20:i),cx+co*(radius-46)-9,cy+si*(radius-46)+7,18,fg,false);
        }
        double a=Math.toRadians(140+260*(speedometer?Math.min(1,(preview?88:data.speed)/(max*20)):rev()));
        stroke(c,cx,cy,cx+(float)Math.cos(a)*(radius-30),cy+(float)Math.sin(a)*(radius-30),speedometer?accent:0xffff5055,6);
        fill(c,cx-10,cy-10,20,20,fg,10);
    }
    private void bmw(Canvas c){
        text(c,"RACE",40,100,21,accent,true);bar(c,16,60,154,865,37,true);
        text(c,speed(),72,355,speedSize(164),fg,true);text(c,unit(),315,348,26,muted,false);engine(c);
        stroke(c,435,230,435,365,data.day?0xffc8d1da:0xff273444,2);
        text(c,"LAP "+data.lapCount,477,256,18,muted,false);text(c,duration(data.lap),470,307,36,fg,true);
        text(c,"BEST  "+(data.bestLap>0?duration(data.bestLap):"—"),477,348,18,muted,false);
        fill(c,40,371,30,5,0xff42a5ff,0);fill(c,73,371,30,5,0xff3a398b,0);fill(c,106,371,30,5,0xffe43b46,0);
    }
    private void h2(Canvas c){
        ring(c,267,244,160,16,false);text(c,"×1000 r/min",196,346,18,muted,false);
        text(c,data.virtualEngine||data.demo||preview?"SIM RPM":"ECU —",213,160,16,accent,false);
        fill(c,474,99,475,270,data.day?0xffdce9e5:0xff14221c,12);
        text(c,"SUPERCHARGED / GPS",501,132,18,accent,false);text(c,speed(),496,286,speedSize(153),fg,true);text(c,unit(),710,284,25,muted,false);
        if(data.showGear){text(c,data.virtualEngine?"SIM GEAR":"GEAR",825,167,16,muted,false);text(c,gear(),825,244,73,accent,true);}
        stroke(c,501,308,922,308,0xff3c6351,2);text(c,"BOOST —   ·   ECU ไม่ได้เชื่อมต่อ",501,343,18,muted,false);
    }
    private void honda(Canvas c){
        if(data.showRpm){path.reset();path.moveTo(62,292);path.lineTo(170,111);path.lineTo(936,111);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(29);paint.setColor(data.day?0xffc7cdd5:0xff28333f);c.drawPath(path,paint);paint.setStrokeWidth(5);paint.setColor(accent);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<=14;i++)text(c,""+i,172+i*53,86,18,i>11?0xffff515b:muted,false);
        if(rev()>0)fill(c,173,99,Math.min(740,rev()*740),23,accent,0);
        text(c,"×1000 r/min · "+(data.virtualEngine||data.demo||preview?"SIM":"ECU —"),377,157,16,muted,false);}
        text(c,speed(),260,333,speedSize(161),fg,true);text(c,unit(),507,329,26,muted,false);engine(c);
        text(c,"SPORT",94,354,18,accent,true);
    }
    private void zx(Canvas c){
        text(c,"CIRCUIT",40,96,19,accent,false);bar(c,15,50,151,900,46,false);
        if(data.showGear){fill(c,51,225,135,135,data.day?0xffd9e2d6:0xff1a281c,9);text(c,data.virtualEngine?"SIM GEAR":"GEAR",74,252,16,muted,false);text(c,gear(),84,339,83,accent,true);}
        text(c,"LAP "+data.lapCount,249,245,17,muted,false);text(c,duration(data.lap),244,288,36,fg,true);text(c,"BEST "+(data.bestLap>0?duration(data.bestLap):"—"),249,333,17,muted,false);
        text(c,speed(),635,344,speedSize(157),fg,true);text(c,unit(),847,345,24,muted,false);
    }
    private void r1(Canvas c){
        fill(c,33,76,934,299,data.day?0xfff6f8f9:0xff17202b,8);
        bar(c,15,60,134,871,35,true);text(c,"GPS / STREET",60,233,18,accent,false);
        text(c,"LAP "+data.lapCount+"   "+duration(data.lap),60,280,25,fg,false);text(c,"ECU DATA   —",60,330,18,muted,false);
        text(c,speed(),523,346,speedSize(145),fg,true);text(c,unit(),728,345,24,muted,false);engine(c);
    }
    private void naked(Canvas c){
        if(model==6){ring(c,290,245,158,12,false);text(c,data.virtualEngine||data.demo||preview?"SIM RPM":"ECU —",245,190,16,muted,false);text(c,speed(),500,330,speedSize(140),fg,true);text(c,unit(),710,330,24,muted,false);engine(c);return;}
        else{bar(c,12,50,138,660,43,true);text(c,speed(),112,337,speedSize(164),fg,true);text(c,unit(),349,333,25,muted,false);}
        text(c,"TRIP",590,217,18,muted,false);text(c,number(data.distance,1),583,272,43,fg,true);text(c,data.miles?"mi":"km",590,302,18,muted,false);engine(c);
    }
    private void r15(Canvas c){
        fill(c,43,87,914,285,data.day?0xffdce5e7:0xff1a292d,5);bar(c,12,75,144,840,37,true);
        text(c,"VVA —",78,253,22,accent,false);text(c,speed(),330,333,speedSize(141),fg,true);text(c,unit(),535,331,24,muted,false);engine(c);
    }
    private void scooter(Canvas c){
        if(model==8){
            for(int i=0;i<12;i++){float x=65+i*75;stroke(c,x,116,x+53,116,accent,4);}
            text(c,"URBAN",60,167,19,accent,false);text(c,speed(),344,326,speedSize(174),fg,true);text(c,unit(),591,324,25,accent,false);
            text(c,"TRIP",70,239,18,muted,false);text(c,number(data.distance,1),65,285,36,fg,true);text(c,"GPS",838,244,20,accent,false);
        }else if(model==9){
            fill(c,45,81,910,295,data.day?0xffdce4f2:0xff142237,17);stroke(c,675,110,675,349,0xff456185,2);
            text(c,"TOURING",75,127,21,accent,false);text(c,speed(),174,312,speedSize(159),fg,true);text(c,unit(),405,310,26,muted,false);
            text(c,"TRIP",718,147,18,muted,false);text(c,number(data.distance,1),711,192,38,fg,true);text(c,"ALTITUDE",718,244,18,muted,false);text(c,data.position&&Double.isFinite(data.altitude)?number(data.altitude,0)+" m":"—",711,291,38,fg,true);
        }else{
            path.reset();path.moveTo(60,318);path.lineTo(140,96);path.lineTo(861,96);path.lineTo(942,318);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(8);paint.setColor(accent);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            text(c,"ADVENTURE",373,144,21,accent,true);text(c,speed(),331,320,speedSize(161),fg,true);text(c,unit(),561,317,24,muted,false);
            text(c,"ALT",104,269,16,muted,false);text(c,data.position&&Double.isFinite(data.altitude)?number(data.altitude,0)+"m":"—",99,307,29,fg,true);text(c,"GPS",826,303,19,accent,false);
        }
    }
    private void wave(Canvas c){
        ring(c,378,242,161,9,true);text(c,unit(),415,330,16,muted,false);text(c,speed(),327,331,speedSize(70),fg,true);
        fill(c,621,122,290,211,data.day?0xffd8d8bb:0xff262e26,8);text(c,"TRIP",652,167,21,muted,false);text(c,number(data.distance,1),647,230,50,fg,true);text(c,data.miles?"mi":"km",650,273,21,muted,false);text(c,"GPS",793,307,20,accent,false);
    }
    @Override protected void onDraw(Canvas original){
        super.onDraw(original);model=Math.max(0,Math.min(model,NAMES.length-1));
        base=data.day?0xffe9eef2:0xff090e14;fg=data.day?0xff101e2a:0xfff2f5f7;muted=data.day?0xff526475:0xff8799a9;accent=COLORS[model];if(data.day)accent=Color.rgb((int)(Color.red(accent)*.6),(int)(Color.green(accent)*.6),(int)(Color.blue(accent)*.6));
        if(data.customTheme){base=data.customBase;accent=data.customAccent;}original.drawColor(base);float scale=Math.min(getWidth()/1000f,getHeight()/480f);original.save();original.translate((getWidth()-1000*scale)/2,(getHeight()-480*scale)/2);original.scale(scale,scale);
        Canvas c=original;
        text(c,NAMES[model],40,40,23,fg,true);
        text(c,data.demo||preview?"DEMO · ค่าจำลอง":data.overspeed?"เกินความเร็วที่ตั้งไว้":data.fresh?"GPS LIVE":data.running?"GPS รอสัญญาณ":"GPS / พักทริป",370,39,17,data.demo||preview?0xffffb95c:data.overspeed?0xffff515b:data.fresh?accent:muted,false);
        text(c,data.time,806,39,21,fg,false);text(c,data.battery+"%",918,39,18,muted,false);
        stroke(c,40,56,960,56,data.day?0xffc4ced7:0xff26323f,1);
        switch(model){case 0:bmw(c);break;case 1:h2(c);break;case 2:honda(c);break;case 3:zx(c);break;case 4:r1(c);break;case 5:case 6:naked(c);break;case 7:r15(c);break;case 8:case 9:case 10:scooter(c);break;default:wave(c);}
        if(data.showStats){stroke(c,40,396,960,396,data.day?0xffc4ced7:0xff26323f,1);
        if(data.focus){text(c,"TRIP   "+number(data.distance,2)+" "+(data.miles?"mi":"km"),45,439,24,fg,false);text(c,"TIME   "+duration(data.elapsed),645,439,24,fg,false);}
        else{bottom(c,"TRIP",number(data.distance,2)+(data.miles?" mi":" km"),45);bottom(c,"MAX",number(data.maximum,0)+" "+unit(),290);bottom(c,"AVG",number(data.average,0)+" "+unit(),515);boolean bearing=data.position&&Double.isFinite(data.bearing);bottom(c,bearing?"HEADING":"ELAPSED",bearing?number(data.bearing,0)+"°":duration(data.elapsed),750);}
        }if(data.overspeed){stroke(c,8,8,992,8,0xffff515b,7);stroke(c,8,472,992,472,0xffff515b,7);}
        original.restore();
    }
    private void bottom(Canvas c,String label,String value,int x){text(c,label,x,422,14,muted,false);text(c,value,x,457,26,fg,true);}
}



