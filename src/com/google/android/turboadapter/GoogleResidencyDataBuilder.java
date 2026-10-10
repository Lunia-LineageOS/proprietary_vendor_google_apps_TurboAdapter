package com.google.android.turboadapter;

import com.google.android.turboadapter.poweranomalyservice.proto.EntityType;
import com.google.android.turboadapter.poweranomalyservice.proto.PowerData;
import com.google.android.turboadapter.poweranomalyservice.proto.Residency;

import java.util.function.LongSupplier;

final class GoogleResidencyDataBuilder extends GoogleKeyedDataBuilder {
    private final PowerData.Builder powerDataBuilder = PowerData.newBuilder();

    private GoogleResidencyDataBuilder(
            EntityType entityType, String entityName, LongSupplier elapsedTimeMillis) {
        super(entityType, entityName, elapsedTimeMillis);
    }

    static GoogleResidencyDataBuilder newBuilder(
            EntityType entityType, String entityName, LongSupplier elapsedTimeMillis) {
        return new GoogleResidencyDataBuilder(entityType, entityName, elapsedTimeMillis);
    }

    @Override
    PowerData.Builder createPowerData() {
        return powerDataBuilder;
    }

    GoogleResidencyDataBuilder addResidencyData(String stateName, Long duration, Long count) {
        powerDataBuilder.addResidency(
                Residency.newBuilder()
                        .setStateName(stateName)
                        .setDuration(duration)
                        .setCount(count));
        return this;
    }
}
