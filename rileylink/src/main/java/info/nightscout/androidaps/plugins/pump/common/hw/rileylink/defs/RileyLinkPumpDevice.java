package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs;

import info.nightscout.androidaps.plugins.pump.common.hw.connector.defs.CommunicatorPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService;

public interface RileyLinkPumpDevice extends CommunicatorPumpDevice {

    void setBusy(boolean busy);

    void triggerPumpConfigurationChangedEvent();

    RileyLinkService getRileyLinkService();

    RileyLinkPumpInfo getPumpInfo();

    long getLastConnectionTimeMillis();

    void setLastCommunicationToNow();
}
