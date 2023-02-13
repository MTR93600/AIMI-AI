package info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs

import info.nightscout.androidaps.plugins.pump.common.hw.connector.defs.CommunicatorPumpDevice
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import org.json.JSONObject
import java.util.stream.Stream

/**
 * Created by Dirceu on 30/09/20.
 */
interface MedLinkPumpDevice : CommunicatorPumpDevice {

    fun handleNewTreatmentData(bolusInfo: Stream<JSONObject>, history: Boolean = false)
    fun getPumpInfo(): RileyLinkPumpInfo?
    fun getRileyLinkService(): MedLinkService?

    fun getBatteryInfoConfig(): String?
    fun setBusy(busy: Boolean)
    override fun triggerPumpConfigurationChangedEvent()

    // var medLinkService: MedLinkService?
    val lastConnectionTimeMillis: Long
    fun setLastCommunicationToNow()
    fun extendBasalTreatment(duration: Int, callback: Function1<PumpEnactResult, *>): PumpEnactResult
    fun nextScheduledCommand(): String? //    void deliverTreatment(DetailedBolusInfo detailedBolusInfo,
    //                          @NotNull Function<PumpEnactResult, Unit> func);





}