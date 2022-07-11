package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.interfaces.BgSync
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCalibrateCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import java.util.function.Supplier
import java.util.stream.Stream

class CalibrateMedLinkMessage(
    baseCallback: java.util.function.Function<Supplier<Stream<String>>, MedLinkStandardReturn<BgSync.BgHistory>>,
    argCallback: java.util.function.Function<Supplier<Stream<String>>, MedLinkStandardReturn<BgSync.BgHistory>>,
    btSleepTime: Long,
    bleCommand: BleCalibrateCommand,
    shouldBeSuspended: Boolean,
    calibrationArgument: String
) : StartStopMessage<BgSync.BgHistory,String>(
    MedLinkCommandType.Calibrate,
    MedLinkCommandType.CalibrateValue,
    baseCallback,
    argCallback,
    btSleepTime,
    bleCommand,
    shouldBeSuspended,
    calibrationArgument
) {

    companion object {

        operator fun invoke(calibrationInfo: Double,
                            baseCallback: java.util.function.Function<Supplier<Stream<String>>, MedLinkStandardReturn<BgSync.BgHistory>>,
                            argCallback: java.util.function.Function<Supplier<Stream<String>>, MedLinkStandardReturn<BgSync.BgHistory>>,
                            btSleepTime: Long,
                            bleCommand: BleCalibrateCommand,
                            shouldBeSuspended: Boolean): CalibrateMedLinkMessage{
            calibrationArgument.bgValue = calibrationInfo
            val argument = if(calibrationInfo<100){
                " 0${calibrationInfo.toInt()}"
            } else{
                " ${calibrationInfo.toInt()}"
            }
            return CalibrateMedLinkMessage(baseCallback, argCallback, btSleepTime, bleCommand,
                                           shouldBeSuspended, argument)
        }
        private val calibrationArgument = MedLinkCommandType.CalibrateValue
    }
}

