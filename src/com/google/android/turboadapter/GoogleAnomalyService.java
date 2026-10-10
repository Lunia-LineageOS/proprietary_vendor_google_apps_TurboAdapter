package com.google.android.turboadapter;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

public class GoogleAnomalyService extends Service {
    private static final String TAG = "GoogleAnomalyService";

    @VisibleForTesting static final int VERSION = 1;

    private GoogleAnomalyServiceBinder mBinder;
    @VisibleForTesting GooglePowerDataCollector mPowerDataCollector;
    @VisibleForTesting GoogleReactionDriver mReactionDriver;

    @Override
    public void onCreate() {
        super.onCreate();
        mPowerDataCollector = new GooglePowerDataCollector(this);
        mReactionDriver = new GoogleReactionDriver();
        Log.i(TAG, "Created...");
        Log.i(TAG, "Version: " + VERSION);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Binded...");
        }
        if (mBinder == null) {
            mBinder = new GoogleAnomalyServiceBinder();
        }
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Unbinded...");
        }
        mBinder = null;
        return false;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroyed...");
        super.onDestroy();
    }

    @VisibleForTesting
    boolean isCallerAllowed() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "isCallerAllowed");
        }
        return TurboAdapterAccessChecker.isCallerAllowed(
                getApplicationContext(), Binder.getCallingUid());
    }

    class GoogleAnomalyServiceBinder extends IGoogleAnomalyService.Stub {
        private GoogleAnomalyServiceBinder() {}

        @Override
        public int getTurboAdapterVersion() {
            return VERSION;
        }

        @Override
        public byte[] collectPowerData(int[] providerIds) {
            if (!isCallerAllowed()) {
                throw new SecurityException("Access denied.");
            }
            return mPowerDataCollector.collectPowerData(providerIds);
        }

        @Override
        public boolean triggerReaction(String reaction, String parameter) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(
                        TAG,
                        "Triggering reaction "
                                + reaction
                                + " with the parameter "
                                + parameter
                                + ".");
            }
            if (!isCallerAllowed()) {
                throw new SecurityException("Access denied.");
            }
            return mReactionDriver.triggerReaction(reaction, parameter);
        }
    }
}
