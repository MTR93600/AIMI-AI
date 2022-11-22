package info.nightscout.androidaps.plugins.general.overview

import android.widget.TextView
import androidx.annotation.StringRes
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.extensions.age
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.omnipod.eros.OmnipodErosPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.pump.eopatch.AppConstant
import info.nightscout.shared.sharedPreferences.SP
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
    private val repository: AppRepository
) {

    /**
     * applies the extended statusLight subview on the overview fragment
     */
    fun updateStatusLights(
        careportal_cannula_age: TextView?,
        careportal_insulin_age: TextView?,
        careportal_reservoir_level: TextView?,
        careportal_sensor_age: TextView?,
        careportal_sensor_battery_level: TextView?,
        careportal_pb_age: TextView?,
        careportal_battery_level: TextView?,
        medlink_battery_level: TextView?
    ) {
        val pump = activePlugin.activePump
        val bgSource = activePlugin.activeBgSource
        handleAge(careportal_cannula_age, TherapyEvent.Type.CANNULA_CHANGE, R.string.key_statuslights_cage_warning, 48.0, R.string.key_statuslights_cage_critical, 72.0)
        handleAge(careportal_insulin_age, TherapyEvent.Type.INSULIN_CHANGE, R.string.key_statuslights_iage_warning, 72.0, R.string.key_statuslights_iage_critical, 144.0)
        handleAge(careportal_sensor_age, TherapyEvent.Type.SENSOR_CHANGE, R.string.key_statuslights_sage_warning, 216.0, R.string.key_statuslights_sage_critical, 240.0)
        if (pump is MedLinkMedtronicPumpPlugin) {
            val pumpStatusData = pump.pumpStatusData
            if (pumpStatusData is MedLinkMedtronicPumpStatus) {
                if (pumpStatusData.batteryType == BatteryType.LiPo || sp.getString(
                        R.string
                            .key_medlink_battery_info, "Age"
                    ) == "Age"
                ) {
                    handleAge(careportal_pb_age, TherapyEvent.Type.PUMP_BATTERY_CHANGE, R.string.key_statuslights_bage_warning, 100.0, R.string.key_statuslights_bage_critical, 120.0)
                }
                handleLevel(
                    medlink_battery_level, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0,
                    pump.pumpStatusData.batteryRemaining.toDouble()
                    // pump.pumpStatusData.deviceBatteryRemaining.toDouble()
                    , "%"
                )
            } else if (pump.pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled()) {
                handleAge(careportal_pb_age, TherapyEvent.Type.PUMP_BATTERY_CHANGE, R.string.key_statuslights_bage_warning, 216.0, R.string.key_statuslights_bage_critical, 240.0)
            } else {
                medlink_battery_level?.visibility = false.toVisibility()
            }
        } else if (pump.pumpDescription.isBatteryReplaceable || (pump is OmnipodErosPumpPlugin && pump.isUseRileyLinkBatteryLevel && pump.isBatteryChangeLoggingEnabled())) {
            handleAge(careportal_pb_age, TherapyEvent.Type.PUMP_BATTERY_CHANGE, R.string.key_statuslights_bage_warning, 216.0, R.string.key_statuslights_bage_critical, 240.0)
        }

        careportal_sensor_battery_level?.text = ""
        if (!config.NSCLIENT) {
        val insulinUnit = rh.gs(R.string.insulin_unit_shortname)
        if (pump.model() == PumpType.OMNIPOD_EROS || pump.model() == PumpType.OMNIPOD_DASH) {
            handlePatchReservoirLevel(careportal_reservoir_level, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel, insulinUnit,
                                  OmnipodConstants.MAX_RESERVOIR_READING)
        } else if (pump.model() == PumpType.EOFLOW_EOPATCH2) {
            handlePatchReservoirLevel(careportal_reservoir_level, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel, insulinUnit,
                                      AppConstant.MAX_RESERVOIR_READING)
        } else {
            handleLevel(careportal_reservoir_level, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel, insulinUnit)
        }

        if (!config.NSCLIENT) {
            if (bgSource.sensorBatteryLevel != -1)
                handleLevel(careportal_sensor_battery_level, R.string.key_statuslights_sbat_critical, 5.0, R.string.key_statuslights_sbat_warning, 20.0, bgSource.sensorBatteryLevel.toDouble(), "%")
            else
                careportal_sensor_battery_level?.text = ""
        }

        if (!config.NSCLIENT) {
            // The Omnipod Eros does not report its battery level. However, some RileyLink alternatives do.
            // Depending on the user's configuration, we will either show the battery level reported by the RileyLink or "n/a"
            val erosBatteryLinkAvailable = pump.model() == PumpType.OMNIPOD_EROS && pump is OmnipodErosPumpPlugin && pump.isUseRileyLinkBatteryLevel

            if (pump.model().supportBatteryLevel || erosBatteryLinkAvailable || pump !is MedLinkPumpDevice && pump.model() != PumpType.ACCU_CHEK_COMBO) {
                handleLevel(careportal_battery_level, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.batteryLevel.toDouble(), "%")
            } else if (pump is MedLinkMedtronicPumpPlugin) {
                if (sp.getString(
                        R.string
                            .key_medlink_battery_info, "Age"
                    ) != "Age"
                ) {
                    if (pump.pumpStatusData.batteryVoltage != null) {
                        handleDecimal(
                            careportal_battery_level, R.string.key_statuslights_bat_critical, 1.30, R.string.key_statuslights_bat_warning, 1.20,
                            pump.pumpStatusData.batteryVoltage!!, "V"
                        )
                    }
                }
                // else {
                //     handleLevel(careportal_battery_level, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.pumpStatusData.batteryRemaining.toDouble(), "%")
                // }
            } else {
                careportal_battery_level?.text = rh.gs(R.string.value_unavailable_short)
                careportal_battery_level?.setTextColor(rh.gac(careportal_battery_level.context, R.attr.defaultTextColor))
            }
            // else {
            //     handleLevel(careportal_battery_level, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.pumpStatusData.batteryRemaining.toDouble(), "%")
            // }
        }
    }
    // if(resourceHelper.gs(R.string.key_medlink_battery_info).equals()){
    //
    // }

    private fun handleAge(view: TextView?, type: TherapyEvent.Type, @StringRes warnSettings: Int, defaultWarnThreshold: Double, @StringRes urgentSettings: Int, defaultUrgentThreshold: Double) {
        val warn = sp.getDouble(warnSettings, defaultWarnThreshold)
        val urgent = sp.getDouble(urgentSettings, defaultUrgentThreshold)
        val therapyEvent = repository.getLastTherapyRecordUpToNow(type).blockingGet()
        if (therapyEvent is ValueWrapper.Existing<TherapyEvent>) {
            warnColors.setColorByAge(view, therapyEvent.value, warn, urgent)
            view?.text = therapyEvent.value.age(rh.shortTextMode(), rh, dateUtil)
        } else {
            view?.text = if (rh.shortTextMode()) "-" else rh.gs(R.string.value_unavailable_short)
        }
    }

    private fun handleLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String) {
        val resUrgent = sp.getDouble(criticalSetting, criticalDefaultValue)
        val resWarn = sp.getDouble(warnSetting, warnDefaultValue)
        @Suppress("SetTextI18n")
        view?.text = " " + DecimalFormatter.to0Decimal(level) + units
        warnColors.setColorInverse(view, level, resWarn, resUrgent)
    }

    private fun handleDecimal(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String) {
        val resUrgent = sp.getDouble(criticalSetting, criticalDefaultValue)
        val resWarn = sp.getDouble(warnSetting, warnDefaultValue)
        @Suppress("SetTextI18n")
        view?.text = " " + DecimalFormatter.to2Decimal(level) + units
        warnColors.setColorInverse(view, level, resWarn, resUrgent)
    }

    // Omnipod only reports reservoir level when it's 50 units or less, so we display "50+U" for any value > 50
    @Suppress("SameParameterValue")
    private fun handlePatchReservoirLevel(
        view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int,
        warnDefaultValue: Double, level: Double, units: String, maxReading: Double
    ) {
        if (level >= maxReading) {
            @Suppress("SetTextI18n")
            view?.text = " ${maxReading.toInt()}+$units"
            view?.setTextColor(rh.gac(view.context, R.attr.defaultTextColor))
        } else {
            handleLevel(view, criticalSetting, criticalDefaultValue, warnSetting, warnDefaultValue, level, units)
        }
    }
}
