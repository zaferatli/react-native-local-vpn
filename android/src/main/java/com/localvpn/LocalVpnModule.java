package com.localvpn;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.app.Activity;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.BaseActivityEventListener;
import static android.app.Activity.RESULT_OK;

import java.io.IOException;

public class LocalVpnModule extends ReactContextBaseJavaModule {
    public static final String NAME = "LocalVpn";
    private static ReactApplicationContext _reactContext;
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything
    private static final String TAG = "LocalVpnModule";
    private BroadcastReceiver vpnStateReceiver;

    public LocalVpnModule(ReactApplicationContext reactContext) {
        super(reactContext);
        _reactContext = reactContext;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void prepareLocalVPN(Promise promise) {
        try {
            Intent intent = VpnService.prepare(getReactApplicationContext());
            if (intent != null) {
                _reactContext.addActivityEventListener(new BaseActivityEventListener() {
                    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
                        if(requestCode == 0 && resultCode == RESULT_OK){
                            promise.resolve(true);
                        } else {
                            promise.reject("PrepareError", "Failed to prepare");
                        }
                    }
                });
                getCurrentActivity().startActivityForResult(intent, 0);
            } else {
                getCurrentActivity().registerReceiver(vpnStateReceiver,
                new IntentFilter(LocalVpnService.BROADCAST_VPN_STATE));
                promise.resolve(true);
            }
        } catch (Exception e) {
            promise.reject("VPN_CONNECTION_ERROR", "Error connecting to VPN", e);
        }
    }

  @ReactMethod
  public void connectLocalVPN(Promise promise) {
    Intent intent = VpnService.prepare(getReactApplicationContext());
    if (intent != null) {
        promise.reject("PrepareError", "Not prepared");
        return;
    }
    startVpnService();
    promise.resolve(true);
  }

  @ReactMethod
  public void disconnectLocalVPN(Promise promise) {
      try {
          Intent intent = new Intent(getReactApplicationContext(), LocalVpnService.class);
          intent.setAction(Constants.ACTION_FOREGROUND_SERVICE_STOP);
          getReactApplicationContext().startService(intent);
          Log.d("localvpnModule", "VPN Service stopped: ");
              promise.resolve(true);
         
      } catch (Exception e) {
          promise.reject("VPN_DISCONNECTION_ERROR", "Error disconnecting from VPN", e);
      }
  }

    private void startVpnService() {
        Intent intent = new Intent(getReactApplicationContext(), LocalVpnService.class);
        getReactApplicationContext().startService(intent);
    }
}
