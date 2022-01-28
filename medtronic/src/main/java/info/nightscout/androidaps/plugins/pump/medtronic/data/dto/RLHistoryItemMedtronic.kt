package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.joda.time.LocalDateTime

class RLHistoryItemMedtronic(private val medtronicCommandType: MedtronicCommandType) :
    RLHistoryItem(LocalDateTime(), RLHistoryItemSource.MedtronicCommand, RileyLinkTargetDevice.MedtronicPump) {

    override fun getDescription(rh: ResourceHelper): String {
        return if (RLHistoryItemSource.MedtronicCommand == source) {
            medtronicCommandType.name
        } else super.getDescription(rh)
    }

}