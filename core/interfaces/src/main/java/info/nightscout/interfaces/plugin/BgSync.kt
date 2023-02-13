package info.nightscout.androidaps.interfaces

import android.os.Bundle
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.TherapyEvent

/**
  MedLink bg sync
 @dirceu

 */
interface BgSync {

    /**
     *  Query expected pump state
     *
     *  Driver may query AAPS for expecting state of the pump and use it for sanity check
     *  or generation of status for NS
     *
     *  TemporaryBasal
     *  duration in milliseconds
     *  rate in U/h or % where 100% is equal to no TBR
     *
     *  ExtendedBolus
     *  duration in milliseconds
     *  amount in U
     *  rate in U/h (synthetic only)
     *
     *  @return         data from database.
     *                  temporaryBasal (and extendedBolus) is null if there is no record in progress based on data in database
     *                  bolus is null when there is no record in database
     */
    data class BgHistory(var bgValue: List<BgValue>, val bgCalibration: List<Calibration>, val serialNumber: String? = null) {

        data class BgValue  constructor(
            val timestamp: Long,
            val raw: Double,
            val value: Double,
            val noise: Double,
            val arrow: BgArrow,
            val sourceSensor: SourceSensor,
            val isig: Double?,
            val calibrationFactor: Double?,
            val sensorUptime: Int?
        )

        data class Calibration @JvmOverloads constructor(val timestamp: Long, val value: Double, val glucoseUnit: GlucoseUnit)

    }

    /*
     *   BOLUSES & CARBS
     */

    fun syncBgWithTempId(
        bgValues: List<BgHistory.BgValue>,
        calibrations: List<BgHistory.Calibration>
    ): Boolean

    fun syncBgWithTempId(bundle: Bundle)
    /**
     * Synchronization of boluses with temporary id
     *
     * Search for combination of  temporaryId, PumpType, pumpSerial
     *
     * If db record doesn't exist data is ignored and false returned.
     * If exists, amount and timestamp is updated, type and pumpId only if provided
     * isValid field is preserved
     *
     * USAGE:
     * After reading record from history or completed bolus call syncBolusWithTempId and
     * provide updated timestamp, amount, pumpId (if known), type (if change needed) with the same temporaryId, pumpType, pumpSerial
     *
     * @param timestamp     timestamp of event from pump history
     * @param amount        amount of insulin
     * @param temporaryId   temporary id generated when pump id in not know yet
     * @param type          type of bolus (NORMAL, SMB, PRIME)
     * @param pumpId        pump id from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if record is successfully updated
     **/
    fun addBgTempId(
        timestamp: Long,
        raw: Double?,
        value: Double,
        noise: Double?,
        arrow: BgArrow,
        sourceSensor: SourceSensor,
        isig: Double?,
        calibrationFactor: Double?,
        sensorUptime: Int?
    ): Boolean

    enum class GlucoseUnit {
        MGDL,
        MMOL;

        fun toDbType():TherapyEvent.GlucoseUnit =
            when(this){
                MGDL -> TherapyEvent.GlucoseUnit.MGDL
                MMOL -> TherapyEvent.GlucoseUnit.MMOL
        }
    }

    enum class BgArrow {
        NONE,
        TRIPLE_UP,
        DOUBLE_UP,
        SINGLE_UP,
        FORTY_FIVE_UP,
        FLAT,
        FORTY_FIVE_DOWN,
        SINGLE_DOWN,
        DOUBLE_DOWN,
        TRIPLE_DOWN;

        fun toDbType(): GlucoseValue.TrendArrow =
            when (this) {
                NONE            -> GlucoseValue.TrendArrow.NONE
                TRIPLE_UP       -> GlucoseValue.TrendArrow.TRIPLE_UP
                DOUBLE_UP       -> GlucoseValue.TrendArrow.DOUBLE_UP
                SINGLE_UP       -> GlucoseValue.TrendArrow.SINGLE_UP
                FORTY_FIVE_UP   -> GlucoseValue.TrendArrow.FORTY_FIVE_UP
                FLAT            -> GlucoseValue.TrendArrow.FLAT
                FORTY_FIVE_DOWN -> GlucoseValue.TrendArrow.FORTY_FIVE_DOWN
                SINGLE_DOWN     -> GlucoseValue.TrendArrow.SINGLE_DOWN
                DOUBLE_DOWN     -> GlucoseValue.TrendArrow.DOUBLE_DOWN
                TRIPLE_DOWN     -> GlucoseValue.TrendArrow.TRIPLE_DOWN
            }

    }

