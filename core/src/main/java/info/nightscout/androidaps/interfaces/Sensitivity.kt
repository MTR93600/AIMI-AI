package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensDataStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult

interface Sensitivity : ConfigExportImport {

    enum class SensitivityType(val value: Int) {
        UNKNOWN(-1),
        SENSITIVITY_AAPS(0),
        SENSITIVITY_WEIGHTED(1),
        SENSITIVITY_OREF1(2);

        companion object {

            private val map = values().associateBy(SensitivityType::value)
            fun fromInt(type: Int) = map[type]
        }
    }

    val id: SensitivityType
    fun detectSensitivity(ads: AutosensDataStore, fromTime: Long, toTime: Long): AutosensResult

    companion object {

        const val MIN_HOURS = 1.0
        const val MIN_HOURS_FULL_AUTOSENS = 4.0
    }
}