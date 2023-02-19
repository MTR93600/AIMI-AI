package info.nightscout.androidaps.plugins.pump.common.hw.medlink.data;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.pump.core.defs.PumpDeviceState;
import info.nightscout.shared.interfaces.ResourceHelper;


/**
 * Created by dirceu on 9/25/20.
 * copied from HLHistoryItem
 */

public class MLHistoryItem  {

    //private MedtronicCommandType medtronicCommandType;
    protected LocalDateTime dateTime;
    protected MLHistoryItemSource source;
    protected MedLinkServiceState serviceState;
    protected MedLinkError errorCode;

    protected RileyLinkTargetDevice targetDevice;
    protected PumpDeviceState pumpDeviceState;
    //private OmnipodCommandType omnipodCommandType;

    public MLHistoryItem(LocalDateTime dateTime, MLHistoryItemSource source, RileyLinkTargetDevice targetDevice) {
        this.dateTime = dateTime;
        this.source = source;
        this.targetDevice = targetDevice;
    }

    public MLHistoryItem(MedLinkServiceState serviceState, MedLinkError errorCode,
                         RileyLinkTargetDevice targetDevice) {
        this.targetDevice = targetDevice;
        this.dateTime = new LocalDateTime();
        this.serviceState = serviceState;
        this.errorCode = errorCode;
        this.source = MLHistoryItemSource.MedLink;
    }


    public MLHistoryItem(PumpDeviceState pumpDeviceState, RileyLinkTargetDevice targetDevice) {
        this.pumpDeviceState = pumpDeviceState;
        this.dateTime = new LocalDateTime();
        this.targetDevice = targetDevice;
        this.source = MLHistoryItemSource.MedtronicPump;
    }


//    public RLHistoryItem(MedtronicCommandType medtronicCommandType) {
//        this.dateTime = new LocalDateTime();
//        this.medtronicCommandType = medtronicCommandType;
//        source = RLHistoryItemSource.MedtronicCommand;
//    }
//
//
//    public RLHistoryItem(OmnipodCommandType omnipodCommandType) {
//        this.dateTime = new LocalDateTime();
//        this.omnipodCommandType = omnipodCommandType;
//        source = RLHistoryItemSource.OmnipodCommand;
//    }


    public LocalDateTime getDateTime() {
        return dateTime;
    }


    public MedLinkServiceState getServiceState() {
        return serviceState;
    }


    public MedLinkError getErrorCode() {
        return errorCode;
    }


    public String getDescription(ResourceHelper resourceHelper) {

        switch (this.source) {
            case MedLink:
                return "State: " + resourceHelper.gs(serviceState.getResourceId())
                        + (this.errorCode == null ? "" : ", Error Code: " + errorCode);

            case MedtronicPump:
                return resourceHelper.gs(pumpDeviceState.getResourceId());

            default:
                return "Unknown Description";
        }
    }


    public MLHistoryItemSource getSource() {
        return source;
    }


    public PumpDeviceState getPumpDeviceState() {
        return pumpDeviceState;
    }

    public enum MLHistoryItemSource {
        MedLink("MedLink"), //
        MedtronicPump("Medtronic"), //
        MedtronicCommand("Medtronic");

        private final String desc;


        MLHistoryItemSource(String desc) {
            this.desc = desc;
        }


        public String getDesc() {
            return desc;
        }
    }

    public static class Comparator implements java.util.Comparator<MLHistoryItem> {

        @Override
        public int compare(MLHistoryItem o1, MLHistoryItem o2) {
            return o2.dateTime.compareTo(o1.getDateTime());
        }
    }

}
