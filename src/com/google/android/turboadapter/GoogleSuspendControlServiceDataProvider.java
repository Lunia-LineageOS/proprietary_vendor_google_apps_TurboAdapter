package com.google.android.turboadapter;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.sysprop.SuspendProperties;
import android.system.suspend.internal.ISuspendControlServiceInternal;
import android.system.suspend.internal.SuspendInfo;
import android.system.suspend.internal.WakeLockInfo;
import android.system.suspend.internal.WakeupInfo;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.android.turboadapter.poweranomalyservice.proto.EntityType;
import com.google.android.turboadapter.poweranomalyservice.proto.Id;
import com.google.android.turboadapter.poweranomalyservice.proto.KeyedPowerData;
import com.google.android.turboadapter.poweranomalyservice.proto.PowerData;
import com.google.android.turboadapter.poweranomalyservice.proto.PowerDataResult;
import com.google.android.turboadapter.poweranomalyservice.proto.Residency;
import com.google.android.turboadapter.poweranomalyservice.proto.ResourceHolder;
import com.google.android.turboadapter.poweranomalyservice.proto.Scalar;

import java.util.Optional;

/** Reports native wakelocks, kernel wakeup reasons and suspend stats from system_suspend. */
class GoogleSuspendControlServiceDataProvider extends GooglePowerDataProvider {
    private static final String TAG = "GoogleSuspendControlServiceDataProvider";

    private static final Integer DEFAULT_SHORT_SUSPEND_THRESHOLD_MILLIS = 0;

    GoogleSuspendControlServiceDataProvider() {
        super(TAG);
    }

    @Override
    public Optional<PowerDataResult> getData() {
        Optional<ISuspendControlServiceInternal> suspendControl = getSuspendControlInternal();
        if (!suspendControl.isPresent()) {
            return Optional.empty();
        }
        PowerDataResult.Builder result = PowerDataResult.newBuilder();
        getWakeLockData(suspendControl.get()).ifPresent(result::mergeFrom);
        getWakeupReasonData(suspendControl.get()).ifPresent(result::mergeFrom);
        getSuspendData(suspendControl.get()).ifPresent(result::mergeFrom);
        return nonEmpty(result.build());
    }

