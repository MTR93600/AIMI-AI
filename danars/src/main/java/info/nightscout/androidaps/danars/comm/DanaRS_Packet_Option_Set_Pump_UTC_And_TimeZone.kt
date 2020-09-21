package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class DanaRS_Packet_Option_Set_Pump_UTC_And_TimeZone(
    injector: HasAndroidInjector,
    private var time: Long = 0,
    private var zoneOffset: Int = 0
) : DanaRS_Packet(injector) {

    var error = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__SET_PUMP_UTC_AND_TIME_ZONE
        aapsLogger.debug(LTag.PUMPCOMM, "Setting UTC pump time ${dateUtil.dateAndTimeString(time)} ZoneOffset: $zoneOffset")
    }

    override fun getRequestParams(): ByteArray {
        val date = DateTime(time).withZone(DateTimeZone.UTC)
        val request = ByteArray(7)
        request[0] = (date.year - 2000 and 0xff).toByte()
        request[1] = (date.monthOfYear and 0xff).toByte()
        request[2] = (date.dayOfMonth and 0xff).toByte()
        request[3] = (date.hourOfDay and 0xff).toByte()
        request[4] = (date.minuteOfHour and 0xff).toByte()
        request[5] = (date.secondOfMinute and 0xff).toByte()
        request[6] = zoneOffset.toByte()
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
        return "OPTION__SET_PUMP_UTC_AND_TIMEZONE"
    }
}