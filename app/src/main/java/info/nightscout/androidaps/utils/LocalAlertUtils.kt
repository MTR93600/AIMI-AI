package info.nightscout.androidaps.utils

import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 17/12/17.
 */
@Singleton
class LocalAlertUtils @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val activePlugin: ActivePluginProvider,
    private val profileFunction: ProfileFunction,
    private val iobCobCalculatorPlugin: IobCobCalculatorPlugin,
    private val config: Config,
    private val nsUpload: NSUpload,
    private val dateUtil: DateUtil
) {

    fun missedReadingsThreshold(): Long {
        return T.mins(sp.getInt(resourceHelper.gs(R.string.key_missed_bg_readings_threshold), 30).toLong()).msecs()
    }

    private fun pumpUnreachableThreshold(): Long {
        return T.mins(sp.getInt(resourceHelper.gs(R.string.key_pump_unreachable_threshold), 30).toLong()).msecs()
    }

    fun checkPumpUnreachableAlarm(lastConnection: Long, isStatusOutdated: Boolean, isDisconnected: Boolean) {
        val alarmTimeoutExpired = lastConnection + pumpUnreachableThreshold() < System.currentTimeMillis()
        val nextAlarmOccurrenceReached = sp.getLong("nextPumpDisconnectedAlarm", 0L) < System.currentTimeMillis()
        if (config.APS && sp.getBoolean(resourceHelper.gs(R.string.key_enable_pump_unreachable_alert), true)
            && isStatusOutdated && alarmTimeoutExpired && nextAlarmOccurrenceReached && !isDisconnected) {
            aapsLogger.debug(LTag.CORE, "Generating pump unreachable alarm. lastConnection: " + dateUtil.dateAndTimeString(lastConnection) + " isStatusOutdated: " + isStatusOutdated)
            val n = Notification(Notification.PUMP_UNREACHABLE, resourceHelper.gs(R.string.pump_unreachable), Notification.URGENT)
            n.soundId = R.raw.alarm
            sp.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + pumpUnreachableThreshold())
            rxBus.send(EventNewNotification(n))
            if (sp.getBoolean(R.string.key_ns_create_announcements_from_errors, true)) {
                nsUpload.uploadError(n.text)
            }
        }
        if (!isStatusOutdated && !alarmTimeoutExpired) rxBus.send(EventDismissNotification(Notification.PUMP_UNREACHABLE))
    }

    /*Presnoozes the alarms with 5 minutes if no snooze exists.
     * Call only at startup!
     */
    fun presnoozeAlarms() {
        if (sp.getLong("nextMissedReadingsAlarm", 0L) < System.currentTimeMillis()) {
            sp.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + 5 * 60 * 1000)
        }
        if (sp.getLong("nextPumpDisconnectedAlarm", 0L) < System.currentTimeMillis()) {
            sp.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + 5 * 60 * 1000)
        }
    }

    fun shortenSnoozeInterval() { //shortens alarm times in case of setting changes or future data
        var nextMissedReadingsAlarm = sp.getLong("nextMissedReadingsAlarm", 0L)
        nextMissedReadingsAlarm = Math.min(System.currentTimeMillis() + missedReadingsThreshold(), nextMissedReadingsAlarm)
        sp.putLong("nextMissedReadingsAlarm", nextMissedReadingsAlarm)
        var nextPumpDisconnectedAlarm = sp.getLong("nextPumpDisconnectedAlarm", 0L)
        nextPumpDisconnectedAlarm = Math.min(System.currentTimeMillis() + pumpUnreachableThreshold(), nextPumpDisconnectedAlarm)
        sp.putLong("nextPumpDisconnectedAlarm", nextPumpDisconnectedAlarm)
    }

    fun notifyPumpStatusRead() { //TODO: persist the actual time the pump is read and simplify the whole logic when to alarm
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        if (profile != null) {
            val lastConnection = pump.lastDataTime()
            val earliestAlarmTime = lastConnection + pumpUnreachableThreshold()
            if (sp.getLong("nextPumpDisconnectedAlarm", 0L) < earliestAlarmTime) {
                sp.putLong("nextPumpDisconnectedAlarm", earliestAlarmTime)
            }
        }
    }

    fun checkStaleBGAlert() {
        val bgReading: BgReading? = iobCobCalculatorPlugin.lastBg()
        if (sp.getBoolean(resourceHelper.gs(R.string.key_enable_missed_bg_readings_alert), false)
            && bgReading != null && bgReading.date + missedReadingsThreshold() < System.currentTimeMillis() && sp.getLong("nextMissedReadingsAlarm", 0L) < System.currentTimeMillis()) {
            val n = Notification(Notification.BG_READINGS_MISSED, resourceHelper.gs(R.string.missed_bg_readings), Notification.URGENT)
            n.soundId = R.raw.alarm
            sp.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + missedReadingsThreshold())
            rxBus.send(EventNewNotification(n))
            if (sp.getBoolean(R.string.key_ns_create_announcements_from_errors, true)) {
                nsUpload.uploadError(n.text)
            }
        }
    }
}