package com.google.android.turboadapter;

import android.os.BatteryManager;
import android.os.BatteryProperty;
import android.os.IBatteryPropertiesRegistrar;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.turboadapter.poweranomalyservice.proto.EntityType;
import com.google.android.turboadapter.poweranomalyservice.proto.Id;
import com.google.android.turboadapter.poweranomalyservice.proto.KeyedPowerData;
import com.google.android.turboadapter.poweranomalyservice.proto.PowerData;
import com.google.android.turboadapter.poweranomalyservice.proto.PowerDataResult;
import com.google.android.turboadapter.poweranomalyservice.proto.Scalar;

import java.util.Optional;

/** Reports the battery charge counter. */
class GoogleBatteryPropertiesDataProvider extends GooglePowerDataProvider {
    private static final String TAG = "GoogleBatteryPropertiesDataProvider";

    GoogleBatteryPropertiesDataProvider() {
        super(TAG);
    }

    @Override
    public Optional<PowerDataResult> getData() {
        try {
            IBatteryPropertiesRegistrar registrar =
                    IBatteryPropertiesRegistrar.Stub.asInterface(
                            ServiceManager.waitForService("batteryproperties"));
            if (registrar == null) {
                Log.e(TAG, "IBatteryPropertiesRegistrar is not supported!");
                return Optional.empty();
            }
            BatteryProperty property = new BatteryProperty();
            if (registrar.getProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, property)
                    != 0) {
                Log.e(TAG, "Failed to read ChargeCounter from BatteryProperties.");
                return Optional.empty();
            }
            long chargeCounter = property.getLong();
            Id id =
                    Id.newBuilder()
                            .setEntityType(EntityType.PWR_ENT_COULOMB_COUNTER)
                            .setEntityName(EntityName.COULOMB_COUNT_UAH.toString())
                            .setTimestamp(SystemClock.elapsedRealtime())
                            .build();
            PowerData data =
                    PowerData.newBuilder()
                            .setScalar(Scalar.newBuilder().setValue(chargeCounter))
                            .build();
            return Optional.of(
                    PowerDataResult.newBuilder()
                            .addPowerData(KeyedPowerData.newBuilder().setId(id).setData(data))
                            .build());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to read data from BatteryPropertiesRegistrar:", e);
            return Optional.empty();
        }
    }
}
