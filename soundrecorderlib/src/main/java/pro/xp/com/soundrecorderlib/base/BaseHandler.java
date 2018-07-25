package pro.xp.com.soundrecorderlib.base;

import android.os.Handler;
import android.os.Looper;

@SuppressWarnings("all")
public class BaseHandler extends Handler {

    private Callback mCallbackEx;

    public BaseHandler() {
        super();
    }

    public BaseHandler(Looper looper) {
        super(looper);
    }

    public BaseHandler(Looper looper, Callback callback) {
        super(looper, callback);
        mCallbackEx = callback;
    }

    public Callback getCallbackEx() {
        return mCallbackEx;
    }

}
