package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.shared.logging.LTag

class MsgBolusStartWithSpeed(
    injector: HasAndroidInjector,
    private var amount: Double,
    speed: Int
) : MessageBase(injector) {

    init {
        setCommand(0x0104)
        // HARDCODED LIMIT
        amount = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
        addParamInt((amount * 100).toInt())
        addParamByte(speed.toByte())
        aapsLogger.debug(LTag.PUMPBTCOMM, "Bolus start : $amount speed: $speed")
    }

    override fun handleMessage(bytes: ByteArray) {
        val errorCode = intFromBuff(bytes, 0, 1)
        if (errorCode != 2) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode FAILED!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode OK")
        }
        danaPump.bolusStartErrorCode = errorCode
    }
}