package com.onesecondx.velocity;

import android.content.Context;
import android.media.*;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

/** Streaming DSP on its own thread; view/GPS rendering never generates audio samples. */
public final class EngineAudio {
    private final AudioManager manager;
    private final AudioFocusRequest focus;
    private volatile boolean alive,focused;
    private volatile int engine,exhaust;
    private volatile double kmh;
    private volatile float volume=.25f;
    private volatile int options=EngineSynth.AUTO_SHIFT|EngineSynth.REV_DROP|EngineSynth.SHIFT_FX;
    private volatile float bass=.5f,texture=.4f,ignition=.1f,drop=.45f;
    public volatile float currentRpm;
    public volatile int currentGear;
    private Thread worker;
    public volatile String error="";
    private volatile long generation;
    public EngineAudio(Context context){
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(change->focused=change==AudioManager.AUDIOFOCUS_GAIN,new Handler(Looper.getMainLooper())).build();
    }
    public synchronized void update(boolean active,int engine,int exhaust,double kmh,float volume){
        this.engine=engine;this.exhaust=exhaust;this.kmh=kmh;this.volume=volume;
        if(!active){stop();return;}if(alive)return;
        focused=manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(!focused){error="ระบบยังไม่อนุญาตเสียง ลองเปิดเสียงอีกครั้ง";return;}
        alive=true;error="";long session=++generation;
        worker=new Thread(()->stream(session),"Velocity-engine-audio");worker.start();
    }
    public void configure(int flags,float bass,float texture,float ignition,float drop){this.options=flags;this.bass=bass;this.texture=texture;this.ignition=ignition;this.drop=drop;}
    private void stream(long session){
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);AudioTrack track=null;
        try{
            int rate=48000;int minimum=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(4096,minimum*2)).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
            if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("AudioTrack unavailable");
            EngineSynth synth=new EngineSynth();short[] buffer=new short[512];track.play();
            while(alive&&generation==session){synth.render(buffer,engine,exhaust,kmh,volume,focused,rate,options,bass,texture,ignition,drop);currentRpm=synth.rpm();currentGear=synth.gear();int offset=0;while(offset<buffer.length&&alive&&generation==session){int count=track.write(buffer,offset,buffer.length-offset,AudioTrack.WRITE_BLOCKING);if(count<0)throw new IllegalStateException("Audio write failed: "+count);offset+=count;}}
            for(int fade=0;fade<6;fade++){synth.render(buffer,engine,exhaust,kmh,0,false,rate,options,bass,texture,0,drop);track.write(buffer,0,buffer.length,AudioTrack.WRITE_BLOCKING);}
        }catch(Exception e){error="ไม่สามารถเปิดเสียงบนอุปกรณ์นี้ได้";}
        finally{if(track!=null){try{track.pause();track.flush();}catch(Exception ignored){}track.release();}synchronized(this){if(generation==session){alive=false;focused=false;manager.abandonAudioFocusRequest(focus);}}}
    }
    public synchronized void stop(){if(!alive)return;alive=false;++generation;focused=false;currentRpm=0;currentGear=0;manager.abandonAudioFocusRequest(focus);}
    public boolean isActive(){return alive&&focused;}
}

