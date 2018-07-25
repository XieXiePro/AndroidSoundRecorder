package pro.xp.com.soundrecorder;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import pro.xp.com.soundrecorderlib.base.SuperBaseAdpter;
import pro.xp.com.soundrecorderlib.recorder.Recorder;
import pro.xp.com.soundrecorderlib.seleteaudio.model.AudioBean;
import pro.xp.com.soundrecorderlib.utils.TimeDataUtils;


public class ReportAudioAdapter extends SuperBaseAdpter<AudioBean> {
    /**
     * 1:查询，0，选择；
     */
    private int type;

    private int selected = -1;

    private int selectedItem = -1;

    private String mTimerFormat;
    private AudioItemClickListener mListener;
    private final Handler mHandler = new Handler();

    private void setSelected(int selected) {
        this.selected = selected;
    }

    private void setSelectedItem(int selectedItem) {
        this.selectedItem = selectedItem;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    ReportAudioAdapter(Context context) {
        super(context);
        mTimerFormat = context.getResources().getString(R.string.timer_format);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final AudioViewHolder mHolder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.audio_list_item, parent, false);
            mHolder = new AudioViewHolder();

            mHolder.llAudioItem = (LinearLayout) convertView.findViewById(R.id.id_ll_audio_item);

            mHolder.tvItemName = (TextView) convertView.findViewById(R.id.id_audio_name);
            mHolder.tvItemTime = (TextView) convertView.findViewById(R.id.id_audio_time);
            mHolder.tvItemIimeLength = (TextView) convertView.findViewById(R.id.id_audio_time_length);
            mHolder.cbItemIsUpload = (RadioButton) convertView.findViewById(R.id.id_audio_is_upload);

            mHolder.llProcess = (LinearLayout) convertView.findViewById(R.id.ll_process);
            mHolder.tvSelectPlay = (TextView) convertView.findViewById(R.id.tv_select_play);
            mHolder.tvSelectStopPlay = (TextView) convertView.findViewById(R.id.tv_select_stop_play);
            mHolder.tvSelectTimeProcess = (TextView) convertView.findViewById(R.id.tv_select_time_process);
            mHolder.stateProgressBar = (ProgressBar) convertView.findViewById(R.id.stateProgressBar);
            mHolder.btnSelectTimeTotal = (TextView) convertView.findViewById(R.id.btn_select_time_total);
            mHolder.tvAudioDelete = (TextView) convertView.findViewById(R.id.id_audio_delete);
            convertView.setTag(mHolder);
        } else {
            mHolder = (AudioViewHolder) convertView.getTag();
        }

        final AudioBean item = getItem(position);

        //查询模式
        mHolder.cbItemIsUpload.setVisibility(View.GONE);
        mHolder.cbItemIsUpload.setVisibility(View.GONE);

        if (item.recorder != null) {
            updateTimerView(mHolder, item);
        }

        String fileTimeLen = TimeDataUtils.converLongTimeToStr(item.getTimeLength());
        String data = TimeDataUtils.timeStamp2Date(item.getTime(), null);
        mHolder.tvItemName.setText(item.getName());
        mHolder.tvItemTime.setText(data);
        mHolder.tvItemIimeLength.setText(fileTimeLen);

        mHolder.llAudioItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    mListener.onAudioItemClick(item);
                    setSelectedItem(position);
                    notifyDataSetChanged();
                }
            }
        });
        mHolder.cbItemIsUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelected(position);
                notifyDataSetChanged();
            }
        });
        if (selectedItem == position && item.isShowProcess()) {
            mHolder.llProcess.setVisibility(View.VISIBLE);
        } else {
            mHolder.llProcess.setVisibility(View.GONE);
        }
        if (selected == position) {
            // 设置为选中
            mHolder.cbItemIsUpload.setBackgroundResource(R.mipmap.circle_orange);
        } else {
            // 设置为未选中
            mHolder.cbItemIsUpload.setBackgroundResource(R.mipmap.circle_gray);
        }

        mHolder.tvSelectPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playOrStopAudio(item);
            }
        });

        mHolder.tvSelectStopPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playOrStopAudio(item);
            }
        });
        return convertView;
    }

    /**
     * 播放或者停止逻辑
     *
     * @param item 条目布局数据
     */
    private void playOrStopAudio(AudioBean item) {
        int state = item.recorder.state();
        switch (state) {
            case Recorder.IDLE_STATE:
                item.recorder.startPlayback(item.getPath());
                break;
            case Recorder.PLAYING_STATE:
                item.recorder.stop();
                break;
            default:
                break;
        }


    }

    /**
     * Update the big MM:SS timer. If we are in playback, also update the
     * progress bar.
     */
    private void updateTimerView(final AudioViewHolder mHolder, final AudioBean item) {
        int state = item.recorder.state();
        switch (item.recorder.state()) {
            case Recorder.IDLE_STATE:
                mHolder.tvSelectPlay.setVisibility(View.VISIBLE);
                mHolder.tvSelectStopPlay.setVisibility(View.GONE);
                break;

            case Recorder.PLAYING_STATE:
                mHolder.tvSelectPlay.setVisibility(View.GONE);
                mHolder.tvSelectStopPlay.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }


        boolean ongoing = state == Recorder.PLAYING_STATE;

        //文件时长 /4得到mp3文件时长
        long timeLen = item.getTimeLength() / 1000 / 4;
        long time = ongoing ? item.recorder.progress() : timeLen;
        String timeStr = String.format(mTimerFormat, time / 60, time % 60);
        mHolder.tvSelectTimeProcess.setText(timeStr);

        if (state == Recorder.PLAYING_STATE) {
            if (timeLen != 0) {
                mHolder.stateProgressBar.setProgress((int) (100 * time / timeLen));
            }
        } else {
            //非播放时，进度条置0
            mHolder.stateProgressBar.setProgress(0);
        }

        if (ongoing) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateTimerView(mHolder, item);
                }
            }, 1000);
        }
    }


    class AudioViewHolder {
        LinearLayout llAudioItem;
        TextView tvItemName;
        TextView tvItemTime;
        TextView tvItemIimeLength;
        RadioButton cbItemIsUpload;
        TextView tvAudioDelete;

        LinearLayout llProcess;
        TextView tvSelectPlay;
        TextView tvSelectStopPlay;
        TextView tvSelectTimeProcess;
        ProgressBar stateProgressBar;
        TextView btnSelectTimeTotal;
    }

    public void setListener(AudioItemClickListener listener) {
        mListener = listener;
    }

    public interface AudioItemClickListener {
        void onAudioItemClick(AudioBean data);
    }
}