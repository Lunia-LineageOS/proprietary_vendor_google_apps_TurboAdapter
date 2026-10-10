package com.google.android.turboadapter;

/** Collects power data for Device Health Services' power anomaly detection. */
interface IGoogleAnomalyService {
    /** Returns a serialized PowerDataResult with the data of the requested providers. */
    byte[] collectPowerData(in int[] providerIds);

    boolean triggerReaction(String reaction, String parameter);

    int getTurboAdapterVersion();
}
