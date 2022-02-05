package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs

import info.nightscout.androidaps.plugins.pump.common.hw.connector.defs.CommunicatorPumpDevice
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.queue.Callback

/**
 * Created by Dirceu on 30/09/20.
 */
interface MedLinkPumpDevice : CommunicatorPumpDevice {

    fun getPumpInfo(): RileyLinkPumpInfo?
    fun getRileyLinkService(): MedLinkService?

    fun getBatteryInfoConfig(): String?
    fun setBusy(busy: Boolean)
    override fun triggerPumpConfigurationChangedEvent()

    // var medLinkService: MedLinkService?
    val lastConnectionTimeMillis: Long
    fun setLastCommunicationToNow()
    fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo, func: (PumpEnactResult) -> Unit)
    fun setTempBasalPercent(percent: Int,
                            durationInMinutes: Int,
                            profile: Profile,
                            enforceNew: Boolean,
                            func: Function1<PumpEnactResult, *>): PumpEnactResult
    fun setTempBasalAbsolute(
        absoluteRate: Double,
        durationInMinutes: Int,
        profile: Profile,
        enforceNew: Boolean,
        callback: Function1<PumpEnactResult, *>
    ): PumpEnactResult

    fun cancelTempBasal(enforceNew: Boolean, callback: Callback?)
    fun extendBasalTreatment(duration: Int, callback: Function1<PumpEnactResult, *>): PumpEnactResult
    fun nextScheduledCommand(): String? //    void deliverTreatment(DetailedBolusInfo detailedBolusInfo,
    //                          @NotNull Function<PumpEnactResult, Unit> func);





}