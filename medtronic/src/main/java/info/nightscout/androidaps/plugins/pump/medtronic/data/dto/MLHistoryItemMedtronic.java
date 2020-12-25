package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.data.MLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by Dirceu on 25/09/20
 * copied from {@link RLHistoryItemMedtronic}
 */
public class MLHistoryItemMedtronic extends MLHistoryItem {

    private MedLinkCommandType medtronicCommandType;

    public MLHistoryItemMedtronic(MedLinkCommandType medtronicCommandType) {
        super(new LocalDateTime(), MLHistoryItemSource.MedtronicCommand, RileyLinkTargetDevice.MedtronicPump);
        this.medtronicCommandType = medtronicCommandType;
    }

    public String getDescription(ResourceHelper resourceHelper) {

        switch (this.source) {
            case MedLink:
                return "State: " + resourceHelper.gs(serviceState.getResourceId())
                        + (this.errorCode == null ? "" : ", Error Code: " + errorCode);

            case MedtronicPump:
                return resourceHelper.gs(pumpDeviceState.getResourceId());

            case MedtronicCommand:
                return medtronicCommandType.name();

            default:
                return "Unknown Description";
        }
    }



}
