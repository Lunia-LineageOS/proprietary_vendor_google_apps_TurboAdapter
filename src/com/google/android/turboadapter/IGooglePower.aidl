package com.google.android.turboadapter;

import android.os.Bundle;

/** Lets Device Health Services send boosts to the Pixel power HAL extension. */
interface IGooglePower {
    void setPowerHints(in String[] hints, int durationMs);

    void setAdaptiveCpuConfig(boolean enabled, in Bundle config);
}
