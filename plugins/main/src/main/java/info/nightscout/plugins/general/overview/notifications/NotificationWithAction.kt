package info.nightscout.plugins.general.overview.notifications

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.nsclient.NSAlarm
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.plugins.R
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.T
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class NotificationWithAction constructor(
    injector: HasAndroidInjector
) : Notification() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var activePlugin: ActivePlugin

    init {
        injector.androidInjector().inject(this)
    }

    constructor(injector: HasAndroidInjector, id: Int, text: String, level: Int) : this(injector) {
        this.id = id
        date = System.currentTimeMillis()
        this.text = text
        this.level = level
    }

    constructor (injector: HasAndroidInjector, nsAlarm: NSAlarm) : this(injector) {
        date = System.currentTimeMillis()
        when (nsAlarm.level()) {
            0 -> {
                id = NS_ANNOUNCEMENT
                level = ANNOUNCEMENT
                text = nsAlarm.message()
                validTo = System.currentTimeMillis() + T.mins(60).msecs()
            }

            1 -> {
                id = NS_ALARM
                level = NORMAL
                text = nsAlarm.title()
                soundId = info.nightscout.core.ui.R.raw.alarm
            }

            2 -> {
                id = NS_URGENT_ALARM
                level = URGENT
                text = nsAlarm.title()
                soundId = R.raw.urgentalarm
            }
        }
        buttonText = info.nightscout.core.ui.R.string.snooze
        action = Runnable {
            activePlugin.activeNsClient?.handleClearAlarm(nsAlarm, 60 * 60 * 1000L)
            // Adding current time to snooze if we got staleData
            aapsLogger.debug(LTag.NOTIFICATION, "Notification text is: $text")
            val msToSnooze = sp.getInt(info.nightscout.core.utils.R.string.key_ns_alarm_stale_data_value, 15) * 60 * 1000L
            aapsLogger.debug(LTag.NOTIFICATION, "snooze nsalarm_staledatavalue in minutes is ${T.msecs(msToSnooze).mins()} currentTimeMillis is: ${System.currentTimeMillis()}")
            sp.putLong(rh.gs(info.nightscout.core.utils.R.string.key_snoozed_to) + nsAlarm.level(), System.currentTimeMillis() + msToSnooze)
        }
    }

    fun action(buttonText: Int, action: Runnable): NotificationWithAction {
        this.buttonText = buttonText
        this.action = action
        return this
    }

}