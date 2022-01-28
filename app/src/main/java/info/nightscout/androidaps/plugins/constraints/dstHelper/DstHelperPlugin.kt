package info.nightscout.androidaps.plugins.constraints.dstHelper

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DstHelperPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    rh: ResourceHelper,
    private val sp: SP,
    private val activePlugin: ActivePlugin,
    private val loop: Loop
) : PluginBase(PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .neverVisible(true)
    .alwaysEnabled(true)
    .showInList(false)
    .pluginName(R.string.dst_plugin_name),
    aapsLogger, rh, injector
), Constraints {

    companion object {
        private const val DISABLE_TIME_FRAME_HOURS = -3
        private const val WARN_PRIOR_TIME_FRAME_HOURS = 12
    }

    //Return false if time to DST change happened in the last 3 hours.
    @Suppress("ReplaceGetOrSet")
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val pump = activePlugin.activePump
        if (pump.canHandleDST()) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Pump can handle DST")
            return value
        }
        val cal = Calendar.getInstance()
        if (willBeDST(cal)) {
            val snoozedTo: Long = sp.getLong(R.string.key_snooze_dst_in24h, 0L)
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                val notification = NotificationWithAction(injector, Notification.DST_IN_24H, rh.gs(R.string.dst_in_24h_warning), Notification.LOW)
                notification.action(R.string.snooze) {
                    sp.putLong(R.string.key_snooze_dst_in24h, System.currentTimeMillis() + T.hours(24).msecs())
                }
                rxBus.send(EventNewNotification(notification))
            }
        }
        if (!value.value()) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Already not allowed - don't check further")
            return value
        }
        if (wasDST(cal)) {
            if (!loop.isSuspended) {
                val snoozedTo: Long = sp.getLong(R.string.key_snooze_loopdisabled, 0L)
                if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                    val notification = NotificationWithAction(injector, Notification.DST_LOOP_DISABLED, rh.gs(R.string.dst_loop_disabled_warning), Notification.LOW)
                    notification.action(R.string.snooze) {
                        sp.putLong(R.string.key_snooze_loopdisabled, System.currentTimeMillis() + T.hours(24).msecs())
                    }
                    rxBus.send(EventNewNotification(notification))
                }
            } else {
                aapsLogger.debug(LTag.CONSTRAINTS, "Loop already suspended")
            }
            value.set(aapsLogger, false, "DST in last 3 hours.", this)
        }
        return value
    }

    fun wasDST(now: Calendar): Boolean {
        val ago = now.clone() as Calendar
        ago.add(Calendar.HOUR, DISABLE_TIME_FRAME_HOURS)
        return now[Calendar.DST_OFFSET] != ago[Calendar.DST_OFFSET]
    }

    fun willBeDST(now: Calendar): Boolean {
        val ago = now.clone() as Calendar
        ago.add(Calendar.HOUR, WARN_PRIOR_TIME_FRAME_HOURS)
        return now[Calendar.DST_OFFSET] != ago[Calendar.DST_OFFSET]
    }
}