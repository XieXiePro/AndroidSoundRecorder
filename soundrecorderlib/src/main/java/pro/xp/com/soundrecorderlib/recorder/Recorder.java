package pro.xp.com.soundrecorderlib.recorder;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Bundle;
import android.os.Environment;

import com.czt.mp3recorder.MP3Recorder;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("all")
public class Recorder extends MP3Recorder implements OnCompletionListener, OnErrorListener {
    static final String SAMPLE_PREFIX = "recording";
    static final String SAMPLE_PATH_KEY = "sample_path";
    static final String SAMPLE_LENGTH_KEY = "sample_length";
    static final String MP3_FILE_PATH = "/SoundRecorder/";

    public static final int IDLE_STATE = 0;
    public static final int RECORDING_STATE = 1;
    public static final int PLAYING_STATE = 2;

    int mState = IDLE_STATE;

    public static final int NO_ERROR = 0;
    public static final int SDCARD_ACCESS_ERROR = 1;
    public static final int INTERNAL_ERROR = 2;
    public static final int IN_CALL_RECORD_ERROR = 3;

    /**
     * Default constructor. Setup recorder with default sampling rate 1 channel,
     * 16 bits pcm
     *
     * @param recordFile target file
     */
    public Recorder(File recordFile) {
        super(recordFile);
    }

    public interface OnStateChangedListener {
        public void onStateChanged(int state);

        public void onError(int error);
    }

    OnStateChangedListener mOnStateChangedListener = null;
    // time at which latest record or play operation started
    long mSampleStart = 0;
    // length of current sample
    int mSampleLength = 0;
    static File mSampleFile = null;

    //    MediaRecorder mRecorder = null;
    MP3Recorder mRecorder = null;
    MediaPlayer mPlayer = null;


    public void saveState(Bundle recorderState) {
        recorderState.putString(SAMPLE_PATH_KEY, mSampleFile.getAbsolutePath());
        recorderState.putInt(SAMPLE_LENGTH_KEY, mSampleLength);
    }

    public int getMaxAmplitude() {
        if (mState != RECORDING_STATE) {
            return 0;
        }
        return mRecorder.getMaxVolume();
//        return mRecorder.getMaxAmplitude();
    }

    public void restoreState(Bundle recorderState) {
        String samplePath = recorderState.getString(SAMPLE_PATH_KEY);
        if (samplePath == null) {
            return;
        }
        int sampleLength = recorderState.getInt(SAMPLE_LENGTH_KEY, -1);
        if (sampleLength == -1) {
            return;
        }

        File file = new File(samplePath);
        if (!file.exists()) {
            return;
        }
        if (mSampleFile != null
                && mSampleFile.getAbsolutePath().compareTo(file.getAbsolutePath()) == 0) {
            return;
        }
        delete();
        mSampleFile = file;
        mSampleLength = sampleLength;
        signalStateChanged(IDLE_STATE);
    }

    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mOnStateChangedListener = listener;
    }

    public int state() {
        return mState;
    }

    public int progress() {
        if (mState == RECORDING_STATE || mState == PLAYING_STATE) {
            return (int) ((System.currentTimeMillis() - mSampleStart) / 1000);
        }
        return 0;
    }

    public int sampleLength() {
        return mSampleLength;
    }

    public File sampleFile() {
        return mSampleFile;
    }

    public static File getAudioFile() {
        File mSampleFile = null;
        return mSampleFile;
    }

    /**
     * Resets the recorder state. If a sample was recorded, the file is deleted.
     */
    public void delete() {
        stop();
        if (mSampleFile != null) {
            mSampleFile.delete();
        }
        mSampleFile = null;
        mSampleLength = 0;
        signalStateChanged(IDLE_STATE);
    }

    /**
     * Resets the recorder state. If a sample was recorded, the file is left on disk and will
     * be reused for a new recording.
     */
    public void clear() {
        stop();
        mSampleLength = 0;
        signalStateChanged(IDLE_STATE);
    }

    public static String getSDPath() {
        File sdDir = null;
        // 判断sd卡是否存在
        boolean sdCardExist = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (sdCardExist) {
            //获取跟目录
            sdDir = Environment.getExternalStorageDirectory();
        }
        return sdDir != null ? sdDir.toString() : null;
    }

    public void startRecording(int outputfileformat, String extension, Context context) {
        stop();
        if (mSampleFile == null) {
            //创建制定文件夹存放录音文件
            String audiofile = getSDPath() + MP3_FILE_PATH;
            File sampleDir = new File(audiofile);
            if (!sampleDir.exists()) {
                sampleDir.mkdirs();
            }
            if (!sampleDir.canWrite())
            // Workaround for broken sdcard support on the device.
            {
                sampleDir = new File("/sdcard/sdcard");
            }

            try {
                mSampleFile = File.createTempFile(SAMPLE_PREFIX, extension, sampleDir);
            } catch (IOException e) {
                setError(SDCARD_ACCESS_ERROR);
                return;
            }
        }

        mRecorder = new MP3Recorder(mSampleFile);

        try {
            mRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mSampleStart = System.currentTimeMillis();
        setState(RECORDING_STATE);
    }

    public void stopRecording() {
        if (mRecorder == null) {
            return;
        }
        mRecorder.stop();
        mRecorder = null;

        mSampleLength = (int) ((System.currentTimeMillis() - mSampleStart) / 1000);
        setState(IDLE_STATE);
    }

    public void startPlayback() {
        stop();

        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mSampleFile.getAbsolutePath());
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            setError(INTERNAL_ERROR);
            mPlayer = null;
            return;
        } catch (IOException e) {
            setError(SDCARD_ACCESS_ERROR);
            mPlayer = null;
            return;
        }

        mSampleStart = System.currentTimeMillis();
        setState(PLAYING_STATE);
    }


    public void startPlayback(String filePath) {
        stop();

        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(filePath);
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            setError(INTERNAL_ERROR);
            mPlayer = null;
            return;
        } catch (IOException e) {
            setError(SDCARD_ACCESS_ERROR);
            mPlayer = null;
            return;
        }

        mSampleStart = System.currentTimeMillis();
        setState(PLAYING_STATE);
    }


    public void stopPlayback() {
        if (mPlayer == null)
        // we were not in playback
        {
            return;
        }

        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
        setState(IDLE_STATE);
    }

    @Override
    public void stop() {
        stopRecording();
        stopPlayback();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        stop();
        setError(SDCARD_ACCESS_ERROR);
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        stop();
    }

    private void setState(int state) {
        if (state == mState) {
            return;
        }
        mState = state;
        signalStateChanged(mState);
    }

    private void signalStateChanged(int state) {
        if (mOnStateChangedListener != null) {
            mOnStateChangedListener.onStateChanged(state);
        }
    }

    private void setError(int error) {
        if (mOnStateChangedListener != null) {
            mOnStateChangedListener.onError(error);
        }
    }
}