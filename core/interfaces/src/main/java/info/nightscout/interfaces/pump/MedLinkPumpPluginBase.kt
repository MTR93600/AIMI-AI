package info.nightscout.interfaces.pump

import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.queue.Callback

interface MedLinkPumpPluginBase {

    // override fun onStart() {
    //     super.onStart()
    //     if (getType() == PluginType.PUMP) {
    //         Thread {
    //             SystemClock.sleep(3000)
    //             commandQueue.readStatus(rh.gs(R.string.pump_driver_changed), null)
    //         }.start()
    //     }
    // }

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
    )
 fun cancelTempBasal(enforceNew: Boolean, callback: Callback?): PumpEnactResult

  fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo, func: (PumpEnactResult) -> Unit)
    fun calibrate(bg: Double)

}