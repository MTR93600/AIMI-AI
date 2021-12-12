package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

/**
 * TempBasalInquireResponsePacket
 */
open class TempBasalInquireResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    var result = 0
    init {
        msgType = 0x8A.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "TempBasalInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "TempBasalInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        result =  getByteToInt(bufferData)
        if(!isSuccInquireResponseResult(result)) {
            failed = true
            return
        }

        diaconnG8Pump.tbStatus = getByteToInt(bufferData) // 임시기저 상태
        diaconnG8Pump.tbTime = getByteToInt(bufferData)    // 임시기저시간
        diaconnG8Pump.tbInjectRateRatio = getShortToInt(bufferData) //임시기저 주입량/률
        diaconnG8Pump.tbElapsedTime = getShortToInt(bufferData) // 임시기저 경과시간

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> $result")
        aapsLogger.debug(LTag.PUMPCOMM, "tbStatus > " + diaconnG8Pump.tbStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "tbTime> " + diaconnG8Pump.tbTime)
        aapsLogger.debug(LTag.PUMPCOMM, "tbInjectRateRatio > " + diaconnG8Pump.tbInjectRateRatio)
        aapsLogger.debug(LTag.PUMPCOMM, "tbElapsedTime > " + diaconnG8Pump.tbElapsedTime)
    }

    override fun getFriendlyName(): String {
        return "PUMP_TEMP_BASAL_INQUIRE_RESPONSE"
    }
}