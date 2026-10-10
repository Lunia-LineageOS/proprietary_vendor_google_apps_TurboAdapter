package com.google.android.turboadapter;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.android.systemui.googlebattery.AdaptiveChargingManager;

import vendor.google.google_battery.ChargingStage;
import vendor.google.google_battery.IGoogleBattery;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Proxies the Google Battery HAL to Device Health Services. */
public class GoogleBatteryService extends Service {
    private static final String TAG = "GoogleBatteryService";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String GOOGLE_BATTERY_HAL_INSTANCE =
            "vendor.google.google_battery.IGoogleBattery/default";
    private static final String VERSION = "7";
    private static final String KEY_FEATURE_PROPERTY = "feature_property";
    private static final String KEY_CHARGING_STAGE = "charging_stage";
    private static final String KEY_DEADLINE_SECONDS = "deadline_seconds";
    private static final String HAL_UNAVAILABLE = "Google Battery connection no longer available.";

    private GoogleBatteryProxy mBinder;
    @VisibleForTesting volatile IGoogleBattery mGoogleBattery;
    private final Object mLock = new Object();
    @VisibleForTesting ExecutorService mSingleThreadExecutor = Executors.newSingleThreadExecutor();

    private final IBinder.DeathRecipient mDeathRecipient =
            () -> {
                if (DEBUG) {
                    Log.d(TAG, "binderDied");
                }
                synchronized (mLock) {
                    mGoogleBattery = null;
                }
            };

