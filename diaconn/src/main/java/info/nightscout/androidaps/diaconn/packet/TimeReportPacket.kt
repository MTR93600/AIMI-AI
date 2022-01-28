package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

/**
 * TimeReportPacket
 */
class TimeReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var result =0
    init {
        msgType = 0xCF.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "TimeReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "TimeReportPacket Report Packet Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        diaconnG8Pump.year   = getByteToInt(bufferData) // yyyy
        diaconnG8Pump.month  = getByteToInt(bufferData) // month
        diaconnG8Pump.day    = getByteToInt(bufferData) // day
        diaconnG8Pump.hour   = getByteToInt(bufferData) // hour
        diaconnG8Pump.minute = getByteToInt(bufferData) // min
        diaconnG8Pump.second = getByteToInt(bufferData) // second

        aapsLogger.debug(LTag.PUMPCOMM, "year   --> ${diaconnG8Pump.year  }")
        aapsLogger.debug(LTag.PUMPCOMM, "month  --> ${diaconnG8Pump.month }")
        aapsLogger.debug(LTag.PUMPCOMM, "day    --> ${diaconnG8Pump.day   }")
        aapsLogger.debug(LTag.PUMPCOMM, "hour   --> ${diaconnG8Pump.hour  }")
        aapsLogger.debug(LTag.PUMPCOMM, "minute --> ${diaconnG8Pump.minute}")
        aapsLogger.debug(LTag.PUMPCOMM, "second --> ${diaconnG8Pump.second}")
    }

    override fun getFriendlyName(): String {
        return "PUMP_TIME_REPORT"
    }
}