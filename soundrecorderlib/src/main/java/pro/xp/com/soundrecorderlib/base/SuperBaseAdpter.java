package pro.xp.com.soundrecorderlib.base;

import android.content.Context;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("all")
public abstract class SuperBaseAdpter<T> extends BaseAdapter {

    protected LayoutInflater inflater;

    public SuperBaseAdpter(Context context) {
        inflater = LayoutInflater.from(context);
        mDatas = new ArrayList<T>();
    }

    private List<T> mDatas;
    private BaseHandler mUIHandler = new BaseHandler(Looper.getMainLooper());

    /*
     * 检查position的数据是否存在
     */
    @Override
    public boolean isEnabled(int position) {
        if (this.mDatas == null || position > this.mDatas.size()) {
            return false;
        }
        return super.isEnabled(position);
    }

    /**
     * 为adapter设置数据，同时刷新
     */
    public void setData(List<T> datas) {
        final List<T> tempDatas;
        if (datas != null) {
            tempDatas = new ArrayList<T>(datas);
        } else {
            tempDatas = null;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            this.mDatas = tempDatas;
            notifyDataSetChangedInternal();
        } else {
            mUIHandler.post(new Runnable() {

                @Override
                public void run() {
                    mDatas = tempDatas;
                    notifyDataSetChangedInternal();
                }

            });
        }
    }

    public List<T> getData() {
        return mDatas;
    }

    private void notifyDataSetChangedInternal() {
        super.notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyDataSetChangedInternal();
        } else {
            mUIHandler.post(new Runnable() {

                @Override
                public void run() {
                    notifyDataSetChangedInternal();
                }

            });
        }
    }

    @Override
    public int getCount() {
        if (mDatas == null) {
            return 0;
        } else {
            return mDatas.size();
        }
    }

    @Override
    public T getItem(int position) {
        if (mDatas == null)
            return null;
        if (mDatas.size() <= position)
            return null;
        return mDatas.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }


    public void appendDatas(List<T> datas) {
        if (this.mDatas != null) {
            this.mDatas.addAll(datas);
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {

            notifyDataSetChangedInternal();
        } else {
            mUIHandler.post(new Runnable() {

                @Override
                public void run() {

                    notifyDataSetChangedInternal();
                }

            });
        }
    }

    public void insertDatas(List<T> datas, int purgeCount) {
        final List<T> tempDatas;
        if (datas != null) {
            tempDatas = new ArrayList<T>(datas);
        } else {
            tempDatas = null;
        }

        if (tempDatas == null)
            return;

        List<T> oldData = null;
        if (this.mDatas != null && this.mDatas.size() > 0)
            oldData = new ArrayList<T>(this.mDatas);

        this.mDatas = tempDatas;

        if (oldData != null && oldData.size() > 0) {
            this.mDatas.addAll(oldData);
        }

        if (this.mDatas.size() > purgeCount) {
            this.mDatas = this.mDatas.subList(0, purgeCount);
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {

            notifyDataSetChangedInternal();
        } else {
            mUIHandler.post(new Runnable() {

                @Override
                public void run() {

                    notifyDataSetChangedInternal();
                }

            });
        }
    }

    public void removeData(T data) {
        mDatas.remove(data);

        if (Looper.myLooper() == Looper.getMainLooper()) {

            notifyDataSetChangedInternal();
        } else {
            mUIHandler.post(new Runnable() {

                @Override
                public void run() {

                    notifyDataSetChangedInternal();
                }

            });
        }
    }

    public void removeDataInAnim(final View v, final T data) {
        if (null != v) {
            final int initialHeight = v.getMeasuredHeight();
            Animation.AnimationListener al = new Animation.AnimationListener() {
                @Override
                public void onAnimationEnd(Animation arg0) {
                    v.getLayoutParams().height = initialHeight;
                    v.requestLayout();
                    v.setVisibility(View.VISIBLE);
                    removeData(data);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationStart(Animation animation) {
                }
            };
            collapse(v, al, initialHeight);
        }
    }

    private void collapse(final View v, Animation.AnimationListener al, final int initialHeight) {
        Animation anim = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    // nothing
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        if (al != null) {
            anim.setAnimationListener(al);
        }
        anim.setDuration(500);
        v.startAnimation(anim);
    }
}