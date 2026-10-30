import com.onesecondx.velocity.*;
import java.util.*;
public class V4SoundCheck {
 static int checks;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static long audio(int engine,int exhaust,int flags){EngineSynth synth=new EngineSynth();short[] b=new short[512];long hash=1,energy=0;for(int j=0;j<48;j++){synth.render(b,engine,exhaust,75,.4f,true,48000,flags,.65f,.3f,.1f,.5f);for(short v:b){checkBound(v);hash=hash*31+v;energy+=(long)v*v;}}check(energy>0,"Audible profile "+engine+"/"+exhaust);return hash;}
 static void checkBound(short s){if(Math.abs((int)s)>32760)throw new AssertionError("Clipped sample");}
 public static void main(String[] args){
  Set<Long> hashes=new HashSet<>();for(int e=0;e<8;e++)for(int x=0;x<12;x++)hashes.add(audio(e,x,7));check(hashes.size()==96,"96 unique bounded profiles");
  DriveModel drive=new DriveModel();float lowestCut=1;for(int i=0;i<400;i++){drive.update(Math.min(210,i*.7),.05,0,true,true,.6f);lowestCut=Math.min(lowestCut,drive.cut);}check(drive.gear()==6&&drive.shifts>=5,"Ramping speed shifts up to sixth");check(lowestCut<.5,"Shift has brief torque cut");
  DriveModel jitter=new DriveModel();for(int i=0;i<200;i++)jitter.update(i%2==0?48.4:47.6,.1,0,true,true,.5f);check(jitter.shifts==0,"GPS jitter does not chatter gears");
  DriveModel noAuto=new DriveModel();for(int i=0;i<200;i++)noAuto.update(i,.1,0,false,true,.5f);check(noAuto.shifts==0&&noAuto.gear()==1,"Disabling automatic prevents gear changes");
  DriveModel continuous=new DriveModel();float previous=0;boolean monotonic=true;for(int i=0;i<200;i++){continuous.update(i,.1,0,true,false,.7f);if(continuous.targetRpm<previous-.01)monotonic=false;previous=continuous.targetRpm;}check(monotonic,"Rev-drop off keeps rising RPM continuous");
  EngineSynth pops=new EngineSynth(),quiet=new EngineSynth();short[] b=new short[512];for(int i=0;i<800;i++){double k=i<200?110:Math.max(0,110-(i-200)*.4);pops.render(b,0,8,k,.35f,true,48000,15,.7f,.25f,.12f,.5f);for(short v:b)checkBound(v);quiet.render(b,0,8,k,.35f,true,48000,7,.7f,.25f,.12f,.5f);}check(pops.ignitionEvents>0&&pops.ignitionEvents<=7,"Lift-off events occur with cooldown");check(quiet.ignitionEvents==0,"Ignition toggle disables events");
  EngineSynth silence=new EngineSynth();silence.render(b,1,11,100,.7f,false,48000);boolean zero=true;for(short v:b)if(v!=0)zero=false;check(zero,"Disabled engine is silent");
  check(audio(4,8,7)!=audio(4,8,0),"Gear flags change audio output");System.out.println("PASS: "+checks+" DSP/drivetrain checks; 96 exhaust/engine combinations, shift hysteresis, cut, no-auto, no-drop, ignition cooldown/toggle and silence.");
 }
}
