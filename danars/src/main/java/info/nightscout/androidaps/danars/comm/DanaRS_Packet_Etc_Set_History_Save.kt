package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_Etc_Set_History_Save(
    injector: HasAndroidInjector,
    private var historyType: Int = 0,
    private var historyYear: Int = 0,
    private var historyMonth: Int = 0,
    private var historyDate: Int = 0,
    private var historyHour: Int = 0,
    private var historyMinute: Int = 0,
    private var historySecond: Int = 0,
    private var historyCode: Int = 0,
    private var historyValue: Int = 0
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_ETC__SET_HISTORY_SAVE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(10)
        request[0] = (historyType and 0xff).toByte()
        request[1] = (historyYear and 0xff).toByte()
        request[2] = (historyMonth and 0xff).toByte()
        request[3] = (historyDate and 0xff).toByte()
        request[4] = (historyHour and 0xff).toByte()
        request[5] = (historyMinute and 0xff).toByte()
        request[6] = (historySecond and 0xff).toByte()
        request[7] = (historyCode and 0xff).toByte()
        request[8] = (historyValue and 0xff).toByte()
        request[9] = (historyValue ushr 8 and 0xff).toByte()
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
        return "ETC__SET_HISTORY_SAVE"
    }
}