    @VisibleForTesting
    IBinder bindToGoogleBatteryProxy() {
        if (mBinder == null) {
            mBinder = new GoogleBatteryProxy();
        }
        return mBinder;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "onBind");
        }
        initHalInterface();
        return bindToGoogleBatteryProxy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "onUnbind");
        }
        destroyHalInterface();
        mBinder = null;
        return false;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "onDestroy");
        }
        super.onDestroy();
        mSingleThreadExecutor.shutdown();
    }

    private void runOnBackgroundThread(Runnable runnable) {
        mSingleThreadExecutor.execute(runnable);
    }

    @VisibleForTesting
    boolean allowToAccessService() {
        if (DEBUG) {
            Log.d(TAG, "allowToAccessService");
        }
        return TurboAdapterAccessChecker.isCallerAllowed(
                getApplicationContext(), Binder.getCallingUid());
    }

    /** Returns the HAL if the caller may use it, or null. */
    @VisibleForTesting
    IGoogleBattery getIGoogleBattery() {
        if (!allowToAccessService()) {
            return null;
        }
        synchronized (mLock) {
            return mGoogleBattery;
        }
    }

    private IGoogleBattery getGoogleBatteryHAL() {
        try {
            IBinder binder =
                    Binder.allowBlocking(
                            ServiceManager.waitForDeclaredService(GOOGLE_BATTERY_HAL_INSTANCE));
            if (binder == null) {
                return null;
            }
            IGoogleBattery googleBattery = IGoogleBattery.Stub.asInterface(binder);
            if (googleBattery != null) {
                binder.linkToDeath(mDeathRecipient, 0);
            }
            return googleBattery;
        } catch (RemoteException | NoSuchElementException e) {
            Log.e(TAG, "Failed to get Google Battery HAL: ", e);
            return null;
        }
    }

    private void initHalInterface() {
        if (DEBUG) {
            Log.d(TAG, "initHalInterface");
        }
        synchronized (mLock) {
            if (mGoogleBattery == null) {
                mGoogleBattery = getGoogleBatteryHAL();
            }
        }
    }

    private void destroyHalInterface() {
        if (DEBUG) {
            Log.d(TAG, "destroyHalInterface");
        }
        synchronized (mLock) {
            IGoogleBattery googleBattery = mGoogleBattery;
            mGoogleBattery = null;
            if (googleBattery == null) {
                return;
            }
            IBinder binder = googleBattery.asBinder();
            if (binder != null) {
                try {
                    binder.unlinkToDeath(mDeathRecipient, 0);
                } catch (NoSuchElementException e) {
                    Log.w(TAG, "unlinkToDeath called on unlinked binder", e);
                }
            }
        }
    }

    class GoogleBatteryProxy extends IGoogleBatteryService.Stub {
        GoogleBatteryProxy() {}

        @Override
        public boolean isAdaptiveChargingEnabled() {
            if (DEBUG) {
                Log.d(TAG, "isAdaptiveChargingEnabled");
            }
            if (!allowToAccessService()) {
                return false;
            }
            long identity = Binder.clearCallingIdentity();
            try {
                AdaptiveChargingManager manager = new AdaptiveChargingManager(getBaseContext());
                return manager.isAvailable() && manager.isEnabled();
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public boolean setChargingDeadline(int seconds) {
            if (DEBUG) {
                Log.d(TAG, "setChargingDeadline");
            }
            IGoogleBattery googleBattery = getIGoogleBattery();
            if (googleBattery == null) {
                Log.e(TAG, HAL_UNAVAILABLE);
                return false;
            }
            try {
                googleBattery.setChargingDeadline(seconds);
                return true;
            } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
                Log.e(TAG, "Setting charging deadline failed", e);
                return false;
            }
        }

        @Override
        public void getChargingStageAndDeadline(ResultReceiver receiver) {
            if (DEBUG) {
                Log.d(TAG, "getChargingStageAndDeadline");
            }
            if (allowToAccessService() && receiver != null) {
                runOnBackgroundThread(new GetChargingStageAndDeadlineRunnable(receiver));
            }
        }

        @Override
        public String getVersion() {
            return VERSION;
        }

        @Override
        public boolean setProperty(int feature, int property, int value) {
            if (DEBUG) {
                Log.d(
                        TAG,
                        "setProperty, Feature : "
                                + feature
                                + ", Property : "
                                + property
                                + ", Value : "
                                + value);
            }
            IGoogleBattery googleBattery = getIGoogleBattery();
            if (googleBattery == null) {
                Log.e(TAG, HAL_UNAVAILABLE);
                return false;
            }
            try {
                googleBattery.setProperty(feature, property, value);
                return true;
            } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
                Log.e(TAG, "setting battery property failed", e);
                return false;
            }
        }

        @Override
        public void getProperty(int feature, int property, ResultReceiver receiver) {
            if (DEBUG) {
                Log.d(TAG, "getProperty");
            }
            if (allowToAccessService() && receiver != null) {
                runOnBackgroundThread(new GetBatteryPropertyRunnable(feature, property, receiver));
            }
        }

        @Override
        public boolean setStringProperty(int feature, int property, String value) {
            if (DEBUG) {
                Log.d(TAG, "setStringProperty");
            }
            IGoogleBattery googleBattery = getIGoogleBattery();
            if (googleBattery == null) {
                Log.e(TAG, HAL_UNAVAILABLE);
                return false;
            }
            try {
                googleBattery.setStringProperty(feature, property, value);
                return true;
            } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
                Log.e(TAG, "setting battery property failed", e);
                return false;
            }
        }

        @Override
        public String getStringProperty(int feature, int property) {
            if (DEBUG) {
                Log.d(TAG, "getStringProperty, Feature : " + feature + ", Property : " + property);
            }
            IGoogleBattery googleBattery = getIGoogleBattery();
            if (googleBattery == null) {
                Log.e(TAG, HAL_UNAVAILABLE);
                return null;
            }
            try {
                return googleBattery.getStringProperty(feature, property);
            } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
                Log.e(TAG, "getting battery property failed", e);
                return null;
            }
        }

        @Override
        public List<String> getRelaxationData(long arg) {
            if (DEBUG) {
                Log.d(TAG, "getRelaxationData");
            }
            IGoogleBattery googleBattery = getIGoogleBattery();
            if (googleBattery == null) {
                Log.e(TAG, HAL_UNAVAILABLE);
                return null;
            }
            try {
                // Our IGoogleBattery declares String[] where stock's has List<String>; both are
                // the same string array on the wire.
                String[] data = googleBattery.getRelaxationData(arg);
                return data == null ? null : Arrays.asList(data);
            } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
                Log.e(TAG, "getting relaxation data failed", e);
                return null;
            }
        }
    }

    final class GetBatteryPropertyRunnable implements Runnable {
        final int mFeature;
        final int mProperty;
        final ResultReceiver mResultReceiver;

        GetBatteryPropertyRunnable(int feature, int property, ResultReceiver receiver) {
            mFeature = feature;
            mProperty = property;
            mResultReceiver = receiver;
        }

        @Override
        public void run() {
            IGoogleBattery googleBattery;
            synchronized (mLock) {
                googleBattery = mGoogleBattery;
            }
            if (googleBattery == null) {
                Log.e(TAG, HAL_UNAVAILABLE);
                return;
            }
            try {
                int value = googleBattery.getProperty(mFeature, mProperty);
                if (DEBUG) {
                    Log.d(TAG, "getBatteryPropertyCallback value: " + value);
                }
                Bundle result = new Bundle();
                result.putInt(KEY_FEATURE_PROPERTY, value);
                mResultReceiver.send(0, result);
            } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
                Log.e(TAG, "getProperty failed", e);
            }
        }
    }

    final class GetChargingStageAndDeadlineRunnable implements Runnable {
        final ResultReceiver mResultReceiver;

        GetChargingStageAndDeadlineRunnable(ResultReceiver receiver) {
            mResultReceiver = receiver;
        }

        @Override
        public void run() {
            IGoogleBattery googleBattery;
            synchronized (mLock) {
                googleBattery = mGoogleBattery;
            }
            if (googleBattery == null) {
                Log.e(TAG, HAL_UNAVAILABLE);
                return;
            }
            try {
                ChargingStage chargingStage = googleBattery.getChargingStageAndDeadline();
                if (chargingStage == null || chargingStage.stage == null) {
                    Log.d(TAG, "getChargingStageAndDeadline stage is invalid");
                    return;
                }
                String stage = chargingStage.stage.trim();
                int deadlineSecs = chargingStage.deadlineSecs;
                if (DEBUG) {
                    Log.d(
                            TAG,
                            "getChargingStageAndDeadline stage: \""
                                    + stage
                                    + "\", seconds: "
                                    + deadlineSecs);
                }
                Bundle result = new Bundle();
                result.putString(KEY_CHARGING_STAGE, stage);
                result.putInt(KEY_DEADLINE_SECONDS, deadlineSecs);
                mResultReceiver.send(0, result);
            } catch (ServiceSpecificException | RemoteException | IllegalArgumentException e) {
                Log.e(TAG, "getChargingStageAndDeadline failed", e);
            }
        }
    }
}
