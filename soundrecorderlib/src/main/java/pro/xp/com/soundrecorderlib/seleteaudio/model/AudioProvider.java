package pro.xp.com.soundrecorderlib.seleteaudio.model;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

import pro.xp.com.soundrecorderlib.seleteaudio.manager.AudioSelectManager;

/**
 * AudioProvider:
 * Author: xp
 * Date: 18/3/26 10:38
 * Email: xiexiepro@gmail.com
 * Blog: http://XieXiePro.github.io
 */
@SuppressWarnings("all")
public class AudioProvider implements AbstructProvider {


    private Context context;

    public AudioProvider(Context context) {
        this.context = context;
    }

    @Override
    public List<AudioBean> getList() {
        List<AudioBean> list = getAudioList(AudioSelectManager.MP3_FILE_PATH);
        return list;
    }

    /**
     * 获取指定路径下的音频文件
     *
     * @param filePath
     * @return
     */
    private List<AudioBean> getAudioList(String filePath) {
        List<AudioBean> list = null;
        if (context != null) {
            list = new ArrayList<>();
            Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null, null);
            int i = 0;
            String url;
            int cursorCount = cursor.getCount();
            if (cursorCount > 0) {
                cursor.moveToFirst();
            }

            while (i < cursorCount) {
                url = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                if (url.toLowerCase().indexOf("/recorder") > 0) {
                    String path = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                    String displayName = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME));
                    String mimeType = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE));
                    //最后修改时间
                    String time = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED));
                    //持续时间
                    long duration = cursor
                            .getInt(cursor
                                    .getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                    long size = cursor
                            .getLong(cursor
                                    .getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));
                    //String type, String size, String path, String name, String time, long timeLength, boolean isUpload
                    AudioBean audio = new AudioBean(mimeType, size, path, displayName, time, duration, false, false);
                    list.add(audio);
                }
                i++;
                cursor.moveToNext();
            }

            cursor.close();

        }
        return list;
    }
}