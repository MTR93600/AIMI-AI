package info.nightscout.androidaps.plugins.pump.common.data

import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import java.util.*

/**
 * Created by andy on 4/28/18.
 */
abstract class PumpStatus(var pumpType: PumpType) {

    // connection
    var lastDataTime: Long = 0
    var lastConnection = 0L
    var previousConnection = 0L // here should be stored last connection of previous session (so needs to be

    // read before lastConnection is modified for first time).
    // last bolus
    var lastBolusTime: Date? = null
    var lastBolusAmount: Double? = null

    // other pump settings
    var activeProfileName = "0"
    var reservoirLevel = 0.0

    var reservoirFullUnits = 0
    var batteryLevel = 0 // percent, so 0-100

    var batteryVoltage: Double? = null

    // iob
    var iob: String? = null

    // TDD
    var dailyTotalUnits: Double? = null
    var maxDailyTotalUnits: String? = null
    var units: String? = null // Constants.MGDL or Constants.MMOL
    var pumpStatusType = PumpStatusType.Running
    var basalsByHour: DoubleArray? = null
    var tempBasalStart: Date? = null
    var tempBasalAmount: Double? = 0.0
    var tempBasalLength: Int? = 0
    var tempBasalEnd: Long? = null

    abstract fun initSettings()

    fun setLastCommunicationToNow() {
        lastDataTime = System.currentTimeMillis()
        lastConnection = System.currentTimeMillis()
    }

    abstract val errorInfo: String?

}