    @VisibleForTesting
    Optional<ISuspendControlServiceInternal> getSuspendControlInternal() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Attempt to get service...");
        }
        try {
            return Optional.of(
                    ISuspendControlServiceInternal.Stub.asInterface(
                            ServiceManager.waitForService("suspend_control_internal")));
        } catch (Exception e) {
            Log.e(TAG, "Failed to access suspend control service; unable to get data.", e);
            return Optional.empty();
        }
    }

    @VisibleForTesting
    Optional<PowerDataResult> getWakeLockData(ISuspendControlServiceInternal suspendControl) {
        try {
            WakeLockInfo[] wakeLocks = suspendControl.getWakeLockStats();
            PowerDataResult.Builder result = PowerDataResult.newBuilder();
            long timestamp = getCurrentElapsedRealtimeMillis();
            for (WakeLockInfo wakeLock : wakeLocks) {
                KeyedPowerData.Builder data =
                        KeyedPowerData.newBuilder()
                                .setId(
                                        id(
                                                wakeLock.isKernelWakelock
                                                        ? EntityType.PWR_ENT_KERNEL_WAKESOURCE
                                                        : EntityType.PWR_ENT_NATIVE_WAKELOCK,
                                                wakeLock.name.isEmpty()
                                                        ? EntityName.EMPTY.toString()
                                                        : wakeLock.name,
                                                timestamp))
                                .setData(
                                        PowerData.newBuilder()
                                                .addResidency(
                                                        Residency.newBuilder()
                                                                .setStateName(
                                                                        StateName.WAKELOCK_ACQUIRED
                                                                                .toString())
                                                                .setDuration(wakeLock.totalTime)
                                                                .setCount(wakeLock.activeCount)));
                if (!wakeLock.isKernelWakelock) {
                    data.setResourceHolder(ResourceHolder.newBuilder().setId(wakeLock.pid));
                }
                result.addPowerData(data);
            }
            return nonEmpty(result.build());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to obtain wakelock stats from suspend HAL.", e);
            return Optional.empty();
        }
    }

    @VisibleForTesting
    Optional<PowerDataResult> getWakeupReasonData(ISuspendControlServiceInternal suspendControl) {
        try {
            WakeupInfo[] wakeups = suspendControl.getWakeupStats();
            PowerDataResult.Builder result = PowerDataResult.newBuilder();
            long timestamp = getCurrentElapsedRealtimeMillis();
            for (WakeupInfo wakeup : wakeups) {
                result.addPowerData(
                        scalarData(
                                EntityType.PWR_ENT_KERNEL_WAKE_REASON,
                                wakeup.name,
                                timestamp,
                                wakeup.count));
            }
            return nonEmpty(result.build());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to obtain wakeup reason stats from suspend HAL.", e);
            return Optional.empty();
        }
    }

    private void addSuspendPowerData(
            PowerDataResult.Builder result, long timestamp, EntityName name, long value) {
        result.addPowerData(
                scalarData(EntityType.PWR_ENT_KERNEL_SUSPEND, name.toString(), timestamp, value));
    }

    @VisibleForTesting
    Optional<PowerDataResult> getSuspendData(ISuspendControlServiceInternal suspendControl) {
        try {
            SuspendInfo stats = suspendControl.getSuspendStats();
            int shortSuspendThresholdMillis = getShortSuspendThresholdMillis();
            PowerDataResult.Builder result = PowerDataResult.newBuilder();
            long timestamp = getCurrentElapsedRealtimeMillis();
            addSuspendPowerData(
                    result, timestamp, EntityName.SUSPEND_ATTEMPT_COUNT, stats.suspendAttemptCount);
            addSuspendPowerData(
                    result, timestamp, EntityName.FAILED_SUSPEND_COUNT, stats.failedSuspendCount);
            addSuspendPowerData(
                    result, timestamp, EntityName.SHORT_SUSPEND_COUNT, stats.shortSuspendCount);
            addSuspendPowerData(
                    result, timestamp, EntityName.TOTAL_SUSPEND_TIME, stats.suspendTimeMillis);
            addSuspendPowerData(
                    result,
                    timestamp,
                    EntityName.GOOD_SUSPEND_TIME,
                    stats.suspendTimeMillis - stats.shortSuspendTimeMillis);
            addSuspendPowerData(
                    result, timestamp, EntityName.SHORT_SUSPEND_TIME, stats.shortSuspendTimeMillis);
            addSuspendPowerData(
                    result,
                    timestamp,
                    EntityName.SUSPEND_OVERHEAD_TIME,
                    stats.suspendOverheadTimeMillis);
            addSuspendPowerData(
                    result,
                    timestamp,
                    EntityName.FAILED_SUSPEND_OVERHEAD_TIME,
                    stats.failedSuspendOverheadTimeMillis);
            addSuspendPowerData(
                    result, timestamp, EntityName.NEW_BACKOFF_COUNT, stats.newBackoffCount);
            addSuspendPowerData(
                    result,
                    timestamp,
                    EntityName.BACKOFF_CONTINUE_COUNT,
                    stats.backoffContinueCount);
            addSuspendPowerData(result, timestamp, EntityName.SLEEP_TIME, stats.sleepTimeMillis);
            addSuspendPowerData(
                    result,
                    timestamp,
                    EntityName.SHORT_SUSPEND_THRESHOLD_TIME,
                    shortSuspendThresholdMillis);
            return Optional.of(result.build());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to obtain suspend stats from suspend HAL.", e);
            return Optional.empty();
        }
    }

    @VisibleForTesting
    long getCurrentElapsedRealtimeMillis() {
        return SystemClock.elapsedRealtime();
    }

    @VisibleForTesting
    int getShortSuspendThresholdMillis() {
        return SuspendProperties.short_suspend_threshold_millis()
                .orElse(DEFAULT_SHORT_SUSPEND_THRESHOLD_MILLIS);
    }

    private static Id.Builder id(EntityType type, String name, long timestamp) {
        return Id.newBuilder().setEntityType(type).setEntityName(name).setTimestamp(timestamp);
    }

    private static KeyedPowerData.Builder scalarData(
            EntityType type, String name, long timestamp, long value) {
        return KeyedPowerData.newBuilder()
                .setId(id(type, name, timestamp))
                .setData(PowerData.newBuilder().setScalar(Scalar.newBuilder().setValue(value)));
    }

    private static Optional<PowerDataResult> nonEmpty(PowerDataResult result) {
        return result.getPowerDataCount() == 0 ? Optional.empty() : Optional.of(result);
    }
}
