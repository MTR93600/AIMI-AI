package info.nightscout.androidaps.plugins.pump.common.events

import android.content.Context
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkError
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.pump.core.defs.PumpDeviceState
import info.nightscout.rx.events.EventStatus
import info.nightscout.shared.interfaces.ResourceHelper

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

    override fun getStatus(context: Context): String {
        val medLinkServiceState = this.medLinkServiceState ?: return ""
        val resourceId = medLinkServiceState.resourceId
        val medLinkError = this.medLinkError

        if (medLinkServiceState.isError && medLinkError != null) {
            val rileyLinkTargetDevice = this.rileyLinkTargetDevice ?: return ""
            return context.getString(medLinkError.getResourceId(rileyLinkTargetDevice))
        }

        return context.getString(resourceId)
    }
}
