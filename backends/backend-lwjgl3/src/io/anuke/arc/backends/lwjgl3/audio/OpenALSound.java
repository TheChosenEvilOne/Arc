package io.anuke.arc.backends.lwjgl3.audio;

import io.anuke.arc.audio.Sound;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.openal.AL10.*;

/** @author Nathan Sweet */
public class OpenALSound implements Sound{
    private final OpenALAudio audio;
    private int bufferID = -1;
    private float duration;

    public OpenALSound(OpenALAudio audio){
        this.audio = audio;
    }

    void setup(byte[] pcm, int channels, int sampleRate){
        int bytes = pcm.length - (pcm.length % (channels > 1 ? 4 : 2));
        int samples = bytes / (2 * channels);
        duration = samples / (float)sampleRate;

        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes);
        buffer.order(ByteOrder.nativeOrder());
        buffer.put(pcm, 0, bytes);
        buffer.flip();

        if(bufferID == -1){
            bufferID = alGenBuffers();
            alBufferData(bufferID, channels > 1 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16, buffer.asShortBuffer(), sampleRate);
        }
    }

    public int play(float volume){
        if(audio.noDevice) return -1;
        int sourceID = audio.obtainSource(false);
        if(sourceID == -1){
            // Attempt to recover by stopping the least recently played sound
            audio.retain(this, true);
            sourceID = audio.obtainSource(false);
        }else audio.retain(this, false);
        // In case it still didn't work
        if(sourceID == -1) return -1;
        int soundId = (int)audio.getSoundId(sourceID);
        alSourcei(sourceID, AL_BUFFER, bufferID);
        alSourcei(sourceID, AL_LOOPING, AL_FALSE);
        alSourcef(sourceID, AL_GAIN, volume);
        alSourcePlay(sourceID);
        return soundId;
    }

    public int loop(){
        return loop(1);
    }

    @Override
    public int loop(float volume){
        if(audio.noDevice) return -1;
        int sourceID = audio.obtainSource(false);
        if(sourceID == -1) return -1;
        int soundId = (int)audio.getSoundId(sourceID);
        alSourcei(sourceID, AL_BUFFER, bufferID);
        alSourcei(sourceID, AL_LOOPING, AL_TRUE);
        alSourcef(sourceID, AL_GAIN, volume);
        alSourcePlay(sourceID);
        return soundId;
    }

    public void stop(){
        if(audio.noDevice) return;
        audio.stopSourcesWithBuffer(bufferID);
    }

    public void dispose(){
        if(audio.noDevice) return;
        if(bufferID == -1) return;
        audio.freeBuffer(bufferID);
        alDeleteBuffers(bufferID);
        bufferID = -1;
        audio.forget(this);
    }

    @Override
    public void stop(int soundId){
        if(audio.noDevice) return;
        audio.stopSound(soundId);
    }

    @Override
    public void pause(){
        if(audio.noDevice) return;
        audio.pauseSourcesWithBuffer(bufferID);
    }

    @Override
    public void pause(int soundId){
        if(audio.noDevice) return;
        audio.pauseSound(soundId);
    }

    @Override
    public void resume(){
        if(audio.noDevice) return;
        audio.resumeSourcesWithBuffer(bufferID);
    }

    @Override
    public void resume(int soundId){
        if(audio.noDevice) return;
        audio.resumeSound(soundId);
    }

    @Override
    public void setPitch(int soundId, float pitch){
        if(audio.noDevice) return;
        audio.setSoundPitch(soundId, pitch);
    }

    @Override
    public void setVolume(int soundId, float volume){
        if(audio.noDevice) return;
        audio.setSoundGain(soundId, volume);
    }

    @Override
    public void setLooping(int soundId, boolean looping){
        if(audio.noDevice) return;
        audio.setSoundLooping(soundId, looping);
    }

    @Override
    public void setPan(int soundId, float pan, float volume){
        if(audio.noDevice) return;
        audio.setSoundPan(soundId, pan, volume);
    }

    @Override
    public int play(float volume, float pitch, float pan){
        int id = play();
        setPitch(id, pitch);
        setPan(id, pan, volume);
        return id;
    }

    @Override
    public int loop(float volume, float pitch, float pan){
        int id = loop();
        setPitch(id, pitch);
        setPan(id, pan, volume);
        return id;
    }

    /** Returns the length of the sound in seconds. */
    public float duration(){
        return duration;
    }
}
