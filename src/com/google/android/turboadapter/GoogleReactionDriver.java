package com.google.android.turboadapter;

import android.app.ActivityManager;
import android.os.Build;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Runs the reactions Device Health Services requests for detected power anomalies. */
class GoogleReactionDriver {
    private static final String TAG = "GoogleReactionDriver";

    private static final String REACTION_GENERATE_PAS_BUG_REPORT = "generatePasBugReport";
    private static final String PAS_BUG_REPORT_SHARE_DESCRIPTION =
            "Potential power anomaly; tap to share bugreport";
    private static final int MAX_BUG_TITLE_LENGTH = 50;
    private static final int MAX_SHARE_DESCRIPTION_LENGTH = 150;

    @VisibleForTesting Map<String, Function<String, Boolean>> mReactions = new HashMap<>();

    GoogleReactionDriver() {
        mReactions.put(REACTION_GENERATE_PAS_BUG_REPORT, this::generatePasBugReport);
    }

    public boolean triggerReaction(String reaction, String parameter) {
        if (reaction == null || parameter == null) {
            Log.e(
                    TAG,
                    "Unable to trigger reaction ( "
                            + reaction
                            + " ) due to incomplete parameters.");
            return false;
        }
        Function<String, Boolean> function = mReactions.get(reaction);
        if (function == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Unsupported reaction '" + reaction + "' was skipped.");
            }
            return false;
        }
        return function.apply(parameter);
    }

    private boolean generatePasBugReport(String bugTitle) {
        return generatePasBugReport(bugTitle, PAS_BUG_REPORT_SHARE_DESCRIPTION);
    }

    private boolean generatePasBugReport(String bugTitle, String shareDescription) {
        String buildType = Build.TYPE;
        if (!"eng".equals(buildType) && !"userdebug".equals(buildType)) {
            Log.e(
                    TAG,
                    "We should never generate a bugreport on a not debug build. (build type: "
                            + buildType
                            + ").");
            return false;
        }
        if (bugTitle == null || shareDescription == null) {
            Log.e(TAG, "Unable to trigger bugreport due to incomplete parameters.");
            return false;
        }
        if (TextUtils.isEmpty(bugTitle) || TextUtils.isEmpty(shareDescription)) {
            Log.e(TAG, "Unable to trigger bugreport due to empty Title or shareDescription.");
            return false;
        }
        if (bugTitle.length() > MAX_BUG_TITLE_LENGTH) {
            Log.e(TAG, "BugTitle should be less than 50 characters.");
            return false;
        }
        if (shareDescription.length() > MAX_SHARE_DESCRIPTION_LENGTH) {
            Log.e(TAG, "ShareDescription should be less than 150 characters");
            return false;
        }
        try {
            ActivityManager.getService()
                    .requestInteractiveBugReportWithDescription(bugTitle, shareDescription);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "GeneratePasBugReport done");
            }
            return true;
        } catch (RemoteException | IllegalArgumentException e) {
            Log.e(TAG, "RequestInteractiveBugReportWithDescription failed.", e);
            return false;
        }
    }
}
