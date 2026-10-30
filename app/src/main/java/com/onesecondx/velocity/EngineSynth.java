package com.onesecondx.velocity;
/** Procedural audio: virtual engine timing, no recordings or ECU channels. */
public final class EngineSynth {
 public static final String[] ENGINES={"S1000RR · Race inline-4","H2R · Supercharged four","CBR650R · Smooth inline-4","ZX-10R · Screaming four","R1 · Crossplane four","MT-09 · Triple","V-Twin · Pulse 270°","155 Single · Thumper"};
 public static final String[] EXHAUSTS={"Stock · Quiet","Titanium · Crisp","Carbon · Warm","Short can · Sharp","Straight · Raw","Megaphone · Resonant","Underbelly · Deep","Track · Rasp","Velvet bass · นุ่มลึก","Touring deep · ทุ้มละมุน","Dual chamber · เบสแน่น","Long muffler · เงียบอุ่น"};
 public static final int AUTO_SHIFT=1,REV_DROP=2,SHIFT_FX=4,IGNITION=8,STARTER=16;
 private static final int TABLE=16384;private static final float[] SIN=new float[TABLE],PULSE=new float[TABLE];
 private static final double[][] FIRING={{0,.25,.5,.75},{0,.25,.5,.75},{0,.25,.5,.75},{0,.25,.5,.75},{0,.375,.625,.75},{0,1.0/3,2.0/3},{0,.375},{0}};
 private static final float[] REDLINE={14500,14000,12500,14500,14000,11500,10000,9500},IDLE={1350,1300,1250,1400,1300,1200,1050,1550};
 private static final float[] CUTOFF={.10f,.24f,.13f,.36f,.46f,.20f,.075f,.31f,.045f,.065f,.09f,.055f},DRIVE={1.05f,1.4f,1.2f,1.65f,2f,1.55f,1.65f,1.8f,1.15f,1.1f,1.35f,.95f};
 private static final double[] RATIOS={1,1,.72,.54,.43,.36,.31};
 static{for(int i=0;i<TABLE;i++){double p=i/(double)TABLE;SIN[i]=(float)Math.sin(p*Math.PI*2);PULSE[i]=(float)(Math.exp(-p*38)*Math.sin(p*Math.PI*22));}}
 public static int gearForSpeed(double kmh){if(kmh<1)return 0;return kmh<24?1:kmh<48?2:kmh<78?3:kmh<112?4:kmh<155?5:6;}
 public static float rpmForSpeed(double kmh,int engine){engine=Math.max(0,Math.min(7,engine));return(float)Math.min(REDLINE[engine],IDLE[engine]+Math.max(0,kmh)*RATIOS[gearForSpeed(kmh)]*280);}
 private static float sin(double phase){return SIN[((int)(phase*TABLE))&(TABLE-1)];}
 private final DriveModel drive=new DriveModel();private double phase,whinePhase,totalTime,lastPop=-10;private float rpm=1250,level,low,dc,bassLow,popEnvelope;private int noiseSeed=0x137b39e1;
 public int ignitionEvents;public float rpm(){return rpm;}public int gear(){return drive.gear();}public int shifts(){return drive.shifts;}
 public void render(short[] output,int engine,int exhaust,double kmh,float volume,boolean audible,int rate){render(output,engine,exhaust,kmh,volume,audible,rate,AUTO_SHIFT|REV_DROP|SHIFT_FX,.5f,.4f,.08f,.45f);}
 public void render(short[] output,int engine,int exhaust,double kmh,float volume,boolean audible,int rate,int flags,float bass,float texture,float ignition,float drop){
  engine=Math.max(0,Math.min(7,engine));exhaust=Math.max(0,Math.min(EXHAUSTS.length-1,exhaust));bass=Math.max(0,Math.min(1,bass));texture=Math.max(0,Math.min(1,texture));ignition=Math.max(0,Math.min(.3f,ignition));
  double dt=output.length/(double)rate;totalTime+=dt;drive.update(kmh,dt,engine,(flags&AUTO_SHIFT)!=0,(flags&REV_DROP)!=0,drop);
  float target=drive.targetRpm,gain=audible?Math.max(0,Math.min(.7f,volume)):0,load=(float)Math.max(.14,Math.min(1,.35+drive.acceleration*.018+kmh/350));
  if((flags&IGNITION)!=0&&audible&&ignition>0&&drive.acceleration<-5&&rpm>3200&&totalTime-lastPop>1.1){popEnvelope=ignition*.75f;lastPop=totalTime;ignitionEvents++;}if((flags&IGNITION)==0)popEnvelope=0;
  float tone=CUTOFF[exhaust]*(.7f+texture*.6f),saturation=DRIVE[exhaust];double[] offsets=FIRING[engine];
  for(int i=0;i<output.length;i++){
   rpm+=(target-rpm)*.00022f;level+=(gain-level)*.0012f;phase+=rpm/(120.0*rate);if(phase>=1)phase-=1;
   float pulse=0;for(double offset:offsets){double p=phase-offset;if(p<0)p+=1;pulse+=PULSE[(int)(p*TABLE)&(TABLE-1)];}
   noiseSeed^=noiseSeed<<13;noiseSeed^=noiseSeed>>>17;noiseSeed^=noiseSeed<<5;float noise=noiseSeed/(float)Integer.MAX_VALUE,firing=sin(phase*offsets.length),harmonic=sin(phase*offsets.length*2);
   float raw=pulse*(1.8f+load)+firing*.32f+harmonic*(.06f+texture*.13f)+noise*(.008f+texture*load*.08f);
   if(engine==1){whinePhase+=Math.min(8500,rpm/60*9)/rate;if(whinePhase>=1)whinePhase-=1;raw+=sin(whinePhase)*(.035f+load*.06f);}if(exhaust==5)raw+=sin(phase*offsets.length*3)*.17f;if(exhaust==6||exhaust>=8)raw+=firing*(exhaust==10?.44f:.26f);if(exhaust==7)raw+=noise*Math.abs(pulse)*.18f;
   if((flags&SHIFT_FX)!=0&&(flags&AUTO_SHIFT)!=0)raw*=drive.cut;low+=tone*(raw-low);bassLow+=.025f*(firing-bassLow);float mixed=low*(1-bass*.15f)+bassLow*bass*(exhaust>=8?.65f:.36f);
   if((flags&STARTER)!=0&&totalTime<.32)mixed*=.25f+.75f*(float)(totalTime/.32);popEnvelope*=.9984f;mixed+=popEnvelope*(noise*.5f+sin(phase*2)*.4f);
   dc+=(mixed-dc)*.0008f;float value=(mixed-dc)*saturation;value=value/(1+Math.abs(value));output[i]=(short)Math.max(-32760,Math.min(32760,value*level*30000));
  }
 }
}
