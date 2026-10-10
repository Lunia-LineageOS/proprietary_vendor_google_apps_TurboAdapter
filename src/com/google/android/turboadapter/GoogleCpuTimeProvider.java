package com.google.android.turboadapter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import androidx.annotation.VisibleForTesting;

import com.android.internal.os.KernelCpuUidTimeReader;

import com.google.android.turboadapter.poweranomalyservice.proto.EntityType;
import com.google.android.turboadapter.poweranomalyservice.proto.PowerDataResult;

import java.util.Optional;

/** Reports the user and system CPU time of each UID. */
class GoogleCpuTimeProvider extends GooglePowerDataProvider {
    private static final String TAG = "GoogleCpuTimeProvider";

    private final Context mContext;
    private final Object mCpuTimePerUidLock = new Object();

    @VisibleForTesting
    KernelCpuUidTimeReader.KernelCpuUidUserSysTimeReader mCpuUidUserSysTimeReader;

    private PackageManager mPackageManager;

    GoogleCpuTimeProvider(Context context) {
        super(TAG);
        synchronized (mCpuTimePerUidLock) {
            mContext = context;
            if (context != null) {
                mPackageManager = context.getPackageManager();
            }
            mCpuUidUserSysTimeReader =
                    new KernelCpuUidTimeReader.KernelCpuUidUserSysTimeReader(true);
        }
    }

    @Override
    public Optional<PowerDataResult> getData() {
        PowerDataResult.Builder result = PowerDataResult.newBuilder();
        synchronized (mCpuTimePerUidLock) {
            mCpuUidUserSysTimeReader.readAbsolute(
                    (uid, times) -> addUidCpuTime(result, uid, times));
        }
        PowerDataResult data = result.build();
        return data.getPowerDataCount() == 0 ? Optional.empty() : Optional.of(data);
    }

    /** {@code times} holds the user and system time in microseconds. */
    private void addUidCpuTime(PowerDataResult.Builder result, int uid, long[] times) {
        long userTimeMs = times[0] / 1000;
        long systemTimeMs = times[1] / 1000;
        String name = mPackageManager != null ? mPackageManager.getNameForUid(uid) : null;
        if (name == null) {
            name = createUidName(uid);
        }
        result.addPowerData(
                GoogleResidencyDataBuilder.newBuilder(
                                EntityType.PWR_ENT_CPU_TIME, name, SystemClock::elapsedRealtime)
                        .addResidencyData(StateName.USER_TIME.toString(), userTimeMs, 0L)
                        .addResidencyData(StateName.SYSTEM_TIME.toString(), systemTimeMs, 0L)
                        .addResidencyData(
                                StateName.TOTAL_TIME.toString(), userTimeMs + systemTimeMs, 0L)
                        .build());
    }
}
