package com.google.android.turboadapter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Only lets Device Health Services, signed with Google's keys, use the adapter services. */
public abstract class TurboAdapterAccessChecker {
    private static final String TAG = "TurboAdapterAccessChecker";

    // SHA-256 digests of the Device Health Services signing certificates.
    @VisibleForTesting
    static final String TURBO_DEV_KEY_DIGEST =
            "ad8492cd5a8899a35a9147f9e5d3b005ab8fdcef93b48c3b59df8c2296364784";

    @VisibleForTesting static final String TURBO_PACKAGE_NAME = "com.google.android.apps.turbo";

    @VisibleForTesting
    static final String TURBO_PROD_KEY_DIGEST =
            "c91e8c545ff5f8ba6371bd88ba19fc26841d9ba5f4aea47b8f381df2b4d1ea41";

    private static final String HEX_DIGITS = "0123456789abcdef";

    public static boolean isCallerAllowed(Context context, int uid) {
        PackageManager packageManager = context.getPackageManager();
        String[] packages = packageManager.getPackagesForUid(uid);
        if (packages == null || packages.length == 0) {
            Log.e(TAG, "No packages found for caller; denying access");
            return false;
        }
        if (!Arrays.asList(packages).contains(TURBO_PACKAGE_NAME)) {
            Log.e(TAG, "Denying access to unknown package: " + Arrays.toString(packages));
            return false;
        }
        try {
            SigningInfo signingInfo =
                    packageManager.getPackageInfo(
                                    TURBO_PACKAGE_NAME, PackageManager.GET_SIGNING_CERTIFICATES)
                            .signingInfo;
            if (signingInfo.hasMultipleSigners()) {
                Log.e(TAG, "Packages with multiple signers are not supported; denying access");
                return false;
            }
            Signature[] history = signingInfo.getSigningCertificateHistory();
            if (history == null || history.length < 1) {
                Log.e(TAG, "Unable to get signing certificate history; denying access");
                return false;
            }
            List<String> unknownDigests = new ArrayList<>();
            for (Signature signature : history) {
                String digest = signatureToSha256(signature);
                if (TURBO_PROD_KEY_DIGEST.equals(digest)) {
                    return true;
                }
                if (allowDevSigned() && TURBO_DEV_KEY_DIGEST.equals(digest)) {
                    return true;
                }
                unknownDigests.add(digest);
            }
            Log.e(TAG, "Denying access to package with unknown signature(s): " + unknownDigests);
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to find package information for client; denying access", e);
            return false;
        }
    }

    private static String signatureToSha256(Signature signature) {
        byte[] bytes = signature.toByteArray();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(HEX_DIGITS.charAt((b & 0xff) >>> 4));
            sb.append(HEX_DIGITS.charAt(b & 0xf));
        }
        return new String(sb);
    }

    /** Dev-signed Device Health Services is accepted everywhere but on release-keys user builds. */
    private static boolean allowDevSigned() {
        return !("user".equals(Build.TYPE) && isReleaseKeysBuild());
    }

    private static boolean isReleaseKeysBuild() {
        for (String tag : Build.TAGS.split(",")) {
            if ("release-keys".equals(tag.trim())) {
                return true;
            }
        }
        return false;
    }
}
