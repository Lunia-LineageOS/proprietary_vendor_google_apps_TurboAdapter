package com.google.android.turboadapter;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import google.hardware.power.extension.pixel.IPowerExt;

import java.util.Arrays;

public class GooglePowerService extends Service {
    private static final String TAG = "GooglePowerService";

    private static final String POWER_HAL_INSTANCE = "android.hardware.power.IPower/default";
    private static final int MAX_HINT_DURATION_MS = 14400000;

    private GooglePowerBinder mBinder;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (mBinder == null) {
            mBinder = new GooglePowerBinder();
        }
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mBinder = null;
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @VisibleForTesting
    boolean isCallerAllowed() {
        return TurboAdapterAccessChecker.isCallerAllowed(
                getApplicationContext(), Binder.getCallingUid());
    }

    @VisibleForTesting
    IBinder getPowerServiceBinder() {
        return ServiceManager.waitForDeclaredService(POWER_HAL_INSTANCE);
    }

    private IPowerExt getPowerExt() throws RemoteException {
        IBinder powerService = getPowerServiceBinder();
        if (powerService == null) {
            throw new RemoteException("Unable to find the Power service.");
        }
        IPowerExt powerExt = IPowerExt.Stub.asInterface(powerService.getExtension());
        if (powerExt == null) {
            throw new RemoteException("Unable to get the Power extension.");
        }
        return powerExt;
    }

    class GooglePowerBinder extends IGooglePower.Stub {
        private GooglePowerBinder() {}

        @Override
        public void setPowerHints(String[] hints, int durationMs) throws RemoteException {
            Log.i(TAG, String.format("setPowerHints(%s, %d)", Arrays.toString(hints), durationMs));
            if (!isCallerAllowed()) {
                throw new SecurityException("Access denied.");
            }
            if (durationMs > MAX_HINT_DURATION_MS || durationMs == 0) {
                durationMs = MAX_HINT_DURATION_MS;
            }
            IPowerExt powerExt = getPowerExt();
            for (String hint : hints) {
                try {
                    powerExt.setBoost(hint, durationMs);
                } catch (RuntimeException e) {
                    Log.e(TAG, "setPowerHints: failed to set hints", e);
                    throw new RemoteException("Unable to set hints.");
                }
            }
        }

        @Override
        public void setAdaptiveCpuConfig(boolean enabled, Bundle config) {
            Log.w(TAG, "setAdaptiveCpuConfig deprecated");
        }
    }
}
