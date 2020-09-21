package info.nightscout.androidaps.danaRKorean.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.logging.LTag

class MsgStatus_k(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        SetCommand(0x020B)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.dailyTotalUnits = intFromBuff(bytes, 0, 3) / 750.0
        danaPump.isExtendedInProgress = intFromBuff(bytes, 3, 1) == 1
        danaPump.extendedBolusMinutes = intFromBuff(bytes, 4, 2)
        danaPump.extendedBolusAmount = intFromBuff(bytes, 6, 2) / 100.0
        //val lastBolusAmount = intFromBuff(bytes, 13, 2) / 100.0
        //if (lastBolusAmount != 0d) {
        //  pump.lastBolusTime = dateTimeFromBuff(bytes, 8);
        //  pump.lastBolusAmount = lastBolusAmount;
        //}
        danaPump.iob = intFromBuff(bytes, 15, 2) / 100.0
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total: " + danaPump.dailyTotalUnits)
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: " + danaPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus min: " + danaPump.extendedBolusMinutes)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus amount: " + danaPump.extendedBolusAmount)
        //aapsLogger.debug(LTag.PUMPCOMM, "Last bolus time: " + pump.lastBolusTime);
        //aapsLogger.debug(LTag.PUMPCOMM, "Last bolus amount: " + pump.lastBolusAmount);
        aapsLogger.debug(LTag.PUMPCOMM, "IOB: " + danaPump.iob)
    }
}