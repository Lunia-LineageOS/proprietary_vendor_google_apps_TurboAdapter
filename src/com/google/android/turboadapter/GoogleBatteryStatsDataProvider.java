package com.google.android.turboadapter;

import android.os.BluetoothBatteryStats;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.WakeLockStats;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.internal.app.IBatteryStats;

import com.google.android.turboadapter.poweranomalyservice.proto.EntityType;
import com.google.android.turboadapter.poweranomalyservice.proto.Id;
import com.google.android.turboadapter.poweranomalyservice.proto.KeyedPowerData;
import com.google.android.turboadapter.poweranomalyservice.proto.PowerData;
import com.google.android.turboadapter.poweranomalyservice.proto.PowerDataResult;
import com.google.android.turboadapter.poweranomalyservice.proto.Residency;
import com.google.android.turboadapter.poweranomalyservice.proto.ResourceHolder;

import java.util.List;
import java.util.Optional;

/** Reports framework wakelocks and Bluetooth usage per UID from BatteryStats. */
class GoogleBatteryStatsDataProvider extends GooglePowerDataProvider {
    private static final String TAG = "GoogleBatteryStatsDataProvider";

    GoogleBatteryStatsDataProvider() {
        super(TAG);
    }

    @Override
    public Optional<PowerDataResult> getData() {
        Optional<IBatteryStats> batteryStats = getBatteryStatsService();
        if (!batteryStats.isPresent()) {
            return Optional.empty();
        }
        PowerDataResult.Builder result = PowerDataResult.newBuilder();
        getWakeLockData(batteryStats.get()).ifPresent(result::mergeFrom);
        getBluetoothData(batteryStats.get()).ifPresent(result::mergeFrom);
        return nonEmpty(result.build());
    }

    @VisibleForTesting
    Optional<IBatteryStats> getBatteryStatsService() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Attempt to get service...");
        }
        try {
            return Optional.of(
                    IBatteryStats.Stub.asInterface(ServiceManager.waitForService("batterystats")));
        } catch (Exception e) {
            Log.e(TAG, "Failed to access battery stats service; unable to get data.", e);
            return Optional.empty();
        }
    }

    private Optional<PowerDataResult> getWakeLockData(IBatteryStats batteryStats) {
        try {
            List<WakeLockStats.WakeLock> wakeLocks =
                    batteryStats.getWakeLockStats().getAggregatedWakeLocks();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Obtained stats for " + wakeLocks.size() + " aggregated wakelocks.");
            }
            if (wakeLocks.size() == 0) {
                return Optional.empty();
            }
            PowerDataResult.Builder result = PowerDataResult.newBuilder();
            long timestamp = getCurrentElapsedRealtimeMillis();
            for (WakeLockStats.WakeLock wakeLock : wakeLocks) {
                PowerData.Builder data =
                        PowerData.newBuilder()
                                .addResidency(
                                        residency(
                                                StateName.WAKELOCK_ACQUIRED,
                                                wakeLock.totalWakeLockData.totalTimeHeldMs,
                                                wakeLock.totalWakeLockData.timesAcquired));
                result.addPowerData(
                        keyedUidData(
                                EntityType.PWR_ENT_FRAMEWORK_WAKELOCK,
                                wakeLock.uid,
                                timestamp,
                                data));
            }
            return nonEmpty(result.build());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to obtain wakelock stats from battery stats service.", e);
            return Optional.empty();
        }
    }

    private Optional<PowerDataResult> getBluetoothData(IBatteryStats batteryStats) {
        try {
            List<BluetoothBatteryStats.UidStats> uidStats =
                    batteryStats.getBluetoothBatteryStats().getUidStats();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Obtained bluetooth stats for " + uidStats.size() + " UIDs.");
            }
            if (uidStats.size() == 0) {
                return Optional.empty();
            }
            PowerDataResult.Builder result = PowerDataResult.newBuilder();
            long timestamp = getCurrentElapsedRealtimeMillis();
            for (BluetoothBatteryStats.UidStats stats : uidStats) {
                PowerData.Builder data =
                        PowerData.newBuilder()
                                .addResidency(
                                        residency(StateName.SCAN_TIME_ALL, stats.scanTimeMs, 0))
                                .addResidency(
                                        residency(
                                                StateName.SCAN_TIME_UNFILTERED,
                                                stats.unoptimizedScanTimeMs,
                                                0))
                                .addResidency(
                                        residency(
                                                StateName.SCAN_RESULT_COUNT,
                                                0,
                                                stats.scanResultCount))
                                .addResidency(residency(StateName.RX_TIME, stats.rxTimeMs, 0))
                                .addResidency(residency(StateName.TX_TIME, stats.txTimeMs, 0));
                result.addPowerData(
                        keyedUidData(EntityType.PWR_ENT_BLUETOOTH, stats.uid, timestamp, data));
            }
            return nonEmpty(result.build());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to obtain bluetooth stats from battery stats service.", e);
            return Optional.empty();
        }
    }

    @VisibleForTesting
    long getCurrentElapsedRealtimeMillis() {
        return SystemClock.elapsedRealtime();
    }

    private static Residency.Builder residency(StateName state, long duration, long count) {
        return Residency.newBuilder()
                .setStateName(state.toString())
                .setDuration(duration)
                .setCount(count);
    }

    private static KeyedPowerData.Builder keyedUidData(
            EntityType type, int uid, long timestamp, PowerData.Builder data) {
        return KeyedPowerData.newBuilder()
                .setId(
                        Id.newBuilder()
                                .setEntityType(type)
                                .setEntityName(createUidName(uid))
                                .setTimestamp(timestamp))
                .setData(data)
                .setResourceHolder(ResourceHolder.newBuilder().setId(uid));
    }

    private static Optional<PowerDataResult> nonEmpty(PowerDataResult result) {
        return result.getPowerDataCount() == 0 ? Optional.empty() : Optional.of(result);
    }
}
