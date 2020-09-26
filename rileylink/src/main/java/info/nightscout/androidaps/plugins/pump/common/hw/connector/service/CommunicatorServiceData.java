package info.nightscout.androidaps.plugins.pump.common.hw.connector.service;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;


/**
 * Created by Dirceu on 16/09/2020.
 */

@Singleton
public interface CommunicatorServiceData {

    boolean tuneUpDone = false;

    public void setPumpID(String pumpId, byte[] pumpIdBytes);

    public void setRileyLinkServiceState(RileyLinkServiceState newState);

    public RileyLinkServiceState getRileyLinkServiceState();

    public void setServiceState(RileyLinkServiceState newState, RileyLinkError errorCode);


}
