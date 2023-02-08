package info.nightscout.androidaps.plugins.pump.medtronic.util;

public interface MedtronicPumpPluginInterface {

    enum BolusDeliveryType {
        Idle, //
        DeliveryPrepared, //
        Delivering, //
        CancelDelivery
    }



}
