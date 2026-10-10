package com.google.android.turboadapter;

import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.android.turboadapter.poweranomalyservice.proto.PowerDataResult;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Reports the PowerStats HAL residencies and rail energy via the prebuilt JNI library. */
class GooglePowerStatsHalDataProvider extends GooglePowerDataProvider {
    private static final String TAG = "GooglePowerStatsHalDataProvider";

    @VisibleForTesting static boolean sIsJniLoaded = false;

    @VisibleForTesting Map<String, Supplier<byte[]>> mMethodMap = new HashMap<>();

    static {
        try {
            System.loadLibrary("powerstatshaldataprovider");
            sIsJniLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load powerstatshaldataprovider library: " + e.getMessage());
        }
    }

    /** Returns a serialized {@link PowerDataResult}, or null on failure. */
    private static native byte[] getRailEnergyNative();

    /** Returns a serialized {@link PowerDataResult}, or null on failure. */
    private static native byte[] getResidenciesNative();

    GooglePowerStatsHalDataProvider() {
        super(TAG);
        mMethodMap.put("getResidencies", GooglePowerStatsHalDataProvider::getResidenciesNative);
        mMethodMap.put("getRailEnergy", GooglePowerStatsHalDataProvider::getRailEnergyNative);
    }

    @Override
    public Optional<PowerDataResult> getData() {
        if (!sIsJniLoaded) {
            return Optional.empty();
        }
        PowerDataResult.Builder result = PowerDataResult.newBuilder();
        try {
            for (Map.Entry<String, Supplier<byte[]>> entry : mMethodMap.entrySet()) {
                byte[] data = entry.getValue().get();
                if (data == null) {
                    Log.e(TAG, "Error getting data via native call: " + entry.getKey());
                } else {
                    result.mergeFrom(data);
                }
            }
            PowerDataResult data = result.build();
            return data.getPowerDataCount() == 0 ? Optional.empty() : Optional.of(data);
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Failed to parse protobuf", e);
            return Optional.empty();
        }
    }
}
