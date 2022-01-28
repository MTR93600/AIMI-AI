package info.nightscout.androidaps.danaRv2.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.T
import java.util.*

class MsgHistoryEventsV2 constructor(
    injector: HasAndroidInjector,
    var from: Long = 0
) : MessageBase(injector) {

    companion object {

        var messageBuffer = arrayListOf<ByteArray>() // for reversing order of incoming messages
    }

    init {
        SetCommand(0xE003)
        if (from > dateUtil.now()) {
            aapsLogger.error("Asked to load from the future")
            from = 0
        }
        if (from == 0L) {
            AddParamByte(0.toByte())
            AddParamByte(1.toByte())
            AddParamByte(1.toByte())
            AddParamByte(0.toByte())
            AddParamByte(0.toByte())
        } else {
            val gFrom = GregorianCalendar()
            gFrom.timeInMillis = from
            AddParamDate(gFrom)
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Loading event history from: " + dateUtil.dateAndTimeString(from))
        danaPump.historyDoneReceived = false
        messageBuffer = arrayListOf()
    }

    override fun handleMessage(data: ByteArray) {
        val recordCode = intFromBuff(data, 0, 1).toByte()
        // Last record
        if (recordCode == 0xFF.toByte()) {
            aapsLogger.debug(LTag.PUMPCOMM, "Last record received")

            val array: Array<ByteArray> = messageBuffer.toTypedArray()
            val sorted = array.sortedArrayWith { s1: ByteArray, s2: ByteArray -> (dateTime(s1) - dateTime(s2)).toInt() }
            for (message in sorted) processMessage(message)
            danaPump.historyDoneReceived = true
        } else messageBuffer.add(data)
    }

    fun dateTime(data: ByteArray): Long =
        dateTimeSecFromBuff(data, 1) // 6 bytes

    fun processMessage(bytes: ByteArray) {
        val recordCode = intFromBuff(bytes, 0, 1).toByte()

        // Last record
        if (recordCode == 0xFF.toByte()) {
            return
        }
        val datetime = dateTimeSecFromBuff(bytes, 1) // 6 bytes
        val param1 = intFromBuff(bytes, 7, 2)
        val param2 = intFromBuff(bytes, 9, 2)
        val status: String
        when (DanaPump.HistoryEntry.fromInt(recordCode.toInt())) {
            DanaPump.HistoryEntry.TEMP_START          -> {
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    "EVENT TEMP_START ($recordCode) " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Ratio: " + param1 + "% Duration: " + param2 + "min"
                )
                val temporaryBasalInfo = temporaryBasalStorage.findTemporaryBasal(datetime, param1.toDouble())
                pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = datetime,
                    rate = param1.toDouble(),
                    duration = T.mins(param2.toLong()).msecs(),
                    isAbsolute = false,
                    type = temporaryBasalInfo?.type,
                    pumpId = datetime,
                    pumpType = PumpType.DANA_RV2,
                    pumpSerial = danaPump.serialNumber
                )
                status = "TEMP_START " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.TEMP_STOP           -> {
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    "EVENT TEMP_STOP ($recordCode) " + dateUtil.dateAndTimeString(datetime)
                )
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    timestamp = datetime,
                    endPumpId = datetime,
                    pumpType = PumpType.DANA_RV2,
                    pumpSerial = danaPump.serialNumber
                )
                status = "TEMP_STOP " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.EXTENDED_START      -> {
                val newRecord = pumpSync.syncExtendedBolusWithPumpId(
                    timestamp = datetime,
                    amount = param1 / 100.0,
                    duration = T.mins(param2.toLong()).msecs(),
                    isEmulatingTB = false,
                    pumpId = datetime,
                    pumpType = PumpType.DANA_RV2,
                    pumpSerial = danaPump.serialNumber
                )
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    (if (newRecord) "**NEW** " else "") + "EVENT EXTENDED_START (" + recordCode + ") "
                        + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U Duration: " + param2 + "min"
                )
                status = "EXTENDED_START " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.EXTENDED_STOP       -> {
                val newRecord = pumpSync.syncStopExtendedBolusWithPumpId(
                    timestamp = datetime,
                    endPumpId = datetime,
                    pumpType = PumpType.DANA_RV2,
                    pumpSerial = danaPump.serialNumber
                )
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    (if (newRecord) "**NEW** " else "") + "EVENT EXTENDED_STOP (" + recordCode + ") "
                        + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Delivered: " + param1 / 100.0 + "U RealDuration: " + param2 + "min"
                )
                status = "EXTENDED_STOP " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.BOLUS               -> {
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(datetime, param1 / 100.0)
                val newRecord = pumpSync.syncBolusWithPumpId(
                    timestamp = datetime,
                    amount = param1 / 100.0,
                    type = detailedBolusInfo?.bolusType,
                    pumpId = datetime,
                    pumpType = PumpType.DANA_RV2,
                    pumpSerial = danaPump.serialNumber
                )
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    (if (newRecord) "**NEW** " else "") + "EVENT BOLUS (" + recordCode + ") "
                        + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Bolus: " + param1 / 100.0 + "U Duration: " + param2 + "min"
                )
                status = "BOLUS " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.DUAL_BOLUS          -> {
                val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(datetime, param1 / 100.0)
                val newRecord = pumpSync.syncBolusWithPumpId(
                    timestamp = datetime,
                    amount = param1 / 100.0,
                    type = detailedBolusInfo?.bolusType,
                    pumpId = datetime,
                    pumpType = PumpType.DANA_RV2,
                    pumpSerial = danaPump.serialNumber
                )
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    (if (newRecord) "**NEW** " else "") + "EVENT DUAL_BOLUS (" + recordCode + ") "
                        + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Bolus: " + param1 / 100.0 + "U Duration: " + param2 + "min"
                )
                status = "DUAL_BOLUS " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.DUAL_EXTENDED_START -> {
                val newRecord = pumpSync.syncExtendedBolusWithPumpId(
                    timestamp = datetime,
                    amount = param1 / 100.0,
                    duration = T.mins(param2.toLong()).msecs(),
                    isEmulatingTB = false,
                    pumpId = datetime,
                    pumpType = PumpType.DANA_RV2,
                    pumpSerial = danaPump.serialNumber
                )
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    (if (newRecord) "**NEW** " else "") + "EVENT DUAL_EXTENDED_START (" + recordCode + ") "
                        + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U Duration: " + param2 + "min"
                )
                status = "DUAL_EXTENDED_START " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.DUAL_EXTENDED_STOP  -> {
                val newRecord = pumpSync.syncStopExtendedBolusWithPumpId(
                    timestamp = datetime,
                    endPumpId = datetime,
                    pumpType = PumpType.DANA_RV2,
                    pumpSerial = danaPump.serialNumber
                )
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    (if (newRecord) "**NEW** " else "") + "EVENT DUAL_EXTENDED_STOP (" + recordCode + ") "
                        + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Delivered: " + param1 / 100.0 + "U RealDuration: " + param2 + "min"
                )
                status = "DUAL_EXTENDED_STOP " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.SUSPEND_ON          -> {
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    "EVENT SUSPEND_ON (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")"
                )
                status = "SUSPEND_ON " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.SUSPEND_OFF         -> {
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    "EVENT SUSPEND_OFF (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")"
                )
                status = "SUSPEND_OFF " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.REFILL              -> {
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    "EVENT REFILL (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U"
                )
                status = "REFILL " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.PRIME               -> {
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    "EVENT PRIME (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Amount: " + param1 / 100.0 + "U"
                )
                status = "PRIME " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.PROFILE_CHANGE      -> {
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    "EVENT PROFILE_CHANGE (" + recordCode + ") " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " No: " + param1 + " CurrentRate: " + param2 / 100.0 + "U/h"
                )
                status = "PROFILE_CHANGE " + dateUtil.timeString(datetime)
            }

            DanaPump.HistoryEntry.CARBS               -> {
                val newRecord = pumpSync.syncCarbsWithTimestamp(
                    timestamp = datetime,
                    amount = param1.toDouble(),
                    pumpId = datetime,
                    pumpType = PumpType.DANA_RV2,
                    pumpSerial = danaPump.serialNumber
                )
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    (if (newRecord) "**NEW** " else "") + "EVENT CARBS (" + recordCode + ") " + dateUtil.dateAndTimeString(
                        datetime
                    ) + " (" + datetime + ")" + " Carbs: " + param1 + "g"
                )
                status = "CARBS " + dateUtil.timeString(datetime)
            }

            else                                      -> {
                aapsLogger.debug(
                    LTag.PUMPBTCOMM,
                    "Event: " + recordCode + " " + dateUtil.dateAndTimeString(datetime) + " (" + datetime + ")" + " Param1: " + param1 + " Param2: " + param2
                )
                status = "UNKNOWN " + dateUtil.timeString(datetime)
            }
        }
        if (datetime > danaPump.lastEventTimeLoaded) danaPump.lastEventTimeLoaded = datetime
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.processinghistory) + ": " + status))
    }
}