    enum class SourceSensor {
        DEXCOM_NATIVE_UNKNOWN,
        DEXCOM_G6_NATIVE,
        DEXCOM_G5_NATIVE,
        DEXCOM_G4_WIXEL,
        DEXCOM_G4_XBRIDGE,
        DEXCOM_G4_NATIVE,
        MEDTRUM_A6,
        DEXCOM_G4_NET,
        DEXCOM_G4_NET_XBRIDGE,
        DEXCOM_G4_NET_CLASSIC,
        DEXCOM_G5_XDRIP,
        DEXCOM_G6_NATIVE_XDRIP,
        DEXCOM_G5_NATIVE_XDRIP,
        DEXCOM_G6_G5_NATIVE_XDRIP,
        LIBRE_1_NET,
        LIBRE_1_BLUE,
        LIBRE_1_PL,
        LIBRE_1_BLUCON,
        LIBRE_1_TOMATO,
        LIBRE_1_RF,
        LIBRE_1_LIMITTER,
        GLIMP,
        LIBRE_2_NATIVE,
        POCTECH_NATIVE,
        GLUNOVO_NATIVE,
        MM_600_SERIES,
        EVERSENSE,
        RANDOM,
        UNKNOWN,

        IOB_PREDICTION,
        A_COB_PREDICTION,
        COB_PREDICTION,
        UAM_PREDICTION,
        ZT_PREDICTION,
        MM_ENLITE
        ;

        fun toDbType(): GlucoseValue.SourceSensor =
            when (this) {
                DEXCOM_NATIVE_UNKNOWN     -> GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN
                DEXCOM_G6_NATIVE          -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE
                DEXCOM_G5_NATIVE          -> GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE
                DEXCOM_G4_WIXEL           -> GlucoseValue.SourceSensor.DEXCOM_G4_WIXEL
                DEXCOM_G4_XBRIDGE         -> GlucoseValue.SourceSensor.DEXCOM_G4_XBRIDGE
                DEXCOM_G4_NATIVE          -> GlucoseValue.SourceSensor.DEXCOM_G4_NATIVE
                MEDTRUM_A6                -> GlucoseValue.SourceSensor.MEDTRUM_A6
                DEXCOM_G4_NET             -> GlucoseValue.SourceSensor.DEXCOM_G4_NET
                DEXCOM_G4_NET_XBRIDGE     -> GlucoseValue.SourceSensor.DEXCOM_G4_NET_XBRIDGE
                DEXCOM_G4_NET_CLASSIC     -> GlucoseValue.SourceSensor.DEXCOM_G4_NET_CLASSIC
                DEXCOM_G5_XDRIP           -> GlucoseValue.SourceSensor.DEXCOM_G5_XDRIP
                DEXCOM_G6_NATIVE_XDRIP    -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP
                DEXCOM_G5_NATIVE_XDRIP    -> GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP
                DEXCOM_G6_G5_NATIVE_XDRIP -> GlucoseValue.SourceSensor.DEXCOM_G6_G5_NATIVE_XDRIP
                LIBRE_1_NET               -> GlucoseValue.SourceSensor.LIBRE_1_NET
                LIBRE_1_BLUE              -> GlucoseValue.SourceSensor.LIBRE_1_BLUE
                LIBRE_1_PL                -> GlucoseValue.SourceSensor.LIBRE_1_PL
                LIBRE_1_BLUCON            -> GlucoseValue.SourceSensor.LIBRE_1_BLUCON
                LIBRE_1_TOMATO            -> GlucoseValue.SourceSensor.LIBRE_1_TOMATO
                LIBRE_1_RF                -> GlucoseValue.SourceSensor.LIBRE_1_RF
                LIBRE_1_LIMITTER          -> GlucoseValue.SourceSensor.LIBRE_1_LIMITTER
                GLIMP                     -> GlucoseValue.SourceSensor.GLIMP
                LIBRE_2_NATIVE            -> GlucoseValue.SourceSensor.LIBRE_2_NATIVE
                POCTECH_NATIVE            -> GlucoseValue.SourceSensor.POCTECH_NATIVE
                GLUNOVO_NATIVE            -> GlucoseValue.SourceSensor.GLUNOVO_NATIVE
                MM_600_SERIES             -> GlucoseValue.SourceSensor.MM_600_SERIES
                EVERSENSE                 -> GlucoseValue.SourceSensor.EVERSENSE
                RANDOM                    -> GlucoseValue.SourceSensor.RANDOM
                UNKNOWN                   -> GlucoseValue.SourceSensor.UNKNOWN
                IOB_PREDICTION            -> GlucoseValue.SourceSensor.IOB_PREDICTION
                A_COB_PREDICTION          -> GlucoseValue.SourceSensor.A_COB_PREDICTION
                COB_PREDICTION            -> GlucoseValue.SourceSensor.COB_PREDICTION
                UAM_PREDICTION            -> GlucoseValue.SourceSensor.UAM_PREDICTION
                ZT_PREDICTION             -> GlucoseValue.SourceSensor.ZT_PREDICTION
                MM_ENLITE                 -> GlucoseValue.SourceSensor.MM_ENLITE
            }
    }

}