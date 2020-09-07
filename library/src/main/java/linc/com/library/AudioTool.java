package linc.com.library;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.audiofx.DynamicsProcessing;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.ContextCompat;

import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class AudioTool {

    private Context context;
    private String tmpDirectory;

    private static String AUDIO_TOOL_TMP = "tmp_audio_file_";

    // Tool settings
    private File audio;

    private AudioTool(Context context) {
        this.context = context;
        this.tmpDirectory = ContextCompat.getExternalFilesDirs(context, Environment.DIRECTORY_MUSIC)[0].getPath();
    }

    public static AudioTool getInstance(Context context) {
        return new AudioTool(context);
    }

    /**
     * Set audio file source
     * @param sourceAudio path to the source file
     */
    public AudioTool withAudio(File sourceAudio) {
        try {
            if(!sourceAudio.exists()) throw new FileNotFoundException();
            this.audio = new File(
                    tmpDirectory
                            + File.separator
                            + AUDIO_TOOL_TMP
                            + System.currentTimeMillis()
                            + FileManager.getFileExtension(sourceAudio)
            );
            FileManager.copyFile(sourceAudio, audio);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return this;
    }

    /**
     * Save output file
     * @param fullPath path to the output directory with file name
     */
    public void saveTo(String fullPath) {
        try {
            FileManager.copyFile(audio, new File(fullPath));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * Remove temporary file from current session
     */
    public void releaseCurrent() {
        audio.delete();
    }

    /**
     * Remove temporary files from all previous sessions
     */
    public void releaseAll() {
        File tmpStorage = new File(tmpDirectory);

        for(File tmpFile : tmpStorage.listFiles()) {
            if(tmpFile.getName().startsWith(AUDIO_TOOL_TMP))
                tmpFile.delete();
        }
    }

    /**
     * @param start format in second -> 0
     * @param end   format in second -> 0
     */
    public AudioTool cutAudio(int start, int end, OnFileComplete onCompleteCallback) {
        cutAudio(start, end);
        onCompleteCallback.onComplete(audio);
        return this;
    }

    public AudioTool cutAudio(int start, int end) {
        FFmpeg.execute("-y", "-i", audio.getPath(), "-ss" , "" + start, "-to", "" + end, audio.getPath());
        return this;
    }

    /**
     * @param start format -> "00:00:00"
     * @param end   format -> "00:20:30"
     * "mm:ss:ms"
     */
    public AudioTool cutAudio(String start, String end, OnFileComplete onCompleteCallback) {
        FFmpeg.execute("-y", "-i", audio.getPath(), "-ss" , start, "-to", end, audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    /**
     * @param format     format output image format -> Image.PNG
     * @param width      format with in px -> 1920
     * @param height     format height in px -> 1080
     * @param color      format hex color value -> #4a4a4a
     * @param onCompleteCallback lambda with result image
     */
    public AudioTool generateWaveform(String format,
                               int width,
                               int height,
                               String color,
                               OnFileComplete onCompleteCallback
    ) {

        return this;
    }

    /**
     * @param volume     volume percent -> 0.75 as 75% of volume
     * @param onCompleteCallback lambda with result audio
     */
    public AudioTool changeAudioVolume(float volume, int start, int end, OnFileComplete onCompleteCallback) {
        volume = Limiter.limit(0f, 12000f, volume);
        start = Limiter.limit(0, ((int) getDurationMillis() / 1000), start);
        end = Limiter.limit(0, ((int) getDurationMillis() / 1000), end);
        FFmpeg.execute("-y", "-i", audio.getPath(), "-af", "volume=enable='between(t," + start + "," + end + ")':volume=" + volume, audio.getPath());
        onResultFile(onCompleteCallback);
        /*
            If we want our volume to be half of the input volume:

            ffmpeg -i input.wav -filter:a "volume=0.5" output.wav
            150% of current volume:

            ffmpeg -i input.wav -filter:a "volume=1.5" output.wav


            // normalize
            ffmpeg -i input.wav -filter:a loudnorm output.wav
         */

        // todo start and end ffmpeg -y -i kygo.mp3 -af "volume=enable='between(t,5,10)':volume=0" muted.mp3
        return this;
    }

    public AudioTool changeAudioVolume(float volume, OnFileComplete onCompleteCallback) {
        //ffmpeg -i input.wav -filter:a "volume=1.5" output.wav
        FFmpeg.execute("-y", "-i", audio.getPath(), "-filter:a", "\"volume=" + volume + "\"", audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool normalizeAudioVolume(OnFileComplete onCompleteCallback) {
        FFmpeg.execute("-y", "-i", audio.getPath(), "-filter:a", "loudnorm", audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool changeAudioSpeed(Float xSpeed, OnFileComplete onCompleteCallback) {
        FFmpeg.execute("-y", "-i", audio.getPath(), "-filter:a", "\"atempo=2.0\"", audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool changeAudioPitch() {
        //							rep---	the same   rep---
//        ffmpeg -y -i kygo.mp3 -filter_complex "asetrate=48000*2^(-10/12),atempo=1/2^(-10/12)" p_10_ky.mp3
        return this;
    }

    public AudioTool changeAudioBass(float bass/*[-20;20]*/, float width/*[0;1]*/, int frequency/*[0 - 999999]*/, OnFileComplete onCompleteCallback) {
        // ffmpeg -y -i kygo.mp3 -af bass=g=10:w=0.5:f=150 bass.mp3
        bass = Limiter.limit(-20, 20, bass);
        width = Limiter.limit(0, 1, width);
        frequency = Limiter.limit(0, 999999, frequency);
        FFmpeg.execute("-y", "-i", audio.getPath(), "-af", "bass=g=" + bass + ":w=" + width + ":f=" + frequency, audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool removeAudioNoise(OnFileComplete onCompleteCallback) {
        filterAudio(400, 4000, onCompleteCallback);
        return this;
    }

    public AudioTool removeVocal(OnFileComplete onCompleteCallback) {
//        ffmpeg -i song.mp3 -af pan="stereo|c0=c0|c1=-1*c1" -ac 1 karaoke.mp3
        FFmpeg.execute("-y", "-i", audio.getPath(), "-af", "pan=\"stereo|c0=c0|c1=-1*c1\"", "-ac", "1", audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool filterAudio(int highpass, int lowpass, OnFileComplete onCompleteCallback) {
        highpass = Limiter.limit(0, 999999, highpass);
        lowpass = Limiter.limit(0, 999999, lowpass);
        FFmpeg.execute("-y", "-i", audio.getPath(), "-af", "\"highpass=f=" + highpass + ", lowpass=f=" + lowpass + "\"", audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool reverseAudio(OnFileComplete onCompleteCallback) {
        FFmpeg.execute("-y", "-i", audio.getPath(), "-map", "0", "-c:v", "copy", "-af", "\"areverse\"", audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool applyEchoEffect(Echo echo, OnFileComplete onCompleteCallback) {
        String filter;
        switch (echo) {
            case ECHO_TWICE_INSTRUMENTS: filter = "\"aecho=0.8:0.88:60:0.4\"";break;
            case ECHO_METALLIC: filter = "\"aecho=0.8:0.88:6:0.4\"";break;
            case ECHO_OPEN_AIR: filter = "\"aecho=0.8:0.9:1000:0.3\"";break;
            default: filter = "\"aecho=0.8:0.9:1000|1800:0.3|0.25\"";
        }
        FFmpeg.execute("-y", "-i", audio.getPath(), "-filter_complex", filter, audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool applyVibratoEffect(float frequency/*[0.1 - 20000.0]*/, float depth/*[0;1]*/, OnFileComplete onCompleteCallback) {
        frequency = Limiter.limit(0.1f, 20000, frequency);
        depth = Limiter.limit(0, 1, depth);
        FFmpeg.execute("-y", "-i", audio.getPath(), "-filter_complex", "vibrato=f=" + frequency + ":d=" + depth, audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool applyReverbEffect(float audioDepth, float reverbDepth, OnFileComplete onCompleteCallback) {

//        ffmpeg -y -i kygo.mp3 -i lev_cut.mp3 -filter_complex '[0] [1] afir=dry=0.1:wet=0.1' reverb.mp3
        onResultFile(onCompleteCallback);
        return this;
    }

    public AudioTool applyShifterEffect(int transitionTime, int width/*[0;2]*/, OnFileComplete onCompleteCallback) {

        // ffmpeg -y -i kygo.mp3 -filter_complex "apulsator=mode=sine:hz=0.125:width=0" shifter.mp3

        onResultFile(onCompleteCallback);

        // hz = 1/transitionTime

        //
        //   1
        // ----- = 7.8
        // 0.128
        //

        //
        //   1      7.8
        // ----- = -----
        //   x       1
        //

        //
        //   1      7.8
        // ----- = -----
        //   x       1
        //

        return this;
    }

    public AudioTool convertVideoToAudio(OnFileComplete onCompleteCallback) {
        FFmpeg.execute("-y", "-i", audio.getPath(), "-vn" , audio.getPath());
        onResultFile(onCompleteCallback);
        return this;
    }

    public void joinAudios(File ... audios  ) {
//        ffmpeg -f concat -safe 0 -i join.txt -c copy output.mp4
    }

    public AudioTool getDuration(Duration duration, OnNumberComplete onNumberComplete) {
        switch (duration) {
            case MILLIS: onNumberComplete.onComplete(getDurationMillis());
                break;
            case SECONDS: onNumberComplete.onComplete(getDurationMillis() / 1000);
                break;
            case MINUTES: onNumberComplete.onComplete((getDurationMillis() % (1000 * 60 * 60)) / (1000 * 60));
                break;
        }
        return this;
    }

    public AudioTool executeFFmpeg(String ... command) {
        return this;
    }

    interface OnCompleteCallback<T> {
        void onComplete(T output);
    }

    public interface OnFileComplete extends OnCompleteCallback<File> {}
    public interface OnNumberComplete extends OnCompleteCallback<Long> {}

    private long getDurationMillis() {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(audio.getAbsolutePath());
        String durationStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        return Long.parseLong(durationStr);
    }

    private void onResultFile(OnFileComplete onCompleteCallback) {
        if(onCompleteCallback != null) {
            onCompleteCallback.onComplete(audio);
        }
    }

}
