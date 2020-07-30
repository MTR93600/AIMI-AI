package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.androidaps.logging.LTag
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import javax.inject.Inject

class DanaRS_Packet_Bolus_Get_Step_Bolus_Information(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_STEP_BOLUS_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val error = intFromBuff(data, 0, 1)
        val bolusType = intFromBuff(data, 1, 1)
        danaPump.initialBolusAmount = intFromBuff(data, 2, 2) / 100.0
        val hours = intFromBuff(data, 4, 1)
        val minutes = intFromBuff(data, 5, 1)
        if (danaPump.usingUTC) danaPump.lastBolusTime = DateTime.now().withZone(DateTimeZone.UTC).withHourOfDay(hours).withMinuteOfHour(minutes).millis
        else danaPump.lastBolusTime = DateTime.now().withHourOfDay(hours).withMinuteOfHour(minutes).millis
        danaPump.lastBolusAmount = intFromBuff(data, 6, 2) / 100.0
        danaPump.maxBolus = intFromBuff(data, 8, 2) / 100.0
        danaPump.bolusStep = intFromBuff(data, 10, 1) / 100.0
        failed = error != 0
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "BolusType: $bolusType")
        aapsLogger.debug(LTag.PUMPCOMM, "Initial bolus amount: " + danaPump.initialBolusAmount + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus time: " + dateUtil.dateAndTimeString(danaPump.lastBolusTime))
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus amount: " + danaPump.lastBolusAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "Max bolus: " + danaPump.maxBolus + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus step: " + danaPump.bolusStep + " U")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_STEP_BOLUS_INFORMATION"
    }
}