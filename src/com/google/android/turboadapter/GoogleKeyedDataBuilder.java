package com.google.android.turboadapter;

import com.google.android.turboadapter.poweranomalyservice.proto.EntityType;
import com.google.android.turboadapter.poweranomalyservice.proto.Id;
import com.google.android.turboadapter.poweranomalyservice.proto.KeyedPowerData;
import com.google.android.turboadapter.poweranomalyservice.proto.PowerData;
import com.google.android.turboadapter.poweranomalyservice.proto.ResourceHolder;

import java.util.function.LongSupplier;

abstract class GoogleKeyedDataBuilder {
    private final LongSupplier elapsedTimeMillis;
    private final String entityName;
    private final EntityType entityType;
    private int resourceHolderId = -1;
    private String resourceHolderName = null;

    GoogleKeyedDataBuilder(
            EntityType entityType, String entityName, LongSupplier elapsedTimeMillis) {
        this.entityType = entityType;
        this.entityName = entityName;
        this.elapsedTimeMillis = elapsedTimeMillis;
    }

    abstract PowerData.Builder createPowerData();

    final KeyedPowerData.Builder build() {
        KeyedPowerData.Builder data =
                KeyedPowerData.newBuilder()
                        .setId(
                                Id.newBuilder()
                                        .setEntityType(entityType)
                                        .setEntityName(entityName)
                                        .setTimestamp(elapsedTimeMillis.getAsLong()))
                        .setData(createPowerData());
        if (resourceHolderId >= 0) {
            data.setResourceHolder(
                    ResourceHolder.newBuilder()
                            .setId(resourceHolderId)
                            .setName(resourceHolderName));
        }
        return data;
    }
}
