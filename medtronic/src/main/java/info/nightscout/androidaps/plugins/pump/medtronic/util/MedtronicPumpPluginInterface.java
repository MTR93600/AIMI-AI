package info.nightscout.androidaps.plugins.pump.medtronic.util;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.pump.medtronic.R;

public interface MedtronicPumpPluginInterface {

    enum BolusDeliveryType {
        Idle, //
        DeliveryPrepared, //
        Delivering, //
        CancelDelivery
    }



}
