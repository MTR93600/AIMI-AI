package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.ConnectionCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BatteryStatusDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicUIResponseType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil;

/**
 * Created by Dirceu on 25/09/20.
 * copied from {@link MedtronicUITask}
 */

public class MedLinkMedtronicUITaskCp {
    //TODO build parsers here

    @Inject RxBusWrapper rxBus;
    @Inject AAPSLogger aapsLogger;
    @Inject MedtronicPumpStatus medtronicPumpStatus;
    @Inject MedLinkMedtronicUtil medtronicUtil;

    private final HasAndroidInjector injector;

    public MedLinkPumpMessage pumpMessage;
    public Object returnData;
    String errorDescription;
    // boolean invalid = false;
    private Object[] parameters;
    // private boolean received;
    MedtronicUIResponseType responseType;


    public MedLinkMedtronicUITaskCp(HasAndroidInjector injector, MedLinkPumpMessage pumpMessage) {
        this.injector = injector;
        this.injector.androidInjector().inject(this);
        this.pumpMessage = pumpMessage;
    }


//    public MedLinkMedtronicUITaskCp(HasAndroidInjector injector, MedLinkCommandType commandType, Object... parameters) {
//        this.injector = injector;
//        this.injector.androidInjector().inject(this);
//        this.commandType = commandType;
//        this.parameters = parameters;
//    }


    public void execute(MedLinkMedtronicCommunicationManager communicationManager, MedLinkMedtronicUIPostprocessor medtronicUIPostprocessor) {

        aapsLogger.debug(LTag.PUMP, "MedtronicUITask: @@@ In execute. {}", pumpMessage);

        switch (pumpMessage.getCommandType()) {
            case BolusStatus:{
                communicationManager.setCommand(pumpMessage);
            }
            case GetState: {
                pumpMessage.getBaseCallback().andThen(f -> {
                    if(medtronicPumpStatus.batteryVoltage != null){
                        BatteryStatusDTO batteryStatus = new BatteryStatusDTO();
                        batteryStatus.batteryStatusType =
                                BatteryStatusDTO.BatteryStatusType.Unknown;
                        batteryStatus.voltage = medtronicPumpStatus.batteryVoltage;
                        medtronicPumpStatus.batteryRemaining =
                                batteryStatus.getCalculatedPercent(medtronicPumpStatus.batteryType);
                    }
                   return f;
                });
                communicationManager.getStatusData(pumpMessage);
            }
            break;
            case PumpModel: {
                Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> activity = new ConnectionCallback().andThen(s -> {
                    if (s.getAnswer().anyMatch(f -> f.contains("eomeomeom") || f.contains("ready"))) {
                        rxBus.send(new EventMedtronicPumpValuesChanged());
                    }

                    return s;
                });
                returnData = communicationManager.getPumpModel(activity);
            }
            break;
//
            case ActiveBasalProfile: {
                communicationManager.getBasalProfile(basalProfile -> {
                    returnData = basalProfile;
                    postProcess(medtronicUIPostprocessor);
                    return basalProfile;
                }, pumpMessage);
            }
            break;
            case BolusHistory: {
                returnData = communicationManager.getBolusHistory(pumpMessage);
            }
            break;
//

//            case GetRemainingInsulin: {
//                returnData = communicationManager.getRemainingInsulin();
//            }
//            break;

//            case : {
//                returnData = communicationManager.getPumpTime();
//                medtronicUtil.setPumpTime(null);
//            }
//            break;

//            case SetRealTimeClock: {
//                returnData = communicationManager.setPumpTime();
//            }
//            break;
//
//            case GetBatteryStatus: {
//                returnData = communicationManager.getRemainingBattery();
//            }
//            break;
//
//            case SetTemporaryBasal: {
//                TempBasalPair tbr = getTBRSettings();
//                if (tbr != null) {
//                    returnData = communicationManager.setTBR(tbr);
//                }
//            }
//            break;
//
//            case ReadTemporaryBasal: {
//                returnData = communicationManager.getTemporaryBasal();
//            }
//            break;


//            case Settings:
//            case Settings_512: {
//                returnData = communicationManager.getPumpSettings();
//            }
//            break;

            case Bolus: {
                Double amount = pumpMessage.getArgument().insulinAmount;

                if (amount != null && amount != 0d)
                    returnData = communicationManager.setBolus(pumpMessage);
            }
            break;

//            case CancelTBR: {
//                returnData = communicationManager.cancelTBR();
//            }
//            break;

//            case SetBasalProfileSTD:
//            case SetBasalProfileA: {
//                BasalProfile profile = (BasalProfile) parameters[0];
//
//                returnData = communicationManager.setBasalProfile(profile);
//            }
//            break;

//            case GetHistoryData: {
//                returnData = communicationManager.getPumpHistory((PumpHistoryEntry) parameters[0],
//                        (LocalDateTime) parameters[1]);
//            }
//            break;
            case PreviousBGHistory:
            case StopStartPump:
            case BGHistory: {
                communicationManager.setCommand(pumpMessage.getCommandType(), pumpMessage.getArgument(),
                        pumpMessage.getBaseCallback(), pumpMessage.getArgCallback());
            }
            break;

            default: {
                aapsLogger.warn(LTag.PUMP, "This commandType is not supported (yet) - {}.", pumpMessage);
                // invalid = true;
                responseType = MedtronicUIResponseType.Invalid;
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


    private TempBasalPair getTBRSettings() {
        return new TempBasalPair(getDoubleFromParameters(0), //
                false, //
                getIntegerFromParameters(1));
    }


    private Float getFloatFromParameters(int index) {
        return (Float) parameters[index];
    }


    Double getDoubleFromParameters(int index) {
        return (Double) parameters[index];
    }


    private Integer getIntegerFromParameters(int index) {
        return (Integer) parameters[index];
    }


    public Object getResult() {
        return returnData;
    }


    public boolean isReceived() {
        return (returnData != null || errorDescription != null);
    }


    void postProcess(MedLinkMedtronicUIPostprocessor postprocessor) {

        aapsLogger.debug(LTag.PUMP, "MedtronicUITask: @@@ In execute. {}", pumpMessage);

        if (responseType == MedtronicUIResponseType.Data) {
//            postprocessor.postProcessData(this);
        }

        aapsLogger.info(LTag.PUMP, "pump response type");
//        aapsLogger.info(LTag.PUMP,responseType.name());

        if (responseType == MedtronicUIResponseType.Invalid) {
            rxBus.send(new EventRileyLinkDeviceStatusChange(PumpDeviceState.ErrorWhenCommunicating,
                    "Unsupported command in MedtronicUITask"));
        } else if (responseType == MedtronicUIResponseType.Error) {
            rxBus.send(new EventRileyLinkDeviceStatusChange(PumpDeviceState.ErrorWhenCommunicating,
                    errorDescription));
        } else {
            rxBus.send(new EventMedtronicPumpValuesChanged());
            medtronicPumpStatus.setLastCommunicationToNow();
        }

        medtronicUtil.setCurrentCommand(null);
    }


    public boolean hasData() {
        return (responseType == MedtronicUIResponseType.Data);
    }


    Object getParameter(int index) {
        return parameters[index];
    }


    public MedtronicUIResponseType getResponseType() {
        return this.responseType;
    }

}
