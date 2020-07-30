package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

open class DanaRS_Packet_Basal_Set_Temporary_Basal(
    injector: HasAndroidInjector,
    private var temporaryBasalRatio: Int = 0,
    private var temporaryBasalDuration: Int = 0
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__SET_TEMPORARY_BASAL
        aapsLogger.debug(LTag.PUMPCOMM, "Setting temporary basal of $temporaryBasalRatio% for $temporaryBasalDuration hours")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(2)
        request[0] = (temporaryBasalRatio and 0xff).toByte()
        request[1] = (temporaryBasalDuration and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override fun getFriendlyName(): String {
        return "BASAL__SET_TEMPORARY_BASAL"
    }
}