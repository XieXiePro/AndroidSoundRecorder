package pro.xp.com.soundrecorderlib.recorder.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.StatFs;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import pro.xp.com.soundrecorderlib.R;
import pro.xp.com.soundrecorderlib.recorder.Recorder;
import pro.xp.com.soundrecorderlib.seleteaudio.ui.ReanameEdtDialog;
import pro.xp.com.soundrecorderlib.seleteaudio.ui.SeleteAudioActivity;

/**
 * Calculates remaining recording time based on available disk space and
 * optionally a maximum recording file size.
 * <p>
 * The reason why this is not trivial is that the file grows in blocks
 * every few seconds or so, while we want a smooth countdown.
 */
@SuppressWarnings("all")
class RemainingTimeCalculator {

    public static final int UNKNOWN_LIMIT = 0;
    public static final int FILE_SIZE_LIMIT = 1;
    public static final int DISK_SPACE_LIMIT = 2;
    public static final int DISK_TIME_LIMIT = 3;

    // which of the two limits we will hit (or have fit) first
    private int mCurrentLowerLimit = UNKNOWN_LIMIT;

    private File mSDCardDirectory;

    // State for tracking file size of recording.
    private File mRecordingFile;
    private long mMaxBytes;

    // Rate at which the file grows
    private int mBytesPerSecond;

    // time at which number of free blocks last changed
    private long mBlocksChangedTime;
    // number of available blocks at that time
    private long mLastBlocks;

    // time at which the size of the file has last changed
    private long mFileSizeChangedTime;
    // size of the file at that time
    private long mLastFileSize;

    public RemainingTimeCalculator() {
        mSDCardDirectory = Environment.getExternalStorageDirectory();
    }

    /**
     * If called, the calculator will return the minimum of two estimates:
     * how long until we run out of disk space and how long until the file
     * reaches the specified size.
     *
     * @param file     the file to watch
     * @param maxBytes the limit
     */

    public void setFileSizeLimit(File file, long maxBytes) {
        mRecordingFile = file;
        mMaxBytes = maxBytes;
    }

    /**
     * Resets the interpolation.
     */
    public void reset() {
        mCurrentLowerLimit = UNKNOWN_LIMIT;
        mBlocksChangedTime = -1;
        mFileSizeChangedTime = -1;
    }

    /**
     * Returns how long (in seconds) we can continue recording.
     */
    public long timeRemaining() {
        // Calculate how long we can record based on free disk space

        StatFs fs = new StatFs(mSDCardDirectory.getAbsolutePath());
        long blocks = fs.getAvailableBlocks();
        long blockSize = fs.getBlockSize();
        long now = System.currentTimeMillis();

        if (mBlocksChangedTime == -1 || blocks != mLastBlocks) {
            mBlocksChangedTime = now;
            mLastBlocks = blocks;
        }

        /* The calculation below always leaves one free block, since free space
           in the block we're currently writing to is not added. This
           last block might get nibbled when we close and flush the file, but 
           we won't run out of disk. */

        // at mBlocksChangedTime we had this much time
        long result = mLastBlocks * blockSize / mBytesPerSecond;
        // so now we have this much time
        result -= (now - mBlocksChangedTime) / 1000;

        if (mRecordingFile == null) {
            mCurrentLowerLimit = DISK_SPACE_LIMIT;
            return result;
        }

        // If we have a recording file set, we calculate a second estimate
        // based on how long it will take us to reach mMaxBytes.

        mRecordingFile = new File(mRecordingFile.getAbsolutePath());
        long fileSize = mRecordingFile.length();
        if (mFileSizeChangedTime == -1 || fileSize != mLastFileSize) {
            mFileSizeChangedTime = now;
            mLastFileSize = fileSize;
        }

        long result2 = (mMaxBytes - fileSize) / mBytesPerSecond;
        result2 -= (now - mFileSizeChangedTime) / 1000;
        result2 -= 1; // just for safety

        mCurrentLowerLimit = result < result2
                ? DISK_SPACE_LIMIT : FILE_SIZE_LIMIT;

        return Math.min(result, result2);
    }

