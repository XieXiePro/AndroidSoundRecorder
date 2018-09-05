package pro.xp.com.soundrecorderlib.seleteaudio.manager;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.text.TextUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import pro.xp.com.soundrecorderlib.seleteaudio.model.AudioBean;

@SuppressWarnings("all")
public class AudioSelectManager {
    private static List<AudioBean> audioBeanList = null;
    //共享方式
    public static final int MODE_PROVIDER = 1;
    //遍历文件方式
    public static final int MODE_FOLDER = 2;
    //录音文件路径
    public static final String MP3_FILE_PATH = "/SoundRecorder/";

    /**
     * 通过android 共享的音视频数据库获取音频文件
     *
     * @return
     */
//    public static List<AudioBean> getAudioBeanList() {
//        AudioProvider AudioProvider = new AudioProvider(WGMFLibApplication.getContext());
//        audioBeanList = AudioProvider.getList();
//        return audioBeanList;
//    }

    /**
     * 根据不同额模式获取音频文件
     * 指定路径扫描音频文件
     *
     * @return
     */
    public static List<AudioBean> getAudioBeanList(int mode, String filePath) {
//        if (mode == AudioSelectManager.MODE_PROVIDER) {
//            AudioProvider audioprovider = new AudioProvider(WGMFLibApplication.getContext());
//            audioBeanList = audioprovider.getList();
//        } else if (mode == AudioSelectManager.MODE_FOLDER) {
        getAudioFiles(filePath);
//        }
        return audioBeanList;
    }


    /**
     * 通过遍历文件夹获取音频文件
     *
     * @param filePath
     */
    private static void getAudioFiles(String filePath) {
        audioBeanList = new ArrayList();
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File path = null;
            if (TextUtils.isEmpty(filePath)) {
                // 获得SD卡路径
                path = Environment.getExternalStorageDirectory();
            } else {
                path = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + filePath);
            }
            // File path = new File("/mnt/sdcard/");
            // 读取
            File[] files = path.listFiles();
            getFileName(files);
        }
    }

    /**
     *
     *
     * @param files
     */
    private static void getFileName(File[] files) {

        if (files != null) {
            // 先判断目录是否为空，否则会报空指针
            for (File file : files) {
                if (file.isDirectory()) {
                    getFileName(file.listFiles());
                } else {
                    String fileName = file.getName();
                    if (fileName.endsWith(".mp3")) {
                        AudioBean audioBean = new AudioBean();
                        audioBean.setPath(file.getAbsolutePath());
                        String name = fileName.substring(0, fileName.lastIndexOf(".")).toString();
                        audioBean.setName(name);
                        audioBean.setTime(file.lastModified() + "");
                        audioBean.setTimeLength(file.length());
                        audioBeanList.add(audioBean);
                    }
                }
            }
        }
    }

    /**
     * 获得sd卡的列表 ，包括外部和内部
     *
     * @param context
     * @return
     */
    public static String[] getExtSDCardPath(Context context) {
        StorageManager storageManager = (StorageManager) context.getSystemService(Context
                .STORAGE_SERVICE);
        try {
            Class<?>[] paramClasses = {};
            Method getVolumePathsMethod = StorageManager.class.getMethod("getVolumePaths", paramClasses);
            getVolumePathsMethod.setAccessible(true);
            Object[] params = {};
            Object invoke = getVolumePathsMethod.invoke(storageManager, params);
            return (String[]) invoke;
        } catch (NoSuchMethodException e1) {
            e1.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
