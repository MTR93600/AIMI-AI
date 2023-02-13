package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * AppConfirmSettingResponsePacket
 */
class AppConfirmSettingResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var result =0
    init {
        msgType = 0xB7.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "AppConfirmSettingReqPacket Response ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "AppConfirmSettingResponsePacket Got some Error")
            failed = true
            return
        } else failed = false
        val bufferData = prefixDecode(data)
        result =  getByteToInt(bufferData)
        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${result}")

        if(!isSuccSettingResponseResult(result)) {
            diaconnG8Pump.resultErrorCode = result
            failed = true
            return
        }
        //  The bolus progress dialog opens only when the confirm result is successful
        if(diaconnG8Pump.bolusConfirmMessage == 0x07.toByte()) {
          diaconnG8Pump.isReadyToBolus = true
        }
    }

    override fun getFriendlyName(): String {
        return "PUMP_APP_CONFIRM_SETTING_RESPONSE"
    }
}
