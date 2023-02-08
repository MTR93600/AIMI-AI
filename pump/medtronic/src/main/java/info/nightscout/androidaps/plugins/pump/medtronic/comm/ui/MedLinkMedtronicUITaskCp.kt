package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.ConnectionCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicUIResponseType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil
import info.nightscout.pump.core.defs.PumpDeviceState
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream
import javax.inject.Inject

/**
 * Created by Dirceu on 25/09/20.
 * copied from [MedtronicUITask]
 */
class MedLinkMedtronicUITaskCp @Inject constructor(injector: HasAndroidInjector,val aapsLogger: AAPSLogger) {



    var rxBus: RxBus?= null
        @Inject set
    var medtronicPumpStatus: MedtronicPumpStatus? = null
        @Inject set

    var medtronicUtil: MedLinkMedtronicUtil? = null
        @Inject set

    var result: Any? = null
    var errorDescription: String? = null

    // boolean invalid = false;
    private val parameters: Array<Any> = arrayOf()

    // private boolean received;
    var responseType: MedtronicUIResponseType? = null

    //    public MedLinkMedtronicUITaskCp(HasAndroidInjector injector, MedLinkCommandType commandType, Object... parameters) {
    //        this.injector = injector;
    //        this.injector.androidInjector().inject(this);
    //        this.commandType = commandType;
    //        this.parameters = parameters;
    //    }
    fun <B,C>execute(communicationManager: MedLinkMedtronicCommunicationManager, medtronicUIPostprocessor: MedLinkMedtronicUIPostprocessor?,pumpMessage: MedLinkPumpMessage<B,C>) {
        aapsLogger!!.info(LTag.PUMP, "MedtronicUITask: @@@ In execute. {}", pumpMessage)
        val func = pumpMessage.firstFunction()
        func.map {
            it.andThen { funcRes ->
                postProcess(medtronicUIPostprocessor, funcRes,pumpMessage)
            }
        }
        when (pumpMessage.firstCommand()) {
            MedLinkCommandType.BolusStatus                                                                                                                                            -> {
                run { communicationManager.setCommand(pumpMessage) }
                run {
//                pumpMessage.getBaseCallback().andThen(f -> {
//                    medtronicPumpStatus.getBatteryLevel();
//                    BatteryStatusDTO batteryStatus = new BatteryStatusDTO();
//                    batteryStatus.setBatteryStatusType(
//                            BatteryStatusDTO.BatteryStatusType.Unknown);
//                    batteryStatus.setVoltage(medtronicPumpStatus.getBatteryVoltage());
//                    medtronicPumpStatus.setBatteryLevel(
//                            batteryStatus.getCalculatedPercent(medtronicPumpStatus.getBatteryType()));
//                    return f;
//                });
//                     communicationManager.getStatusData(pumpMessage)
                }
            }

            MedLinkCommandType.GetState                                                                                                                                               -> {
                communicationManager.getStatusData(pumpMessage)
            }

            MedLinkCommandType.PumpModel                                                                                                                                              -> {
                val activity: Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> = ConnectionCallback().andThen(
                    Function { s: MedLinkStandardReturn<String> ->
                        if (s.getAnswer().anyMatch { f -> f.contains("eomeomeom") || f.contains("ready") }) {
                            rxBus!!.send(EventMedtronicPumpValuesChanged())
                        }
                        s
                    })
                result = communicationManager.getPumpModel(activity)
            }


            MedLinkCommandType.PreviousBolusHistory, MedLinkCommandType.BolusHistory                                                                                                  -> {
                result = communicationManager.getBolusHistory(pumpMessage)
            }

            MedLinkCommandType.TBRBolus, MedLinkCommandType.SMBBolus, MedLinkCommandType.Bolus                                                                                        -> {
                // if ((pumpMessage as BolusMedLinkMessage).insulinAmount != 0.0)
                    result = communicationManager.setBolus(pumpMessage)
            }

            MedLinkCommandType.ActiveBasalProfile,
            MedLinkCommandType.CalibrateFrequency,
            MedLinkCommandType.Calibrate,
            MedLinkCommandType.PreviousBGHistory,
            MedLinkCommandType.StopStartPump,
            MedLinkCommandType.BGHistory -> {
                communicationManager.setCommand(pumpMessage)
            }

            else                                                                                                                                                                      -> {
                aapsLogger!!.warn(LTag.PUMP, "This commandType is not supported (yet) - {}.", pumpMessage)
                // invalid = true;
                responseType = MedtronicUIResponseType.Invalid
            }
        }

//        if (responseType == null) {
//            if (returnData == null) {
//                errorDescription = communicationManager.getErrorResponse();
//                this.responseType = MedtronicUIResponseType.Error;
//            } else {
//                this.responseType = MedtronicUIResponseType.Data;
//            }
//        }
    }

    //
    //
    private val tBRSettings: TempBasalPair
        private get() = TempBasalPair(
            getDoubleFromParameters(0),  //
            false,  //
            getIntegerFromParameters(1)
        )

    private fun getFloatFromParameters(index: Int): Float {
        return parameters[index] as Float
    }

    fun getDoubleFromParameters(index: Int): Double {
        return parameters[index] as Double
    }

    private fun getIntegerFromParameters(index: Int): Int {
        return parameters[index] as Int
    }

    val isReceived: Boolean
        get() = result != null || errorDescription != null

    fun <B,C>postProcess(postprocessor: MedLinkMedtronicUIPostprocessor?, ret: MedLinkStandardReturn<*>, pumpMessage: MedLinkPumpMessage<B,C>) {
        aapsLogger!!.debug(LTag.PUMP, "MedtronicUITask: @@@ In execute. {}", pumpMessage)
        if (responseType === MedtronicUIResponseType.Data) {
//            postprocessor.postProcessData(this);
        }
        aapsLogger!!.info(LTag.PUMP, "pump response type")
        //        aapsLogger.info(LTag.PUMP,responseType.name());
        val errors = ret.getErrors()

        if (ret.getErrors().any {
                it == MedLinkStandardReturn.ParsingError.Unreachable ||
                    it == MedLinkStandardReturn.ParsingError.ConnectionParsingError
            }) {
            rxBus!!.send(
                EventRileyLinkDeviceStatusChange(
                    PumpDeviceState.ErrorWhenCommunicating,
                    errorDescription
                )
            )
        } else if (errors.isNotEmpty()) {
            rxBus!!.send(
                EventRileyLinkDeviceStatusChange(
                    PumpDeviceState.ErrorWhenCommunicating,
                    "Unsupported command in MedtronicUITask"
                )
            )
        } else {
            rxBus!!.send(EventMedtronicPumpValuesChanged())
            medtronicPumpStatus!!.setLastCommunicationToNow()
        }
        medtronicUtil!!.currentCommand = null
    }

    fun hasData(): Boolean {
        return responseType === MedtronicUIResponseType.Data
    }

    fun getParameter(index: Int): Any {
        return parameters[index]
    }


}