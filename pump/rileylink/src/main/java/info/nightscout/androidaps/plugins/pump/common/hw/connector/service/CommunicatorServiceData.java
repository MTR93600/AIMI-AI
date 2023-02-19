package info.nightscout.androidaps.plugins.pump.common.hw.connector.service;

import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;


/**
 * Created by Dirceu on 16/09/2020.
 * used by medlink
 */


@Singleton
public interface CommunicatorServiceData {

    boolean tuneUpDone = false;

    public void setPumpID(String pumpId, byte[] pumpIdBytes);

    public void setRileyLinkServiceState(RileyLinkServiceState newState);

    public RileyLinkServiceState getRileyLinkServiceState();

    public void setServiceState(RileyLinkServiceState newState, RileyLinkError errorCode);


}
