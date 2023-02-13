package info.nightscout.androidaps.plugins.pump.common.hw.medlink.service;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.common.events.EventMedLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.data.MLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;


/**
 * Created by Dirceu on 24/09/2020.
 * copied from RileyLinkServiceData
 */

@Singleton
public class MedLinkServiceData {

    public Integer batteryLevel=0;
    @Inject AAPSLogger aapsLogger;
    @Inject MedLinkUtil medLinkUtil;
    @Inject RxBus rxBus;

    boolean tuneUpDone = false;
    public MedLinkError medLinkError;
    public MedLinkServiceState medLinkServiceState = MedLinkServiceState.NotStarted;
    public RileyLinkFirmwareVersion firmwareVersion;
    public RileyLinkTargetFrequency rileyLinkTargetFrequency;

    public String rileylinkAddress;
    public String rileylinkName;
    long lastTuneUpTime = 0L;
    public Double lastGoodFrequency;

    // bt version
    public String versionBLE113;
    // radio version
    public String versionCC110;

    // Medtronic Pump
    public String pumpID;
    public byte[] pumpIDBytes;

    public RileyLinkTargetDevice targetDevice;

    @Inject
    public MedLinkServiceData() {}

    public void setPumpID(String pumpId, byte[] pumpIdBytes) {
        this.pumpID = pumpId;
        this.pumpIDBytes = pumpIdBytes;
    }

    public void setMedLinkServiceState(MedLinkServiceState newState) {
        setServiceState(newState, null);
    }

    public MedLinkServiceState getMedLinkServiceState() {
        return workWithServiceState(null, null, false);
    }


    public void setServiceState(MedLinkServiceState newState, MedLinkError errorCode) {
        workWithServiceState(newState, errorCode, true);
    }


    private synchronized MedLinkServiceState workWithServiceState(MedLinkServiceState newState,
                                                                  MedLinkError errorCode, boolean set) {

        if (set) {

            medLinkServiceState = newState;
            this.medLinkError = errorCode;

            aapsLogger.info(LTag.PUMP, "MedLink State Changed: {} {}", newState, errorCode == null ? "" : " - Error State: " + errorCode.name());

            medLinkUtil.getRileyLinkHistory().add(new MLHistoryItem(medLinkServiceState, errorCode,
                    targetDevice));
            rxBus.send(new EventMedLinkDeviceStatusChange(targetDevice, newState, errorCode));


            return null;

        } else {
            if(MedLinkServiceState.PumpConnectorReady == newState){

            }
            return medLinkServiceState;
        }

    }

}
