package com.onesecondx.velocity;
/** Virtual drivetrain for sound only; hysteresis prevents GPS gear chatter. */
public final class DriveModel {
 private static final double[] UP={24,48,78,112,155},RATIOS={1,1,.72,.54,.43,.36,.31};
 private static final float[] REDLINE={14500,14000,12500,14500,14000,11500,10000,9500},IDLE={1350,1300,1250,1400,1300,1200,1050,1550};
 private boolean initialized;private int gear,candidate;private double time,candidateSince,lastShift=-10,shiftAge=10,filteredSpeed,previousSpeed;
 public int shifts;public float targetRpm,acceleration,cut;
 public int gear(){return gear;}public double shiftAge(){return shiftAge;}
 public void reset(){initialized=false;gear=candidate=shifts=0;time=0;lastShift=-10;shiftAge=10;filteredSpeed=previousSpeed=targetRpm=0;}
 public void update(double kmh,double seconds,int engine,boolean automatic,boolean drop,float strength){
  seconds=Math.max(.001,Math.min(.3,seconds));kmh=Double.isFinite(kmh)?Math.max(0,Math.min(400,kmh)):0;engine=Math.max(0,Math.min(7,engine));time+=seconds;
  if(!initialized){initialized=true;filteredSpeed=previousSpeed=kmh;gear=automatic?EngineSynth.gearForSpeed(kmh):(kmh<1?0:1);candidate=gear;candidateSince=time;}
  filteredSpeed+=(kmh-filteredSpeed)*(1-Math.exp(-seconds*7));acceleration=(float)((filteredSpeed-previousSpeed)/seconds);previousSpeed=filteredSpeed;
  int wanted=gear;if(!automatic)wanted=kmh<1?0:1;else if(filteredSpeed<.7)wanted=0;else if(gear==0)wanted=1;else if(gear<6&&filteredSpeed>UP[gear-1]+1.8)wanted=gear+1;else if(gear>1&&filteredSpeed<UP[gear-2]-3.5)wanted=gear-1;
  if(wanted!=candidate){candidate=wanted;candidateSince=time;}double dwell=wanted>gear?.18:.4;
  if(candidate!=gear&&time-candidateSince>=dwell&&time-lastShift>.55){gear=candidate;lastShift=time;if(automatic)shifts++;}
  shiftAge=time-lastShift;cut=automatic&&shiftAge<.14?(float)(1-Math.sin(Math.PI*Math.max(0,shiftAge)/.14)*.7):1;
  double ratio=drop&&automatic?RATIOS[gear]:.31;targetRpm=(float)Math.min(REDLINE[engine],IDLE[engine]+filteredSpeed*ratio*280);
  if(drop&&automatic&&shiftAge<.32)targetRpm*=1-Math.max(0,Math.min(1,strength))*.17f*(float)Math.exp(-shiftAge*12);
 }
}

