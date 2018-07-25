package pro.xp.com.soundrecorderlib.seleteaudio.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import pro.xp.com.soundrecorderlib.R;
import pro.xp.com.soundrecorderlib.recorder.Recorder;
import pro.xp.com.soundrecorderlib.seleteaudio.manager.AudioSelectManager;
import pro.xp.com.soundrecorderlib.seleteaudio.model.AudioBean;

import static pro.xp.com.soundrecorderlib.recorder.Recorder.getAudioFile;

@SuppressWarnings("all")
public class SeleteAudioActivity extends Activity implements AudioSelectAdapter.AudioItemClickListener,AudioSelectAdapter.AudioItemDeleteClickListener, Recorder.OnStateChangedListener {
    public static final String TAG = "SeleteAudioActivity";

    public static final String ANSWER_AUDIO_SELECT = "answer_audio_select";
    public static final String RESULT_AUDIO_SELECT = "result_audio_select";

    private ListView mLvAudio;
    private TextView mBtnCommit;
    private TextView mBtnBack;
    private List<AudioBean> mAudioBeanList;
    private List<AudioBean> mSelectedAudioBeanList;
    private AudioSelectAdapter mAapter;
    private Recorder mRecorder;
    private PowerManager.WakeLock mWakeLock;

    static final String RECORDER_STATE_KEY = "recorder_state";
    static final String SAMPLE_INTERRUPTED_KEY = "sample_interrupted";
    static final String MAX_FILE_SIZE_KEY = "max_file_size";

    boolean mSampleInterrupted = false;
    // Some error messages are displayed in the UI,
    String mErrorUiMessage = null;
    // not a dialog. This happens when a recording
    // is interrupted for some reason.

    // can be specified in the intent
    long mMaxFileSize = -1;

    String mTimerFormat;

    public static void launchAct(Context context) {
        context.startActivity(new Intent(context, SeleteAudioActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_audio);
        initData();
        initView();
    }

    private void initData() {
        mSelectedAudioBeanList = new ArrayList<>();
        Intent intent = getIntent();
        mSelectedAudioBeanList = intent.getParcelableArrayListExtra(RESULT_AUDIO_SELECT);
        mAudioBeanList = AudioSelectManager.getAudioBeanList(AudioSelectManager.MODE_PROVIDER, AudioSelectManager.MP3_FILE_PATH);
        Log.d(TAG, mAudioBeanList.toString());
        if (mSelectedAudioBeanList != null) {
            for (AudioBean audioResultBean : mSelectedAudioBeanList) {
                for (AudioBean audioBean : mAudioBeanList) {
                    if (audioBean.getPath().equals(audioResultBean.getPath())) {
                        audioBean.setUpload(audioResultBean.isUpload());
                    }
                }
            }
        }
        Log.d(TAG, mAudioBeanList.toString());
    }

    private void initView() {
        mLvAudio = (ListView) findViewById(R.id.id_lv_audio_list);
        mBtnBack = (TextView) findViewById(R.id.id_btn_back);
        mBtnCommit = (TextView) findViewById(R.id.id_btn_commit);
        mBtnCommit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitForResult();
            }
        });
        mBtnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SeleteAudioActivity.this.finish();
            }
        });

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "SoundRecorder");

        mAapter = new AudioSelectAdapter(this);
        Log.d(TAG, mAudioBeanList.toString());
        mAapter.setData(mAudioBeanList);
        int type = getIntent().getIntExtra("type", 0);
        //选择音频
        mAapter.setType(type);
        mAapter.setListener(this);
        mAapter.setDelListener(SeleteAudioActivity.this);
        mLvAudio.setAdapter(mAapter);
        mAapter.notifyDataSetChanged();
    }

    // return to the result
    private void submitForResult() {
        if (mSelectedAudioBeanList == null) {
            mSelectedAudioBeanList = new ArrayList<>();
        } else {
            mSelectedAudioBeanList.clear();
        }
        try {
            //返回选中的音频
            mSelectedAudioBeanList.add(mAudioBeanList.get(mAapter.getSelected()));
        } catch (Exception e) {
            Log.d(TAG, e.toString());
            e.printStackTrace();
        }

        Intent i = new Intent();
        i.putParcelableArrayListExtra(ANSWER_AUDIO_SELECT, (ArrayList<? extends Parcelable>) mSelectedAudioBeanList);
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void onAudioItemClick(AudioBean data) {
        mAapter.notifyDataSetChanged();
        if (data.recorder == null) {
            mRecorder = new Recorder(getAudioFile());
            mRecorder.setOnStateChangedListener(this);
            data.recorder = mRecorder;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mRecorder == null || mRecorder.sampleLength() == 0) {
            return;
        }
        Bundle recorderState = new Bundle();
        mRecorder.saveState(recorderState);
        recorderState.putBoolean(SAMPLE_INTERRUPTED_KEY, mSampleInterrupted);
        recorderState.putLong(MAX_FILE_SIZE_KEY, mMaxFileSize);

        outState.putBundle(RECORDER_STATE_KEY, recorderState);
    }

    @Override
    public void onStateChanged(int state) {
        if (state == Recorder.PLAYING_STATE) {
            mSampleInterrupted = false;
            mErrorUiMessage = null;
            mWakeLock.acquire(); // we don't want to go to sleep while recording or playing
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

    @Override
    public void onAudioItemDeleteClick(AudioBean data) {
        initData();
        mAapter.setData(mAudioBeanList);
        int type = getIntent().getIntExtra("type", 0);
        //选择音频
        mAapter.setType(type);
        mAapter.setListener(SeleteAudioActivity.this);
        mAapter.setDelListener(SeleteAudioActivity.this);
        mLvAudio.setAdapter(mAapter);
        mAapter.notifyDataSetChanged();
    }
}