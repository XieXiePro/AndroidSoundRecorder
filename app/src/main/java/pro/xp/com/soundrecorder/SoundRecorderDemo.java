package pro.xp.com.soundrecorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.ListView;

import java.util.ArrayList;

import pro.xp.com.soundrecorderlib.recorder.Recorder;
import pro.xp.com.soundrecorderlib.recorder.ui.SoundRecorder;
import pro.xp.com.soundrecorderlib.seleteaudio.model.AudioBean;
import pro.xp.com.soundrecorderlib.seleteaudio.ui.AudioSelectAdapter;
import pro.xp.com.soundrecorderlib.seleteaudio.ui.SeleteAudioActivity;

import static pro.xp.com.soundrecorderlib.recorder.Recorder.getAudioFile;
import static pro.xp.com.soundrecorderlib.seleteaudio.ui.SeleteAudioActivity.ANSWER_AUDIO_SELECT;
import static pro.xp.com.soundrecorderlib.seleteaudio.ui.SeleteAudioActivity.RESULT_AUDIO_SELECT;

public class SoundRecorderDemo extends Activity implements AudioSelectAdapter.AudioItemClickListener, AudioSelectAdapter.AudioItemDeleteClickListener, Recorder.OnStateChangedListener {
    /**
     * 从系统中选择音频
     */
    protected static final int REQUESTCODE_AUDIO_SELECT = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_recorder);
        mLvAudio = findViewById(R.id.id_lv_audio_checked);
        findViewById(R.id.start_record_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SoundRecorder.launchAct(SoundRecorderDemo.this);
            }
        });
        findViewById(R.id.select_audio_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onChooseAudio();
            }
        });
    }

    public void onChooseAudio() {
        //选取音频
        Intent intent = new Intent(this, SeleteAudioActivity.class);
        if (mAudioResultList != null) {
            intent.putParcelableArrayListExtra(RESULT_AUDIO_SELECT, mAudioResultList);
        }
        startActivityForResult(intent, REQUESTCODE_AUDIO_SELECT);
    }

    /**
     * 音频文件显示控件
     */
    ListView mLvAudio;
    /**
     * 音频文件资源
     */
    ArrayList<AudioBean> mAudioResultList;

    AudioSelectAdapter mAapter;

    Recorder mRecorder = null;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == REQUESTCODE_AUDIO_SELECT) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "SoundRecorder");
            //选择音频
            mAudioResultList = data.getParcelableArrayListExtra(ANSWER_AUDIO_SELECT);
            mAapter = new AudioSelectAdapter(this);
            //1:查询，0，选择；
            mAapter.setType(2);
            mAapter.setData(mAudioResultList);
            mAapter.setListener(this);
            mAapter.setDelListener(this);
            mLvAudio.setAdapter(mAapter);
        }
    }

    @Override
    public void onAudioItemClick(AudioBean data) {
        if (data.isShowProcess()) {
            data.setShowProcess(false);
        } else {
            data.setShowProcess(true);
        }
        mAapter.notifyDataSetChanged();
        if (data.recorder == null) {
            mRecorder = new Recorder(getAudioFile());
            mRecorder.setOnStateChangedListener(this);
            data.recorder = mRecorder;
        }
    }

    @Override
    public void onAudioItemDeleteClick(AudioBean data) {
        int type = mAapter.getType();
        if (type == 2) {
            mAudioResultList.clear();
            mAapter.setData(mAudioResultList);
        }
        mAapter.setListener(this);
        mAapter.setDelListener(this);
        mLvAudio.setAdapter(mAapter);
        mAapter.notifyDataSetChanged();
    }

    private PowerManager.WakeLock mWakeLock;
    // Some error messages are displayed in the UI,
    String mErrorUiMessage = null;
    boolean mSampleInterrupted = false;

    @Override
    public void onStateChanged(int state) {
        if (state == Recorder.PLAYING_STATE) {
            mSampleInterrupted = false;
            mErrorUiMessage = null;
            // we don't want to go to sleep while recording or playing
            mWakeLock.acquire();
        } else {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        updateUi();
    }

    private void updateUi() {
        mAapter.notifyDataSetChanged();
    }

    @Override
    public void onError(int error) {
        Resources res = getResources();

        String message = null;
        switch (error) {
            case Recorder.SDCARD_ACCESS_ERROR:
                message = res.getString(R.string.error_sdcard_access);
                break;
            case Recorder.IN_CALL_RECORD_ERROR:
                // update error message to reflect that the recording could not be
                // performed during a call.
            case Recorder.INTERNAL_ERROR:
                message = res.getString(R.string.error_app_internal);
                break;
            default:
                break;
        }
        if (message != null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(message)
                    .setPositiveButton(R.string.button_ok, null)
                    .setCancelable(false)
                    .show();
        }
    }
}