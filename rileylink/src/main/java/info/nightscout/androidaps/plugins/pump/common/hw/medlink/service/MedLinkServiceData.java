package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.data.MLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;


/**
 * Created by Dirceu on 24/09/2018.
 * copied from RileyLinkServiceData
 */

@Singleton
public class MedLinkServiceData {

    @Inject AAPSLogger aapsLogger;
    @Inject MedLinkUtil medLinkUtil;
    @Inject RxBusWrapper rxBus;

    boolean tuneUpDone = false;
    public RileyLinkError rileyLinkError;
    public RileyLinkServiceState rileyLinkServiceState = RileyLinkServiceState.NotStarted;
    public RileyLinkFirmwareVersion firmwareVersion;
    public RileyLinkTargetFrequency rileyLinkTargetFrequency;

    public String rileylinkAddress;
    long lastTuneUpTime = 0L;
    public Double lastGoodFrequency;

    // bt version
    public String versionBLE113;
    // radio version
    public RileyLinkFirmwareVersion versionCC110;

    public RileyLinkTargetDevice targetDevice;

    // Medtronic Pump
    public String pumpID;
    public byte[] pumpIDBytes;

    @Inject
    public MedLinkServiceData() {}

    public void setPumpID(String pumpId, byte[] pumpIdBytes) {
        this.pumpID = pumpId;
        this.pumpIDBytes = pumpIdBytes;
    }

    public void setRileyLinkServiceState(RileyLinkServiceState newState) {
        setServiceState(newState, null);
    }

    public RileyLinkServiceState getRileyLinkServiceState() {
        return workWithServiceState(null, null, false);
    }


    public void setServiceState(RileyLinkServiceState newState, RileyLinkError errorCode) {
        workWithServiceState(newState, errorCode, true);
    }


    private synchronized RileyLinkServiceState workWithServiceState(RileyLinkServiceState newState, RileyLinkError errorCode, boolean set) {

        if (set) {

            rileyLinkServiceState = newState;
            this.rileyLinkError = errorCode;

            aapsLogger.info(LTag.PUMP, "MedLink State Changed: {} {}", newState, errorCode == null ? "" : " - Error State: " + errorCode.name());

            medLinkUtil.getRileyLinkHistory().add(new MLHistoryItem(rileyLinkServiceState, errorCode, targetDevice));
            rxBus.send(new EventRileyLinkDeviceStatusChange(newState, errorCode));

            return null;

        } else {
            return rileyLinkServiceState;
        }

    }

}
