package com.google.android.turboadapter;

import android.content.Context;
import android.util.Log;

import com.google.android.turboadapter.poweranomalyservice.proto.PowerDataResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Merges the data of the providers Device Health Services asks for into one result. */
class GooglePowerDataCollector {
    private static final String TAG = "GooglePowerDataCollector";

    // Provider ids used by IGoogleAnomalyService#collectPowerData.
    private static final int PROVIDER_POWER_STATS_HAL = 0;
    private static final int PROVIDER_SUSPEND_CONTROL = 1;
    private static final int PROVIDER_CPU_TIME = 2;
    private static final int PROVIDER_FRAMEWORK_WAKELOCKS = 3;
    private static final int PROVIDER_BATTERY_PROPERTIES = 4;

    private static GooglePowerDataProvider mBatteryPropertiesProvider;
    private static GooglePowerDataProvider mCpuTimeProvider;
    private static GooglePowerDataProvider mFrameworkWakelocksProvider;
    private static GooglePowerDataProvider mPowerStatsHalDataProvider;
    private static GooglePowerDataProvider mSuspendControlProvider;

    private final Map<Integer, GooglePowerDataProvider> mMajorProviders;

    GooglePowerDataCollector(Context context) {
        Map<Integer, GooglePowerDataProvider> providers = new HashMap<>();
        mPowerStatsHalDataProvider = new GooglePowerStatsHalDataProvider();
        mSuspendControlProvider = new GoogleSuspendControlServiceDataProvider();
        mCpuTimeProvider = new GoogleCpuTimeProvider(context);
        mBatteryPropertiesProvider = new GoogleBatteryPropertiesDataProvider();
        mFrameworkWakelocksProvider = new GoogleBatteryStatsDataProvider();
        providers.put(PROVIDER_POWER_STATS_HAL, mPowerStatsHalDataProvider);
        providers.put(PROVIDER_SUSPEND_CONTROL, mSuspendControlProvider);
        providers.put(PROVIDER_CPU_TIME, mCpuTimeProvider);
        providers.put(PROVIDER_FRAMEWORK_WAKELOCKS, mFrameworkWakelocksProvider);
        providers.put(PROVIDER_BATTERY_PROPERTIES, mBatteryPropertiesProvider);
        mMajorProviders = Collections.unmodifiableMap(providers);
    }

    /** Returns a serialized {@link PowerDataResult}, counting the providers that failed. */
    byte[] collectPowerData(int[] providerIds) {
        PowerDataResult.Builder result = PowerDataResult.newBuilder();
        int errorCount;
        if (providerIds == null) {
            Log.e(TAG, "The provider ID array was null.");
            errorCount = 1;
        } else {
            errorCount = 0;
            for (Map.Entry<Integer, GooglePowerDataProvider> entry : mMajorProviders.entrySet()) {
                int id = entry.getKey();
                GooglePowerDataProvider provider = entry.getValue();
                if (provider == null) {
                    Log.e(TAG, "Uninitialized data provider for id " + id + ".");
                } else if (contains(providerIds, id)) {
                    Optional<PowerDataResult> data = provider.retrieveData();
                    if (data.isPresent()) {
                        result.mergeFrom(data.get());
                    } else {
                        Log.e(TAG, "Got null from data provider (id: " + id + ").");
                        errorCount++;
                    }
                }
            }
        }
        result.setErrorCount(errorCount);
        return result.build().toByteArray();
    }

    private boolean contains(int[] ids, int id) {
        for (int candidate : ids) {
            if (candidate == id) {
                return true;
            }
        }
        return false;
    }
}
