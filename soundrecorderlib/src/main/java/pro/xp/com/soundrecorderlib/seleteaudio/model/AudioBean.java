package pro.xp.com.soundrecorderlib.seleteaudio.model;

import android.os.Parcel;
import android.os.Parcelable;

import pro.xp.com.soundrecorderlib.recorder.Recorder;

@SuppressWarnings("all")
public class AudioBean  implements Parcelable {
    private String type;
    private  long size;
    private String path;
    private String name;
    private String time;
    private  long timeLength;
    private  boolean isUpload;
    private  boolean isShowProcess;

    public Recorder recorder;

    public AudioBean() {
    }

    public AudioBean(String type, long size, String path, String name, String time, long timeLength, boolean isUpload, boolean isShowProcess) {
        this.type = type;
        this.size = size;
        this.path = path;
        this.name = name;
        this.time = time;
        this.timeLength = timeLength;
        this.isUpload = isUpload;
        this.isShowProcess = isShowProcess;
    }

    protected AudioBean(Parcel in) {
        type = in.readString();
        size = in.readLong();
        path = in.readString();
        name = in.readString();
        time = in.readString();
        timeLength = in.readLong();
        isUpload = in.readByte() != 0;
        isShowProcess = in.readByte() != 0;
    }

    public static final Creator<AudioBean> CREATOR = new Creator<AudioBean>() {
        @Override
        public AudioBean createFromParcel(Parcel in) {
            return new AudioBean(in);
        }

        @Override
        public AudioBean[] newArray(int size) {
            return new AudioBean[size];
        }
    };

    public String getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getTime() {
        return time;
    }

    public long getTimeLength() {
        return timeLength;
    }

    public boolean isUpload() {
        return isUpload;
    }

    public boolean isShowProcess() {
        return isShowProcess;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void setTimeLength(long timeLength) {
        this.timeLength = timeLength;
    }

    public void setUpload(boolean upload) {
        isUpload = upload;
    }

    public void setShowProcess(boolean showProcess) {
        isShowProcess = showProcess;
    }

    @Override
    public String toString() {
        return "AudioBean{" +
                "type='" + type + '\'' +
                ", size=" + size +
                ", path='" + path + '\'' +
                ", name='" + name + '\'' +
                ", time='" + time + '\'' +
                ", timeLength=" + timeLength +
                ", isUpload=" + isUpload +
                ", isShowProcess=" + isShowProcess +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(type);
        parcel.writeLong(size);
        parcel.writeString(path);
        parcel.writeString(name);
        parcel.writeString(time);
        parcel.writeLong(timeLength);
        parcel.writeByte((byte) (isUpload ? 1 : 0));
        parcel.writeByte((byte) (isShowProcess ? 1 : 0));
    }
}
