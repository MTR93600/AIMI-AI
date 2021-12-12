package info.nightscout.androidaps.utils

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Created by adrian on 17/12/17.
 */
@Singleton
class LocalAlertUtils @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val smsCommunicatorPlugin: SmsCommunicatorPlugin,
    private val config: Config,
    private val repository: AppRepository,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger
) {

    private val disposable = CompositeDisposable()

    private fun missedReadingsThreshold(): Long {
        return T.mins(sp.getInt(R.string.key_missed_bg_readings_threshold_minutes, Constants.DEFAULT_MISSED_BG_READINGS_THRESHOLD_MINUTES).toLong()).msecs()
    }

    private fun pumpUnreachableThreshold(): Long {
        return T.mins(sp.getInt(R.string.key_pump_unreachable_threshold_minutes, Constants.DEFAULT_PUMP_UNREACHABLE_THRESHOLD_MINUTES).toLong()).msecs()
    }

    fun checkPumpUnreachableAlarm(lastConnection: Long, isStatusOutdated: Boolean, isDisconnected: Boolean) {
        val alarmTimeoutExpired = isAlarmTimeoutExpired(lastConnection, pumpUnreachableThreshold())
        val nextAlarmOccurrenceReached = sp.getLong("nextPumpDisconnectedAlarm", 0L) < System.currentTimeMillis()
        if (config.APS && isStatusOutdated && alarmTimeoutExpired && nextAlarmOccurrenceReached && !isDisconnected) {
            if (sp.getBoolean(R.string.key_enable_pump_unreachable_alert, true)) {
                aapsLogger.debug(LTag.CORE, "Generating pump unreachable alarm. lastConnection: " + dateUtil.dateAndTimeString(lastConnection) + " isStatusOutdated: " + isStatusOutdated)
                sp.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + pumpUnreachableThreshold())
                rxBus.send(EventNewNotification(Notification(Notification.PUMP_UNREACHABLE, rh.gs(R.string.pump_unreachable), Notification.URGENT).also { it.soundId = R.raw.alarm }))
                uel.log(Action.CAREPORTAL, Sources.Aaps, rh.gs(R.string.pump_unreachable), ValueWithUnit.TherapyEventType(TherapyEvent.Type.ANNOUNCEMENT))
                if (sp.getBoolean(R.string.key_ns_create_announcements_from_errors, true))
                    disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(rh.gs(R.string.pump_unreachable))).subscribe()
            }
            if (sp.getBoolean(R.string.key_smscommunicator_report_pump_ureachable, true))
                smsCommunicatorPlugin.sendNotificationToAllNumbers(rh.gs(R.string.pump_unreachable))
        }
        if (!isStatusOutdated && !alarmTimeoutExpired) rxBus.send(EventDismissNotification(Notification.PUMP_UNREACHABLE))
    }

    private fun isAlarmTimeoutExpired(lastConnection: Long, unreachableThreshold: Long): Boolean {
        return if (activePlugin.activePump.pumpDescription.hasCustomUnreachableAlertCheck) {
            activePlugin.activePump.isUnreachableAlertTimeoutExceeded(unreachableThreshold)
        } else {
            lastConnection + pumpUnreachableThreshold() < System.currentTimeMillis()
        }
    }

    /*Pre-snoozes the alarms with 5 minutes if no snooze exists.
     * Call only at startup!
     */
    fun preSnoozeAlarms() {
        if (sp.getLong("nextMissedReadingsAlarm", 0L) < System.currentTimeMillis()) {
            sp.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + 5 * 60 * 1000)
        }
        if (sp.getLong("nextPumpDisconnectedAlarm", 0L) < System.currentTimeMillis()) {
            sp.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + 5 * 60 * 1000)
        }
    }

    fun shortenSnoozeInterval() { //shortens alarm times in case of setting changes or future data
        var nextMissedReadingsAlarm = sp.getLong("nextMissedReadingsAlarm", 0L)
        nextMissedReadingsAlarm = min(System.currentTimeMillis() + missedReadingsThreshold(), nextMissedReadingsAlarm)
        sp.putLong("nextMissedReadingsAlarm", nextMissedReadingsAlarm)
        var nextPumpDisconnectedAlarm = sp.getLong("nextPumpDisconnectedAlarm", 0L)
        nextPumpDisconnectedAlarm = min(System.currentTimeMillis() + pumpUnreachableThreshold(), nextPumpDisconnectedAlarm)
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
        val bgReadingWrapped = repository.getLastGlucoseValueWrapped().blockingGet()
        val bgReading = if (bgReadingWrapped is ValueWrapper.Existing) bgReadingWrapped.value else return
        if (sp.getBoolean(R.string.key_enable_missed_bg_readings_alert, false)
            && bgReading.timestamp + missedReadingsThreshold() < System.currentTimeMillis()
            && sp.getLong("nextMissedReadingsAlarm", 0L) < System.currentTimeMillis()
        ) {
            val n = Notification(Notification.BG_READINGS_MISSED, rh.gs(R.string.missed_bg_readings), Notification.URGENT)
            n.soundId = R.raw.alarm
            sp.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + missedReadingsThreshold())
            rxBus.send(EventNewNotification(n))
            uel.log(Action.CAREPORTAL, Sources.Aaps, rh.gs(R.string.missed_bg_readings), ValueWithUnit.TherapyEventType(TherapyEvent.Type.ANNOUNCEMENT))
            if (sp.getBoolean(R.string.key_ns_create_announcements_from_errors, true)) {
                n.text?.let { disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(it)).subscribe() }
            }
        } else if (dateUtil.isOlderThan(bgReading.timestamp, 5).not()) {
            rxBus.send(EventDismissNotification(Notification.BG_READINGS_MISSED))
        }
    }
}