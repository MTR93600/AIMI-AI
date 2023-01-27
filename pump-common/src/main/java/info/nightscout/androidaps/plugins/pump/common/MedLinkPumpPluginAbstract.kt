package info.nightscout.androidaps.plugins.pump.common

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.common.sync.PumpSyncStorage
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP

/**
 * Created by Dirceu on 06/04/21.
 */
abstract class MedLinkPumpPluginAbstract protected constructor(
    pluginDescription: PluginDescription, pumpType: PumpType?,
    injector: HasAndroidInjector, resourceHelper: ResourceHelper?, aapsLogger: AAPSLogger?, commandQueue: CommandQueue?, rxBus: RxBus?,
    activePlugin: ActivePlugin?,
    sp: SP?, context: Context?,
    fabricPrivacy: FabricPrivacy?,
    dateUtil: DateUtil?,
    aapsSchedulers: AapsSchedulers?,
    pumpSync: PumpSync?,
    pumpSyncStorage: PumpSyncStorage?
) : PumpPluginAbstract(
    pluginDescription, pumpType!!, injector,
    resourceHelper!!,
    aapsLogger!!,
    commandQueue!!,
    rxBus!!,
    activePlugin!!,
    sp!!,
    context!!,
    fabricPrivacy!!,
    dateUtil!!,
    aapsSchedulers!!,
    pumpSync!!,
    pumpSyncStorage!!
) {

    abstract fun handleBolusDelivered(lastBolusInfo: DetailedBolusInfo?)
    abstract val temporaryBasal: PumpSync.PumpState.TemporaryBasal?
    fun deliverTreatment(
        detailedBolusInfo: DetailedBolusInfo,
        func: (PumpEnactResult) -> Unit
    ) {
        try {
            if (detailedBolusInfo.insulin == 0.0 && detailedBolusInfo.carbs == 0.0) {
                // neither carbs nor bolus requested
                aapsLogger.error("deliverTreatment: Invalid input")
                func.invoke(
                    PumpEnactResult(injector).success(false).enacted(false).bolusDelivered(0.0).carbsDelivered(0.0)
                        .comment(rh.gs(R.string.invalidinput))
                )
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                deliverBolus(detailedBolusInfo, func)
            } else {
                //if (MedtronicHistoryData.doubleBolusDebug)
                //    aapsLogger.debug("DoubleBolusDebug: deliverTreatment::(carb only entry)");

                // no bolus required, carb only treatment
                pumpSyncStorage.addBolusWithTempId(detailedBolusInfo, true, this)
                val bolusingEvent = EventOverviewBolusProgress
                bolusingEvent.t = EventOverviewBolusProgress.Treatment(detailedBolusInfo.insulin, detailedBolusInfo.carbs.toInt(), detailedBolusInfo.bolusType == DetailedBolusInfo.BolusType.SMB,
                                                                       detailedBolusInfo.id)
                bolusingEvent.percent = 100
                rxBus.send(bolusingEvent)

                aapsLogger.debug(LTag.PUMP, "deliverTreatment: Carb only treatment.")
                func.invoke(
                    PumpEnactResult(injector).success(true).enacted(true).bolusDelivered(0.0).carbsDelivered(detailedBolusInfo.carbs)
                        .comment(rh.gs(R.string.common_resultok))
                )
            }
        } finally {
            triggerUIChange()
        }
    }

    protected abstract fun deliverBolus(
        detailedBolusInfo: DetailedBolusInfo,
        func: (PumpEnactResult) -> Unit
    )

    abstract fun storeCancelTempBasal()
    abstract fun setPumpDeviceState(state:PumpDeviceState)
    abstract fun postInit()
    abstract fun setMedtronicPumpModel(model:String)
    abstract fun setBatteryLevel(batteryLevel:Int)
    abstract fun getBatteryType(): String
    abstract fun changeStatusTime(currentTimeMillis: Long)
}