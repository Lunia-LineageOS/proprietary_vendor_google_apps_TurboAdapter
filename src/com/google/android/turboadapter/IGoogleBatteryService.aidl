package com.google.android.turboadapter;

import android.os.ResultReceiver;

/** Proxies the Google Battery HAL to Device Health Services. */
interface IGoogleBatteryService {
    boolean setChargingDeadline(int seconds);

    void getChargingStageAndDeadline(in ResultReceiver receiver);

    boolean isAdaptiveChargingEnabled();

    String getVersion();

    boolean setProperty(int feature, int property, int value);

    void getProperty(int feature, int property, in ResultReceiver receiver);

    boolean setStringProperty(int feature, int property, String value);

    String getStringProperty(int feature, int property);

    List<String> getRelaxationData(long arg);
}
