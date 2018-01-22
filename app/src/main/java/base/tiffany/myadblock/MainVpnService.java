package base.tiffany.myadblock;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;


public class MainVpnService extends VpnService implements Handler.Callback {

    private static final int VPN_MSG_STATUS_UPDATE = 0;

    public static final int VPN_STATUS_STARTING = 0;
    public static final int VPN_STATUS_RUNNING = 1;
    public static final int VPN_STATUS_STOPPED = 2;
    public static final int VPN_STATUS_STOPPING = 3;

    public static final int START = 4;
    public static final int STOP = 5;

    public static int vpnStatus = VPN_STATUS_STOPPED;

    private static class MyHandler extends Handler {
        private final WeakReference<Callback> callback;

        public MyHandler(Handler.Callback callback) {
            this.callback = new WeakReference<>(callback);
        }

        @Override
        public void handleMessage(Message msg) {
            Handler.Callback callback = this.callback.get();
            if (callback != null) {
                callback.handleMessage(msg);
            }
            super.handleMessage(msg);
        }
    }

    private final Handler handler = new MyHandler(this);

    private VpnThread vpnThread = new VpnThread(this, new VpnThread.Notify() {
        @Override
        public void run(int value) {
            handler.sendMessage(handler.obtainMessage(VPN_MSG_STATUS_UPDATE, value, 0));
        }
    });

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(this.getClass().getSimpleName(), "onStartCommand" + intent);
        switch (intent == null ? START : intent.getIntExtra("STATUS", START)) {
            case START:
                startVpn();
                break;
            case STOP:
                stopVpn();
                break;
        }
        return Service.START_STICKY;
    }

    private void startVpn() {
        vpnThread.stopThread();
        vpnThread.startThread();
        vpnStatus = VPN_STATUS_STARTING;
    }

    private void stopVpn() {
        if (vpnThread != null) {
            stopVpnThread();
        }
        vpnThread = null;
        vpnStatus = VPN_STATUS_STOPPED;
        stopSelf();
    }

    private void stopVpnThread() {
        vpnThread.stopThread();
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message == null) {
            return true;
        }
        switch (message.what) {
            case VPN_MSG_STATUS_UPDATE:
                vpnStatus = message.arg1;
                break;
        }
        return true;
    }
}

