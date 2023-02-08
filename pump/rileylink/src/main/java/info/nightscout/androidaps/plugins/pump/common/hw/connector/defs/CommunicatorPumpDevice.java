package info.nightscout.androidaps.plugins.pump.common.hw.connector.defs;

import dagger.android.DaggerService;


/**
 * used by medlink
 */
public interface CommunicatorPumpDevice {

//    void setIsBusy(boolean isBusy_);

//    boolean isBusy();

//    void resetConfiguration();

//    boolean hasTuneUp();

//    void doTuneUpDevice();

    void triggerPumpConfigurationChangedEvent();

    DaggerService getService();

}