    /**
     * Indicates which limit we will hit (or have hit) first, by returning one
     * of FILE_SIZE_LIMIT or DISK_SPACE_LIMIT or UNKNOWN_LIMIT. We need this to
     * display the correct message to the user when we hit one of the limits.
     */
    public int currentLowerLimit() {
        return mCurrentLowerLimit;
    }

    /**
     * Is there any point of trying to start recording?
     */
    public boolean diskSpaceAvailable() {
        StatFs fs = new StatFs(mSDCardDirectory.getAbsolutePath());
        // keep one free block
        return fs.getAvailableBlocks() > 1;
    }

    /**
     * Sets the bit rate used in the interpolation.
     *
     * @param bitRate the bit rate to set in bits/sec.
     */
    public void setBitRate(int bitRate) {
        mBytesPerSecond = bitRate / 8;
    }
}

@SuppressWarnings("all")
public class SoundRecorder extends Activity
        implements Button.OnClickListener, Recorder.OnStateChangedListener {
    static final String TAG = "SoundRecorder";
    static final String STATE_FILE_NAME = "soundrecorder.state";
    static final String RECORDER_STATE_KEY = "recorder_state";
    static final String SAMPLE_INTERRUPTED_KEY = "sample_interrupted";
    static final String MAX_FILE_SIZE_KEY = "max_file_size";

    static final String AUDIO_3GPP = "audio/3gpp";
    static final String AUDIO_AMR = "audio/amr";
    static final String AUDIO_ANY = "audio/*";
    static final String ANY_ANY = "*/*";

    static final int BITRATE_AMR = 5900; // bits/sec
    static final int BITRATE_3GPP = 5900;

    WakeLock mWakeLock;
    String mRequestedType = AUDIO_ANY;
    Recorder mRecorder;
    boolean mSampleInterrupted = false;
    String mErrorUiMessage = null; // Some error messages are displayed in the UI,
    // not a dialog. This happens when a recording
    // is interrupted for some reason.

    long mMaxFileSize = -1;        // can be specified in the intent
    RemainingTimeCalculator mRemainingTimeCalculator;

    String mTimerFormat;
    final Handler mHandler = new Handler();
    Runnable mUpdateTimer = new Runnable() {
        @Override
        public void run() {
            updateTimerView();
        }
    };

    TextView mRecordButton;
    TextView mPlayButton;
    TextView mStopButton;
    TextView btnAudioList;

    TextView mStateMessage1;
    TextView mStateMessage2;
    ProgressBar mStateProgressBar;
    TextView mTimerView;

    LinearLayout mExitButtons;
    TextView mAcceptButton;
    TextView mDiscardButton;
    ListView mRecorderLv;

    private BroadcastReceiver mSDCardMountEventReceiver = null;

    public static void launchAct(Context context) {
        context.startActivity(new Intent(context, SoundRecorder.class));
    }

    @Override
    public void onCreate(Bundle icycle) {
        super.onCreate(icycle);
        Intent i = getIntent();
        if (i != null) {
            String s = i.getType();
            if (AUDIO_AMR.equals(s) || AUDIO_3GPP.equals(s) || AUDIO_ANY.equals(s)
                    || ANY_ANY.equals(s)) {
                mRequestedType = s;
            } else if (s != null) {
                // we only support amr and 3gpp formats right now 
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            final String EXTRA_MAX_BYTES = MediaStore.Audio.Media.EXTRA_MAX_BYTES;
            mMaxFileSize = i.getLongExtra(EXTRA_MAX_BYTES, -1);
        }

        if (AUDIO_ANY.equals(mRequestedType) || ANY_ANY.equals(mRequestedType)) {
            mRequestedType = AUDIO_3GPP;
        }
        setContentView(R.layout.activity_recorder);
        //创建录音器和文件
        mRecorder = new Recorder(Recorder.getAudioFile());
        mRecorder.setOnStateChangedListener(this);
        mRemainingTimeCalculator = new RemainingTimeCalculator();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "SoundRecorder");

        initResourceRefs();

        setResult(RESULT_CANCELED);

        registerExternalStorageListener();
        if (icycle != null) {
            Bundle recorderState = icycle.getBundle(RECORDER_STATE_KEY);
            if (recorderState != null) {
                mRecorder.restoreState(recorderState);
                mSampleInterrupted = recorderState.getBoolean(SAMPLE_INTERRUPTED_KEY, false);
                mMaxFileSize = recorderState.getLong(MAX_FILE_SIZE_KEY, -1);
            }
        }
        updateUi();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_recorder);
        initResourceRefs();
        updateUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mRecorder.sampleLength() == 0) {
            return;
        }
        Bundle recorderState = new Bundle();

        mRecorder.saveState(recorderState);
        recorderState.putBoolean(SAMPLE_INTERRUPTED_KEY, mSampleInterrupted);
        recorderState.putLong(MAX_FILE_SIZE_KEY, mMaxFileSize);

        outState.putBundle(RECORDER_STATE_KEY, recorderState);
    }

    /*
     * Whenever the UI is re-created (due f.ex. to orientation change) we have
     * to reinitialize references to the views.
     */
    private void initResourceRefs() {
        mRecordButton = (TextView) findViewById(R.id.btn_record_center_tv);
        mPlayButton = (TextView) findViewById(R.id.btn_record_left_tv);
        mStopButton = (TextView) findViewById(R.id.btn_record_right_tv);

        mStateMessage1 = (TextView) findViewById(R.id.stateMessage1);
        mStateMessage2 = (TextView) findViewById(R.id.stateMessage2);
        mStateProgressBar = (ProgressBar) findViewById(R.id.stateProgressBar);
        mTimerView = (TextView) findViewById(R.id.timerView);

        mExitButtons = (LinearLayout) findViewById(R.id.exitButtons);
        mAcceptButton = (TextView) findViewById(R.id.acceptButton);
        mDiscardButton = (TextView) findViewById(R.id.discardButton);
        mRecorderLv = (ListView) findViewById(R.id.id_lv_audio_take);
        btnAudioList = (TextView) findViewById(R.id.btn_audio_list);

        mRecordButton.setOnClickListener(this);
        mPlayButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mAcceptButton.setOnClickListener(this);
        mDiscardButton.setOnClickListener(this);
        btnAudioList.setOnClickListener(this);
        mTimerFormat = getResources().getString(R.string.timer_format);
    }


    /*
     * Make sure we're not recording music playing in the background, ask
     * the MediaPlaybackService to pause playback.
     */
    private void stopAudioPlayback() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }


    /*
     * Handle the buttons.
     */
    @Override
    public void onClick(View button) {
        if (!button.isEnabled()) {
            return;
        }
        if (button.getId() == R.id.btn_record_center_tv) {
            mRemainingTimeCalculator.reset();
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                mSampleInterrupted = true;
                mErrorUiMessage = getResources().getString(R.string.insert_sd_card);
                updateUi();
            } else if (!mRemainingTimeCalculator.diskSpaceAvailable()) {
                mSampleInterrupted = true;
                mErrorUiMessage = getResources().getString(R.string.storage_is_full);
                updateUi();
            } else {
                stopAudioPlayback();
                if (AUDIO_AMR.equals(mRequestedType)) {
                    mRemainingTimeCalculator.setBitRate(BITRATE_AMR);
                    mRecorder.startRecording(MediaRecorder.OutputFormat.AMR_NB, ".amr", this);
                } else if (AUDIO_3GPP.equals(mRequestedType)) {
                    mRemainingTimeCalculator.setBitRate(BITRATE_3GPP);
//                    mRecorder.startRecording(MediaRecorder.OutputFormat.THREE_GPP, ".3gpp", this);
                    mRecorder.startRecording(MediaRecorder.OutputFormat.THREE_GPP, ".mp3", this);
                } else {
                    throw new IllegalArgumentException("Invalid output file type requested");
                }

                if (mMaxFileSize != -1) {
                    mRemainingTimeCalculator.setFileSizeLimit(
                            mRecorder.sampleFile(), mMaxFileSize);
                }
            }
        } else if (button.getId() == R.id.btn_record_left_tv) {
            mRecorder.startPlayback();
        } else if (button.getId() == R.id.btn_record_right_tv) {
            mRecorder.stop();
        } else if (button.getId() == R.id.acceptButton) {
            showCompleteDialog();
            mRecorder.stop();
        } else if (button.getId() == R.id.discardButton) {
            mRecorder.delete();
            finish();
        } else if (button.getId() == R.id.btn_audio_list) {
            selectAudioActivity();
        }
    }

    /**
     * 录音完成保存对话框
     */
    protected void showCompleteDialog() {
        String filename = mRecorder.sampleFile().getName();
//        Log.e("showCompleteDialog: ",filename.substring(filename.indexOf(".")));
        String[] name = filename.split(".m");
        Log.e("showCompleteDialog: ", name[0]);
        final ReanameEdtDialog.Builder builder = new ReanameEdtDialog.Builder(this);
        builder.setTitle("保存录音？");
        builder.setProNum(name[0]);
        builder.setPositiveButton(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //录音完成，点击取消完成按钮取消保存数据
                dialog.dismiss();
                mRecorder.delete();
            }
        });
        builder.setNegativeButton(new DialogInterface.OnClickListener() {
            @Override

            public void onClick(DialogInterface dialog, int which) {
                //录音完成，点击确定完成按钮保存音频
                dialog.dismiss();
                saveSample();
                reNameLogFile(mRecorder.sampleFile().getAbsolutePath(), builder.getProNum());
                selectAudioActivity();
//                mRecorder.delete();
                finish();
            }

        });
        builder.create().show();
    }

    /**
     * 修改文件名
     *
     * @param oldFilePath
     * @param newFileName
     */
    public static void reNameLogFile(String oldFilePath, String newFileName) {
        File f = new File(oldFilePath);
        String c = f.getParent();
        File mm = new File(c + "/" + newFileName + ".mp3");
        if (f.renameTo(mm)) {
            System.out.println("修改文件名成功!");
        } else {
            System.out.println("修改文件名失败");
        }
    }

    private void selectAudioActivity() {
        Intent intent = new Intent();
        //1:查询，0，选择；
        intent.putExtra("type", 1);
        intent.setClass(this, SeleteAudioActivity.class);
        startActivity(intent);
    }

    /*
     * Handle the "back" hardware key. 
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            switch (mRecorder.state()) {
                case Recorder.IDLE_STATE:
                    if (mRecorder.sampleLength() > 0) {
                        saveSample();
                    }
                    finish();
                    break;
                case Recorder.PLAYING_STATE:
                    mRecorder.stop();
                    saveSample();
                    break;
                case Recorder.RECORDING_STATE:
                    mRecorder.clear();
                    break;
                default:
                    break;
            }
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onStop() {
//        mRecorder.stop();
        super.onStop();
    }

    @Override
    protected void onPause() {
//        mSampleInterrupted = mRecorder.state() == Recorder.RECORDING_STATE;
//        mRecorder.stop();
//        Toast.makeText(this, "进入后台录音模式！", Toast.LENGTH_LONG).show();
        super.onPause();
    }

    /*
     * If we have just recorded a smaple, this adds it to the media data base
     * and sets the result to the sample's URI.
     */
    private void saveSample() {
        if (mRecorder.sampleLength() == 0) {
            return;
        }
        Uri uri = null;
        try {
            uri = this.addToMediaDB(mRecorder.sampleFile());
        } catch (UnsupportedOperationException ex) {  // Database manipulation failure
            return;
        }
        if (uri == null) {
            return;
        }
        setResult(RESULT_OK, new Intent().setData(uri)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    }

    /*
     * Called on destroy to unregister the SD card mount event receiver.
     */
    @Override
    public void onDestroy() {
        if (mSDCardMountEventReceiver != null) {
            unregisterReceiver(mSDCardMountEventReceiver);
            mSDCardMountEventReceiver = null;
        }
        super.onDestroy();
    }

    /*
     * Registers an intent to listen for ACTION_MEDIA_EJECT/ACTION_MEDIA_MOUNTED
     * notifications.
     */
    private void registerExternalStorageListener() {
        if (mSDCardMountEventReceiver == null) {
            mSDCardMountEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        mRecorder.delete();
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        mSampleInterrupted = false;
                        updateUi();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mSDCardMountEventReceiver, iFilter);
        }
    }

    /*
     * A simple utility to do a query into the databases.
     */
    private Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            ContentResolver resolver = getContentResolver();
            if (resolver == null) {
                return null;
            }
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        } catch (UnsupportedOperationException ex) {
            return null;
        }
    }

    /*
     * Add the given audioId to the playlist with the given playlistId; and maintain the
     * play_order in the playlist.
     */
    private void addToPlaylist(ContentResolver resolver, int audioId, long playlistId) {
        String[] cols = new String[]{
                "count(*)"
        };
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        Cursor cur = resolver.query(uri, cols, null, null, null);
        cur.moveToFirst();
        final int base = cur.getInt(0);
        cur.close();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(base + audioId));
        values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId);
        resolver.insert(uri, values);
    }

    /*
     * Obtain the id for the default play list from the audio_playlists table.
     */
    private int getPlaylistId(Resources res) {
        Uri uri = MediaStore.Audio.Playlists.getContentUri("external");
        final String[] ids = new String[]{MediaStore.Audio.Playlists._ID};
        final String where = MediaStore.Audio.Playlists.NAME + "=?";
        final String[] args = new String[]{res.getString(R.string.audio_db_playlist_name)};
        Cursor cursor = query(uri, ids, where, args, null);
        if (cursor == null) {
            Log.v(TAG, "query returns null");
        }
        int id = -1;
        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                id = cursor.getInt(0);
            }
            cursor.close();
        }
        return id;
    }

    /*
     * Create a playlist with the given default playlist name, if no such playlist exists.
     */
    private Uri createPlaylist(Resources res, ContentResolver resolver) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.NAME, res.getString(R.string.audio_db_playlist_name));
        Uri uri = resolver.insert(MediaStore.Audio.Playlists.getContentUri("external"), cv);
        if (uri == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.error_mediadb_new_record)
                    .setPositiveButton(R.string.button_ok, null)
                    .setCancelable(false)
                    .show();
        }
        return uri;
    }

    /*
     * Adds file and returns content uri.
     */
    private Uri addToMediaDB(File file) {
        Resources res = getResources();
        ContentValues cv = new ContentValues();
        long current = System.currentTimeMillis();
        long modDate = file.lastModified();
        long sampleLengthMillis = mRecorder.sampleLength() * 1000L;
        //如果未重命名，重命名文件
        Date date = new Date(current);
        SimpleDateFormat formatter = new SimpleDateFormat(res.getString(R.string.audio_db_title_format));
        String title = formatter.format(date);

        // Lets label the recorded audio file as NON-MUSIC so that the file
        // won't be displayed automatically, except for in the playlist.
        cv.put(MediaStore.Audio.Media.IS_MUSIC, "0");

        cv.put(MediaStore.Audio.Media.TITLE, title);
        cv.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
        cv.put(MediaStore.Audio.Media.DATE_ADDED, (int) (current / 1000));
        cv.put(MediaStore.Audio.Media.DATE_MODIFIED, (int) (modDate / 1000));
        cv.put(MediaStore.Audio.Media.DURATION, sampleLengthMillis);
        cv.put(MediaStore.Audio.Media.MIME_TYPE, mRequestedType);
        cv.put(MediaStore.Audio.Media.ARTIST, res.getString(R.string.audio_db_artist_name));
        cv.put(MediaStore.Audio.Media.ALBUM, res.getString(R.string.audio_db_album_name));
        Log.d(TAG, "Inserting audio record: " + cv.toString());
        ContentResolver resolver = getContentResolver();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Log.d(TAG, "ContentURI: " + base);
        Uri result = resolver.insert(base, cv);
        if (result == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.error_mediadb_new_record)
                    .setPositiveButton(R.string.button_ok, null)
                    .setCancelable(false)
                    .show();
            return null;
        }
        if (getPlaylistId(res) == -1) {
            createPlaylist(res, resolver);
        }
        int audioId = Integer.valueOf(result.getLastPathSegment());
        addToPlaylist(resolver, audioId, getPlaylistId(res));

        // Notify those applications such as Music listening to the 
        // scanner events that a recorded audio file just created. 
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result));
        return result;
    }

    /**
     * Update the big MM:SS timer. If we are in playback, also update the
     * progress bar.
     */
    private void updateTimerView() {
        Resources res = getResources();
        int state = mRecorder.state();

        boolean ongoing = state == Recorder.RECORDING_STATE || state == Recorder.PLAYING_STATE;

        long time = ongoing ? mRecorder.progress() : mRecorder.sampleLength();
        String timeStr = String.format(mTimerFormat, time / 60, time % 60);
        mTimerView.setText(timeStr);

        if (time >= 600) {
            //time=1是1秒，十分钟自动停止
            mSampleInterrupted = true;
            mRecorder.stop();
            return;
        }

        if (state == Recorder.PLAYING_STATE) {
            mStateProgressBar.setProgress((int) (100 * time / mRecorder.sampleLength()));
        } else if (state == Recorder.RECORDING_STATE) {
            updateTimeRemaining();
        }

        if (ongoing) {
            mHandler.postDelayed(mUpdateTimer, 1000);
        }
    }

    /*
     * Called when we're in recording state. Find out how much longer we can 
     * go on recording. If it's under 5 minutes, we display a count-down in 
     * the UI. If we've run out of time, stop the recording. 
     */
    private void updateTimeRemaining() {
        long t = mRemainingTimeCalculator.timeRemaining();

        if (t <= 0) {
            mSampleInterrupted = true;

            int limit = mRemainingTimeCalculator.currentLowerLimit();
            switch (limit) {
                case RemainingTimeCalculator.DISK_SPACE_LIMIT:
                    mErrorUiMessage
                            = getResources().getString(R.string.storage_is_full);
                    break;
                case RemainingTimeCalculator.FILE_SIZE_LIMIT:
                    mErrorUiMessage
                            = getResources().getString(R.string.max_length_reached);
                    break;
                default:
                    mErrorUiMessage = null;
                    break;
            }

            mRecorder.stop();
            return;
        }
        Resources res = getResources();
        String timeStr = "";

        if (t < 60) {
            timeStr = String.format(res.getString(R.string.sec_available), t);
        } else if (t < 540) {
            timeStr = String.format(res.getString(R.string.min_available), t / 60 + 1);
        }
        mStateMessage1.setText(timeStr);
    }

    /**
     * Shows/hides the appropriate child views for the new state.
     */
    private void updateUi() {
        Resources res = getResources();
        switch (mRecorder.state()) {
            case Recorder.IDLE_STATE:
                if (mRecorder.sampleLength() == 0) {
                    mRecordButton.setEnabled(true);
                    mRecordButton.setFocusable(true);
                    mPlayButton.setEnabled(false);
                    mPlayButton.setFocusable(false);
                    mStopButton.setEnabled(false);
                    mStopButton.setFocusable(false);
                    mRecordButton.requestFocus();

                    mStateMessage1.setVisibility(View.INVISIBLE);
                    mStateMessage2.setVisibility(View.INVISIBLE);

                    mExitButtons.setVisibility(View.INVISIBLE);

                    mStateProgressBar.setVisibility(View.INVISIBLE);

                    setTitle(res.getString(R.string.record_your_message));

                    //录音就绪状态：显示中间录音按钮，其余按钮不显示
                    mRecordButton.setVisibility(View.VISIBLE);
                    mPlayButton.setVisibility(View.GONE);
                    mStopButton.setVisibility(View.GONE);
                    mAcceptButton.setVisibility(View.GONE);
                } else {
                    mRecordButton.setEnabled(true);
                    mRecordButton.setFocusable(true);
                    mPlayButton.setEnabled(true);
                    mPlayButton.setFocusable(true);

                    mAcceptButton.setEnabled(true);
                    mAcceptButton.setFocusable(true);

                    mStopButton.setEnabled(false);
                    mStopButton.setFocusable(false);

                    mStateMessage1.setVisibility(View.INVISIBLE);
                    mStateMessage2.setVisibility(View.INVISIBLE);

                    mStateProgressBar.setVisibility(View.INVISIBLE);

                    setTitle(res.getString(R.string.message_recorded));

                    //录音播放就绪状态：显示中间录音按钮，左边显示播放按钮，右边显示完成按钮
                    mRecordButton.setVisibility(View.VISIBLE);
                    mPlayButton.setVisibility(View.VISIBLE);
                    mStopButton.setVisibility(View.GONE);
                    mAcceptButton.setVisibility(View.VISIBLE);
                    mRecordButton.setCompoundDrawables(null, setCompoundDrawable(R.mipmap.btn_record), null, null);
                    mAcceptButton.setCompoundDrawables(null, setCompoundDrawable(R.mipmap.btn_record_complete), null, null);
                }

                if (mSampleInterrupted) {
                    mStateMessage2.setVisibility(View.VISIBLE);
                    mStateMessage2.setText(res.getString(R.string.recording_stopped));
                }

                if (mErrorUiMessage != null) {
                    mStateMessage1.setText(mErrorUiMessage);
                    mStateMessage1.setVisibility(View.VISIBLE);
                }

                break;
            case Recorder.RECORDING_STATE:
                mRecordButton.setEnabled(false);
                mRecordButton.setFocusable(false);
                mPlayButton.setEnabled(false);
                mPlayButton.setFocusable(false);
                mStopButton.setEnabled(true);
                mStopButton.setFocusable(true);

                mStateMessage1.setVisibility(View.VISIBLE);
                mStateMessage2.setVisibility(View.VISIBLE);
                mStateMessage2.setText(res.getString(R.string.recording));

                mExitButtons.setVisibility(View.INVISIBLE);

                mStateProgressBar.setVisibility(View.INVISIBLE);

                setTitle(res.getString(R.string.record_your_message));

                //正在录音状态：中间显示暂停按钮，动画
                mStopButton.setVisibility(View.VISIBLE);
                mRecordButton.setVisibility(View.GONE);
                mPlayButton.setVisibility(View.GONE);
                mAcceptButton.setVisibility(View.GONE);
                break;

            case Recorder.PLAYING_STATE:
                mRecordButton.setEnabled(false);
                mRecordButton.setFocusable(false);
                mPlayButton.setEnabled(false);
                mPlayButton.setFocusable(false);
                mStopButton.setEnabled(false);
                mStopButton.setFocusable(false);
                mAcceptButton.setEnabled(false);
                mAcceptButton.setFocusable(false);

                mStateMessage1.setVisibility(View.INVISIBLE);
                mStateMessage2.setVisibility(View.INVISIBLE);

                mStateProgressBar.setVisibility(View.VISIBLE);

                setTitle(res.getString(R.string.review_message));

                //播放录音状态：中间显示灰色录音按钮，左边显示播放按钮，右边显示完成灰色按钮
                mRecordButton.setVisibility(View.VISIBLE);
                mPlayButton.setVisibility(View.VISIBLE);
                mStopButton.setVisibility(View.GONE);
                mAcceptButton.setVisibility(View.VISIBLE);
                mRecordButton.setCompoundDrawables(null, setCompoundDrawable(R.mipmap.btn_record_pause_gray), null, null);
                mAcceptButton.setCompoundDrawables(null, setCompoundDrawable(R.mipmap.btn_record_complete_gray), null, null);
                break;
            default:
                break;
        }

        updateTimerView();
    }

    /**
     * 设置Top显示图标
     *
     * @param id 图标资源id
     * @return 图标资源
     */
    private Drawable setCompoundDrawable(int id) {
        Drawable drawable = ContextCompat.getDrawable(this, id);
        drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
        return drawable;
    }

    /*
     * Called when Recorder changed it's state.
     */
    @Override
    public void onStateChanged(int state) {
        if (state == Recorder.PLAYING_STATE || state == Recorder.RECORDING_STATE) {
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

    /*
     * Called when MediaPlayer encounters an error.
     */
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