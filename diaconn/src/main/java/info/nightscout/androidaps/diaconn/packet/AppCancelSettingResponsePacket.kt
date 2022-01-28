package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

/**
 * AppCancelSettingResponsePacket
 */
class AppCancelSettingResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var result =0
    init {
        msgType = 0xA9.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "AppCancelSettingResPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "AppCancelSettingResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        result =  getByteToInt(bufferData)
        if(!isSuccSettingResponseResult(result)) {
            diaconnG8Pump.resultErrorCode = result
            failed = true
            return
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${result}")
    }


    override fun getFriendlyName(): String {
        return "PUMP_APP_CANCEL_SETTING_RESPONSE"
    }
}