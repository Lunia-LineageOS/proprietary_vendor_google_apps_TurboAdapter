package com.google.android.turboadapter;

import android.util.Log;

import com.google.android.turboadapter.poweranomalyservice.proto.PowerDataResult;

import java.util.Optional;

/** A source of power data for {@link GooglePowerDataCollector}. */
abstract class GooglePowerDataProvider {
    private final String tag;

    GooglePowerDataProvider(String tag) {
        this.tag = tag;
    }

    abstract Optional<PowerDataResult> getData();

    enum StateName {
        WAKELOCK_ACQUIRED("Acquired"),
        SCAN_TIME_ALL("ScanTimeAll"),
        SCAN_TIME_UNFILTERED("ScanTimeUnfiltered"),
        SCAN_RESULT_COUNT("ScanResultCount"),
        RX_TIME("RxTime"),
        TX_TIME("TxTime"),
        USER_TIME("UserTime"),
        SYSTEM_TIME("SystemTime"),
        TOTAL_TIME("TotalTime");

        private final String name;

        StateName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum EntityName {
        NULL("(null)"),
        EMPTY("(empty)"),
        UID_PREFIX("UID_"),
        COULOMB_COUNT_UAH("ChargeCounter (uAh)"),
        SUSPEND_ATTEMPT_COUNT("SuspendAttemptCount"),
        FAILED_SUSPEND_COUNT("FailedSuspendCount"),
        SHORT_SUSPEND_COUNT("ShortSuspendCount"),
        TOTAL_SUSPEND_TIME("TotalSuspendTimeMillis"),
        GOOD_SUSPEND_TIME("GoodSuspendTimeMillis"),
        SHORT_SUSPEND_TIME("ShortSuspendTimeMillis"),
        SUSPEND_OVERHEAD_TIME("SuspendOverheadTimeMillis"),
        FAILED_SUSPEND_OVERHEAD_TIME("FailedSuspendOverheadTimeMillis"),
        NEW_BACKOFF_COUNT("NewBackoffCount"),
        BACKOFF_CONTINUE_COUNT("BackoffContinueCount"),
        SLEEP_TIME("SleepTimeMillis"),
        SHORT_SUSPEND_THRESHOLD_TIME("ShortSuspendThresholdMillis");

        private final String name;

        EntityName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static String createUidName(int uid) {
        return String.format("%s%d", EntityName.UID_PREFIX, uid);
    }

    Optional<PowerDataResult> retrieveData() {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, "Retrieving data begin");
        }
        Optional<PowerDataResult> data = getData();
        int count = data.isPresent() ? data.get().getPowerDataCount() : 0;
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, "Retrieving data end: count=" + count);
        }
        return data;
    }
}
