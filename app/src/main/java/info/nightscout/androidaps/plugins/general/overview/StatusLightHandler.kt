package info.nightscout.androidaps.plugins.general.overview

import android.graphics.Color
import android.widget.TextView
import androidx.annotation.StringRes
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.omnipod.eros.OmnipodErosPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.extensions.age
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusLightHandler @Inject constructor(
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val activePlugin: ActivePluginProvider,
    private val warnColors: WarnColors,
    private val config: Config,
    private val repository: AppRepository
) {

    /**
     * applies the extended statusLight subview on the overview fragment
     */
    fun updateStatusLights(careportal_cannula_age: TextView?, careportal_insulin_age: TextView?, careportal_reservoir_level: TextView?, careportal_sensor_age: TextView?, careportal_sensor_battery_level: TextView?, careportal_pb_age: TextView?, careportal_battery_level: TextView?,  colorNormal: Int, colorWarning: Int, colorAlarm: Int) {
        val pump = activePlugin.activePump
        val bgSource = activePlugin.activeBgSource
        handleAge(careportal_cannula_age, TherapyEvent.Type.CANNULA_CHANGE, R.string.key_statuslights_cage_warning, 48.0, R.string.key_statuslights_cage_critical, 72.0 , colorNormal, colorWarning, colorAlarm)
        handleAge(careportal_insulin_age, TherapyEvent.Type.INSULIN_CHANGE, R.string.key_statuslights_iage_warning, 72.0, R.string.key_statuslights_iage_critical, 144.0 , colorNormal, colorWarning, colorAlarm)
        handleAge(careportal_sensor_age, TherapyEvent.Type.SENSOR_CHANGE, R.string.key_statuslights_sage_warning, 216.0, R.string.key_statuslights_sage_critical, 240.0 , colorNormal, colorWarning, colorAlarm)
        if (pump.pumpDescription.isBatteryReplaceable) {
            handleAge(careportal_pb_age, TherapyEvent.Type.PUMP_BATTERY_CHANGE, R.string.key_statuslights_bage_warning, 216.0, R.string.key_statuslights_bage_critical, 240.0 , colorNormal, colorWarning, colorAlarm)
        }

        if (!config.NSCLIENT) {
            if (pump.model() == PumpType.Omnipod_Eros || pump.model() == PumpType.Omnipod_Dash) {
                handleOmnipodReservoirLevel(careportal_reservoir_level, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel, "U"  , colorNormal, colorWarning, colorAlarm)
            } else {
                handleLevel(careportal_reservoir_level, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel, "U" , colorNormal, colorWarning, colorAlarm)
            }
            if (bgSource.sensorBatteryLevel != -1)
                handleLevel(careportal_sensor_battery_level, R.string.key_statuslights_sbat_critical, 5.0, R.string.key_statuslights_sbat_warning, 20.0, bgSource.sensorBatteryLevel.toDouble(), "%" , colorNormal, colorWarning, colorAlarm )
            else
                careportal_sensor_battery_level?.text = ""
        }

        if (!config.NSCLIENT) {
            if (pump.model() == PumpType.Omnipod_Dash) {
                // Omnipod Dash does not report its battery level
                careportal_battery_level?.text = resourceHelper.gs(R.string.notavailable)
                careportal_battery_level?.setTextColor(resourceHelper.getAttributeColor( null, R.attr.statuslightNormal ))
            } else if (pump.model() == PumpType.Omnipod_Eros && pump is OmnipodErosPumpPlugin) { // instance of check is needed because at startup, pump can still be VirtualPumpPlugin and that will cause a crash because of the class cast below
                // The Omnipod Eros does not report its battery level. However, some RileyLink alternatives do.
                // Depending on the user's configuration, we will either show the battery level reported by the RileyLink or "n/a"
                handleOmnipodErosBatteryLevel(careportal_battery_level, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.batteryLevel.toDouble(), "%", pump.isUseRileyLinkBatteryLevel , colorNormal, colorWarning, colorAlarm)
            } else if (pump.model() != PumpType.AccuChekCombo) {
                handleLevel(careportal_battery_level, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.batteryLevel.toDouble(), "%" , colorNormal, colorWarning, colorAlarm)
            }
        }
    }

    private fun handleAge(view: TextView?,type: TherapyEvent.Type, @StringRes warnSettings: Int, defaultWarnThreshold: Double, @StringRes urgentSettings: Int, defaultUrgentThreshold: Double , colorNormal: Int, colorWarning: Int, colorAlarm: Int) {
        val warn = sp.getDouble(warnSettings, defaultWarnThreshold)
        val urgent = sp.getDouble(urgentSettings, defaultUrgentThreshold)
          val therapyEvent = repository.getLastTherapyRecord(type).blockingGet()
        if (therapyEvent is ValueWrapper.Existing) {
            warnColors.setColorByAge(view,  therapyEvent.value, warn, urgent, colorNormal, colorWarning, colorAlarm)
            view?.text =  therapyEvent.value.age(resourceHelper.shortTextMode(), resourceHelper)
        } else {
            view?.text = if (resourceHelper.shortTextMode()) "-" else resourceHelper.gs(R.string.notavailable)
        }
    }

    private fun handleLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String, colorNormal: Int, colorWarning: Int, colorAlarm: Int) {
        val resUrgent = sp.getDouble(criticalSetting, criticalDefaultValue)
        val resWarn = sp.getDouble(warnSetting, warnDefaultValue)
        @Suppress("SetTextI18n")
        view?.text = " " + DecimalFormatter.to0Decimal(level) + units
        warnColors.setColorInverse(view, level, resWarn, resUrgent, colorNormal, colorWarning, colorAlarm)
    }

    // Omnipod only reports reservoir level when it's 50 units or less, so we display "50+U" for any value > 50
    @Suppress("SameParameterValue")
    private fun handleOmnipodReservoirLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String, colorNormal: Int, colorWarning: Int, colorAlarm: Int) {
        if (level > OmnipodConstants.MAX_RESERVOIR_READING) {
            @Suppress("SetTextI18n")
            view?.text = " 50+$units"
            view?.setTextColor(colorNormal)
        } else {
            handleLevel(view, criticalSetting, criticalDefaultValue, warnSetting, warnDefaultValue, level, units , colorNormal,  colorWarning ,colorAlarm )
        }
    }

    @Suppress("SameParameterValue")
    private fun handleOmnipodErosBatteryLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String, useRileyLinkBatteryLevel: Boolean,  colorNormal: Int, colorWarning: Int, colorAlarm: Int) {
        if (useRileyLinkBatteryLevel) {
            handleLevel(view, criticalSetting, criticalDefaultValue, warnSetting, warnDefaultValue, level, units , colorNormal, colorWarning, colorAlarm)
        } else {
            view?.text = resourceHelper.gs(R.string.notavailable)
            view?.setTextColor(colorNormal)
        }
    }
}