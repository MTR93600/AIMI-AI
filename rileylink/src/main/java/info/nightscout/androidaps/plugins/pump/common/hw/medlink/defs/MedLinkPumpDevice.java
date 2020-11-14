package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs;

import info.nightscout.androidaps.plugins.pump.common.hw.connector.defs.CommunicatorPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo;


/**
 * Created by Dirceu on 30/09/20.
 */
public interface MedLinkPumpDevice extends CommunicatorPumpDevice {

    void setBusy(boolean busy);

    void triggerPumpConfigurationChangedEvent();

    MedLinkService getRileyLinkService();

    MedLinkService getMedLinkService();

    RileyLinkPumpInfo getPumpInfo();

    long getLastConnectionTimeMillis();

    void setLastCommunicationToNow();


}