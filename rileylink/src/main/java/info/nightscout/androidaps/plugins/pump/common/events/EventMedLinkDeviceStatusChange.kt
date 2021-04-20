package info.nightscout.androidaps.plugins.pump.common.events

import info.nightscout.androidaps.events.EventStatus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.utils.resources.ResourceHelper

open class EventMedLinkDeviceStatusChange : EventStatus {

    var rileyLinkTargetDevice: RileyLinkTargetDevice? = null
    var medLinkServiceState: MedLinkServiceState? = null
    var medLinkError: MedLinkError? = null

    var pumpDeviceState: PumpDeviceState? = null
    var errorDescription: String? = null

    constructor()

    constructor(rileyLinkTargetDevice: RileyLinkTargetDevice,
                medLinkServiceState: MedLinkServiceState?, medLinkError: MedLinkError?) {
        this.rileyLinkTargetDevice = rileyLinkTargetDevice
        this.medLinkServiceState = medLinkServiceState
        this.medLinkError = medLinkError
    }

    constructor(pumpDeviceState: PumpDeviceState?) {
        this.pumpDeviceState = pumpDeviceState
    }

    constructor(pumpDeviceState: PumpDeviceState?, errorDescription: String?) {
        this.pumpDeviceState = pumpDeviceState
        this.errorDescription = errorDescription
    }

    override fun getStatus(resourceHelper: ResourceHelper): String {
        val medLinkServiceState = this.medLinkServiceState ?: return ""
        val resourceId = medLinkServiceState.resourceId
        val rileyLinkError = this.medLinkError

        if (medLinkServiceState.isError && rileyLinkError != null) {
            val rileyLinkTargetDevice = this.rileyLinkTargetDevice ?: return ""
            return resourceHelper.gs(rileyLinkError.getResourceId(rileyLinkTargetDevice))
        }

        return resourceHelper.gs(resourceId)
    }
}
