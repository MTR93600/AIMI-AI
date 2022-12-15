package info.nightscout.plugins.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.HandlerThread
import android.widget.TextView
import androidx.annotation.StringRes
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.WarnColors
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.stats.TddCalculator
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.plugins.R
import info.nightscout.shared.extensions.runOnUiThread
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusLightHandler @Inject constructor(
    private val rh: ResourceHelper,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val warnColors: WarnColors,
    private val config: Config,
    private val repository: AppRepository,
    private val tddCalculator: TddCalculator
) {
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    /**
     * applies the extended statusLight subview on the overview fragment
     */
    fun updateStatusLights(
        cannulaAge: TextView?,
        cannulaUsage: TextView?,
        insulinAge: TextView?,
        reservoirLevel: TextView?,
        sensorAge: TextView?,
        sensorBatteryLevel: TextView?,
        batteryAge: TextView?,
        batteryLevel: TextView?
    ) {
        val pump = activePlugin.activePump
        val bgSource = activePlugin.activeBgSource
        handleAge(cannulaAge, TherapyEvent.Type.CANNULA_CHANGE, info.nightscout.core.utils.R.string.key_statuslights_cage_warning, 48.0, info.nightscout.core.utils.R.string.key_statuslights_cage_critical, 72.0)
        handleAge(insulinAge, TherapyEvent.Type.INSULIN_CHANGE, info.nightscout.core.utils.R.string.key_statuslights_iage_warning, 72.0, info.nightscout.core.utils.R.string.key_statuslights_iage_critical, 144.0)
        handleAge(sensorAge, TherapyEvent.Type.SENSOR_CHANGE, info.nightscout.core.utils.R.string.key_statuslights_sage_warning, 216.0, info.nightscout.core.utils.R.string.key_statuslights_sage_critical, 240.0)
        if (pump.pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled()) {
            handleAge(batteryAge, TherapyEvent.Type.PUMP_BATTERY_CHANGE, info.nightscout.core.utils.R.string.key_statuslights_bage_warning, 216.0, info.nightscout.core.utils.R.string.key_statuslights_bage_critical, 240.0)
        }

        val insulinUnit = rh.gs(info.nightscout.core.ui.R.string.insulin_unit_shortname)
        if (pump.pumpDescription.isPatchPump) {
            handlePatchReservoirLevel(
                reservoirLevel,
                R.string.key_statuslights_res_critical,
                10.0,
                R.string.key_statuslights_res_warning,
                80.0,
                pump.reservoirLevel,
                insulinUnit,
                pump.pumpDescription.maxResorvoirReading.toDouble()
            )
        } else {
            if (cannulaUsage != null) handleUsage(cannulaUsage, insulinUnit)
            handleLevel(reservoirLevel, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel, insulinUnit)
        }
        if (!config.NSCLIENT) {
            if (bgSource.sensorBatteryLevel != -1)
                handleLevel(sensorBatteryLevel, R.string.key_statuslights_sbat_critical, 5.0, R.string.key_statuslights_sbat_warning, 20.0, bgSource.sensorBatteryLevel.toDouble(), "%")
            else
                sensorBatteryLevel?.text = ""
        }

        if (!config.NSCLIENT) {
            // The Omnipod Eros does not report its battery level. However, some RileyLink alternatives do.
            // Depending on the user's configuration, we will either show the battery level reported by the RileyLink or "n/a"
            // Pump instance check is needed because at startup, the pump can still be VirtualPumpPlugin and that will cause a crash
            val erosBatteryLinkAvailable = pump.model() == PumpType.OMNIPOD_EROS && pump.isUseRileyLinkBatteryLevel()

            if (pump.model().supportBatteryLevel || erosBatteryLinkAvailable) {
                handleLevel(batteryLevel, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.batteryLevel.toDouble(), "%")
            } else {
                batteryLevel?.text = rh.gs(info.nightscout.core.ui.R.string.value_unavailable_short)
                batteryLevel?.setTextColor(rh.gac(batteryLevel.context, info.nightscout.core.ui.R.attr.defaultTextColor))
            }
        }
    }

    private fun handleAge(view: TextView?, type: TherapyEvent.Type, @StringRes warnSettings: Int, defaultWarnThreshold: Double, @StringRes urgentSettings: Int, defaultUrgentThreshold: Double) {
        val warn = sp.getDouble(warnSettings, defaultWarnThreshold)
        val urgent = sp.getDouble(urgentSettings, defaultUrgentThreshold)
        val therapyEvent = repository.getLastTherapyRecordUpToNow(type).blockingGet()
        if (therapyEvent is ValueWrapper.Existing) {
            warnColors.setColorByAge(view, therapyEvent.value, warn, urgent)
            view?.text = therapyEvent.value.age(rh.shortTextMode(), rh, dateUtil)
        } else {
            view?.text = if (rh.shortTextMode()) "-" else rh.gs(info.nightscout.core.ui.R.string.value_unavailable_short)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String) {
        val resUrgent = sp.getDouble(criticalSetting, criticalDefaultValue)
        val resWarn = sp.getDouble(warnSetting, warnDefaultValue)
        if (level > 0) view?.text = " " + DecimalFormatter.to0Decimal(level, units)
        else view?.text = ""
        warnColors.setColorInverse(view, level, resWarn, resUrgent)
    }

    // Omnipod only reports reservoir level when it's 50 units or less, so we display "50+U" for any value > 50
    @Suppress("SameParameterValue")
    private fun handlePatchReservoirLevel(
        view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int,
        warnDefaultValue: Double, level: Double, units: String, maxReading: Double
    ) {
        if (level >= maxReading) {
            view?.text = DecimalFormatter.to0Decimal(maxReading, units)
            view?.setTextColor(rh.gac(view.context, info.nightscout.core.ui.R.attr.defaultTextColor))
        } else {
            handleLevel(view, criticalSetting, criticalDefaultValue, warnSetting, warnDefaultValue, level, units)
        }
    }

    private fun handleUsage(view: TextView?, units: String) {
        handler.post {
            val therapyEvent = repository.getLastTherapyRecordUpToNow(TherapyEvent.Type.CANNULA_CHANGE).blockingGet()
            var usage = 0.0
            if (therapyEvent is ValueWrapper.Existing) {
                val tdd = tddCalculator.calculate(therapyEvent.value.timestamp, dateUtil.now())
                usage = tdd.totalAmount
            }
            runOnUiThread {
                view?.text = DecimalFormatter.to0Decimal(usage, units)
            }
        }
    }
    private fun TherapyEvent.age(useShortText: Boolean, rh: ResourceHelper, dateUtil: DateUtil): String {
        val diff = dateUtil.computeDiff(timestamp, System.currentTimeMillis())
        var days = " " + rh.gs(info.nightscout.shared.R.string.days) + " "
        var hours = " " + rh.gs(info.nightscout.shared.R.string.hours) + " "
        if (useShortText) {
            days = rh.gs(info.nightscout.shared.R.string.shortday)
            hours = rh.gs(info.nightscout.shared.R.string.shorthour)
        }
        return diff[TimeUnit.DAYS].toString() + days + diff[TimeUnit.HOURS] + hours
    }
}
