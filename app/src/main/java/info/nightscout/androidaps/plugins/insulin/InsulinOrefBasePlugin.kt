package info.nightscout.androidaps.plugins.insulin

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.interfaces.ResourceHelper
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Created by adrian on 13.08.2017.
 *
 * parameters are injected from child class
 *
 */
abstract class InsulinOrefBasePlugin(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    val profileFunction: ProfileFunction,
    val rxBus: RxBus,
    aapsLogger: AAPSLogger,
    config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.INSULIN)
        .fragmentClass(InsulinFragment::class.java.name)
        .pluginIcon(R.drawable.ic_insulin)
        .shortName(R.string.insulin_shortname)
        .visibleByDefault(false)
        .neverVisible(config.NSCLIENT),
    aapsLogger, rh, injector
), Insulin {

    private var lastWarned: Long = 0
    override val dia
        get(): Double {
            val dia = userDefinedDia
            return if (dia >= MIN_DIA) {
                dia
            } else {
                sendShortDiaNotification(dia)
                MIN_DIA
            }
        }

    open fun sendShortDiaNotification(dia: Double) {
        if (System.currentTimeMillis() - lastWarned > 60 * 1000) {
            lastWarned = System.currentTimeMillis()
            val notification = Notification(Notification.SHORT_DIA, String.format(notificationPattern, dia, MIN_DIA), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        }
    }

    private val notificationPattern: String
        get() = rh.gs(R.string.dia_too_short)

    open val userDefinedDia: Double
        get() {
            val profile = profileFunction.getProfile()
            return profile?.dia ?: MIN_DIA
        }

    override fun iobCalcForTreatment(bolus: Bolus, time: Long, dia: Double): Iob {
        val result = Iob()
        val peak = peak
        if (bolus.amount != 0.0) {
            val now = System.currentTimeMillis() / (1000*60*60)

            var circadian_sensitivity = 1.0
            when (now) {
                in 0..1   -> {
                    //circadian_sensitivity = 1.4;
                    circadian_sensitivity = (0.09130*Math.pow(now.toDouble(),3.0))-(0.33261*Math.pow(now.toDouble(),2.0))+1.4
                }
                in 2..2   -> {
                    //circadian_sensitivity = 0.8;
                    circadian_sensitivity = (0.0869*Math.pow(now.toDouble(),3.0))-(0.05217*Math.pow(now.toDouble(),2.0))-(0.23478*now)+0.8
                }
                in 3..7   -> {
                    //circadian_sensitivity = 0.8;
                    circadian_sensitivity = (0.0007*Math.pow(now.toDouble(),3.0))-(0.000730*Math.pow(now.toDouble(),2.0))-(0.0007826*now)+0.6
                }
                in 8..10  -> {
                    //circadian_sensitivity = 0.6;
                    circadian_sensitivity = (0.001244*Math.pow(now.toDouble(),3.0))-(0.007619*Math.pow(now.toDouble(),2.0))-(0.007826*now)+0.4
                }
                in 11..14 -> {
                    //circadian_sensitivity = 0.8;
                    circadian_sensitivity = (0.00078*Math.pow(now.toDouble(),3.0))-(0.00272*Math.pow(now.toDouble(),2.0))-(0.07619*now)+0.8
                }
                in 15..22 -> {
                    circadian_sensitivity = 1.0
                }
                in 22..24 -> {
                    //circadian_sensitivity = 1.2;
                    circadian_sensitivity = (0.000125*Math.pow(now.toDouble(),3.0))-(0.0015*Math.pow(now.toDouble(),2.0))-(0.0045*now)+1
                }
            }
            val factordia = (ln(bolus.amount) * 1.618)

            val bolusTime = bolus.timestamp
            val t = (time - bolusTime) / 1000.0 / 60.0
            val td = (dia * 60.0 * factordia * circadian_sensitivity).coerceAtLeast(2.5 * 60.0) //getDIA() always >= MIN_DIA
            //val td = dia * 60 * factordia //getDIA() always >= MIN_DIA
            val tp = circadian_sensitivity * peak.toDouble()
            // force the IOB to 0 if over DIA hours have passed
            if (t < td) {
                val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
                val a = 2 * tau / td
                val S = 1 / (1 - a + (1 + a) * exp(-td / tau))
                result.activityContrib = bolus.amount * (S / tau.pow(2.0)) * t * (1 - t / td) * exp(-t / tau)
                result.iobContrib = bolus.amount * (1 - S * (1 - a) * ((t.pow(2.0) / (tau * td * (1 - a)) - t / tau - 1) * Math.exp(-t / tau) + 1))
            }
        }
        return result
    }

    override val insulinConfiguration: InsulinConfiguration
        get() = InsulinConfiguration(friendlyName, (dia *  1000.0 * 3600.0).toLong(), T.mins(peak.toLong()).msecs())

    override val comment
        get(): String {
            var comment = commentStandardText()
            val userDia = userDefinedDia
            if (userDia < MIN_DIA) {
                comment += "\n" + rh.gs(R.string.dia_too_short, userDia, MIN_DIA)
            }
            return comment
        }

    override abstract val peak: Int
    abstract fun commentStandardText(): String

    companion object {

        const val MIN_DIA = 2.5
    }
}