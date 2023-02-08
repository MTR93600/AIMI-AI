package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.shared.interfaces.ResourceHelper;


/**
 * Created by Dirceu on 25/09/20
 * copied from {@link MedtronicUIPostprocessor}.
 */

@Singleton
public class MedLinkMedtronicUIPostprocessor {

    private final AAPSLogger aapsLogger;
    private final RxBus rxBus;
    private final ResourceHelper resourceHelper;
    private final MedLinkMedtronicUtil medtronicUtil;
    private final MedLinkMedtronicPumpStatus medtronicPumpStatus;
    private final MedLinkMedtronicPumpPlugin medtronicPumpPlugin;

    @Inject
    public MedLinkMedtronicUIPostprocessor(
            AAPSLogger aapsLogger,
            RxBus rxBus,
            ResourceHelper resourceHelper,
            MedLinkMedtronicUtil medtronicUtil,
            MedLinkMedtronicPumpStatus medtronicPumpStatus,
            MedLinkMedtronicPumpPlugin medtronicPumpPlugin) {
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.medtronicUtil = medtronicUtil;
        this.medtronicPumpStatus = medtronicPumpStatus;
        this.medtronicPumpPlugin = medtronicPumpPlugin;
    }


    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
//    void postProcessData(MedLinkMedtronicUITask uiTask) {
//
//        switch (uiTask.commandType) {
//
////            case SetBasalProfileSTD: {
////                Boolean response = (Boolean) uiTask.returnData;
////
////                if (response) {
////                    BasalProfile basalProfile = (BasalProfile) uiTask.getParameter(0);
////
////                    medtronicPumpStatus.basalsByHour = basalProfile.getProfilesByHour(medtronicPumpPlugin.getPumpDescription().pumpType);
////                }
////            }
////            break;
//
//            case GetBasalProfileSTD: {
////                MedLinkStandardReturn<BasalProfile> returned = (MedLinkStandardReturn<BasalProfile>) uiTask.returnData;
////                BasalProfile basalProfile = returned.getFunctionResult();
////
////                try {
////                    double[] profilesByHour =
////                            basalProfile.getProfilesByHour(medtronicPumpPlugin.getPumpDescription().getPumpType());
////
////                    if (profilesByHour != null) {
////                        medtronicPumpStatus.setBasalsByHour(profilesByHour);
////                        medtronicPumpStatus.basalProfileStatus = BasalProfileStatus.ProfileOK;
////                        rxBus.send(new EventMedtronicPumpValuesChanged());
////                    } else {
////                        uiTask.responseType = MedtronicUIResponseType.Error;
////                        uiTask.errorDescription = "No profile found.";
////                        aapsLogger.error("Basal Profile was NOT valid. [{}]", basalProfile.basalProfileToStringError());
////                    }
////                } catch (Exception ex) {
////                    aapsLogger.error("Basal Profile was returned, but was invalid. [{}]", basalProfile.basalProfileToStringError());
////                    uiTask.responseType = MedtronicUIResponseType.Error;
////                    uiTask.errorDescription = "No profile found.";
////               | }
//            }
//            break;
//
//            case SetBolus: {
//                medtronicPumpStatus.setLastBolusAmount(uiTask.getDoubleFromParameters(0));
//                medtronicPumpStatus.setLastDataTime(System.currentTimeMillis());
//
//            }
//            break;
//
////            case GetRemainingInsulin: {
////                medtronicPumpStatus.reservoirRemainingUnits = (Float) uiTask.returnData;
////            }
////            break;
////
////            case CancelTBR: {
////                medtronicPumpStatus.tempBasalStart = null;
////                medtronicPumpStatus.tempBasalAmount = null;
////                medtronicPumpStatus.tempBasalLength = null;
////            }
////            break;
//
//            case GetRealTimeClock: {
//                processTime(uiTask);
//            }
//            break;
//
////            case SetRealTimeClock: {
////                boolean response = (Boolean) uiTask.returnData;
////
////                aapsLogger.debug(LTag.PUMP, "New time was {} set.", response ? "" : "NOT");
////
////                if (response) {
////                    medtronicUtil.getPumpTime().timeDifference = 0;
////                }
////            }
////            break;
//
//
////            case GetBatteryStatus: {
////                BatteryStatusDTO batteryStatusDTO = (BatteryStatusDTO) uiTask.returnData;
////
////                medtronicPumpStatus.batteryRemaining = batteryStatusDTO.getCalculatedPercent(medtronicPumpStatus.batteryType);
////
////                if (batteryStatusDTO.voltage != null) {
////                    medtronicPumpStatus.batteryVoltage = batteryStatusDTO.voltage;
////                }
////
////                aapsLogger.debug(LTag.PUMP, "BatteryStatus: {}", batteryStatusDTO.toString());
////
////            }
////            break;
//
//            case PumpModel: {
//                if (medtronicPumpStatus.medtronicDeviceType != medtronicUtil.getMedtronicPumpModel()) {
//                    aapsLogger.warn(LTag.PUMP, "Configured pump is different then pump detected !");
//                    medtronicUtil.sendNotification(MedtronicNotificationType.PumpTypeNotSame, resourceHelper, rxBus);
//                }
//            }
//            break;
//
//            case Settings_512:
//            case Settings: {
//                postProcessSettings(uiTask);
//            }
//            break;
//
//            // no postprocessing
//
//            default:
//                break;
//                //aapsLogger.error(LTag.PUMP, "Post-processing not implemented for {}.", uiTask.commandType.name());
//
//        }
//        aapsLogger.info(LTag.PUMPBTCOMM,uiTask.commandType.command.code);
//        aapsLogger.info(LTag.PUMPBTCOMM,uiTask.commandType.commandDescription);
//        rxBus.send(new EventMedtronicPumpValuesChanged());
//
//    }


//    private void processTime(MedLinkMedtronicUITaskCp uiTask) {
//
//        ClockDTO clockDTO = (ClockDTO) uiTask.returnData;
//
//        Duration dur = new Duration(clockDTO.getPumpTime().toDateTime(DateTimeZone.UTC),
//                clockDTO.getLocalDeviceTime().toDateTime(DateTimeZone.UTC));
//
//        clockDTO.setTimeDifference((int) dur.getStandardSeconds());
//
//        medtronicUtil.setPumpTime(clockDTO);
//
//        aapsLogger.debug(LTag.PUMP,
//                "Pump Time: " + clockDTO.getLocalDeviceTime() + ", DeviceTime=" + clockDTO.getPumpTime() + //
//                ", diff: " + dur.getStandardSeconds() + " s");
//
////        if (dur.getStandardMinutes() >= 10) {
////            if (isLogEnabled())
////                LOG.warn("Pump clock needs update, pump time: " + clockDTO.pumpTime.toString("HH:mm:ss") + " (difference: "
////                        + dur.getStandardSeconds() + " s)");
////            sendNotification(MedtronicNotificationType.PumpWrongTimeUrgent);
////        } else if (dur.getStandardMinutes() >= 4) {
////            if (isLogEnabled())
////                LOG.warn("Pump clock needs update, pump time: " + clockDTO.pumpTime.toString("HH:mm:ss") + " (difference: "
////                        + dur.getStandardSeconds() + " s)");
////            sendNotification(MedtronicNotificationType.PumpWrongTimeNormal);
////        }
//
//    }


//    private void postProcessSettings(MedLinkMedtronicUITaskCp uiTask) {
//
//        Map<String, PumpSettingDTO> settings = (Map<String, PumpSettingDTO>) uiTask.returnData;
//
//        medtronicUtil.setSettings(settings);
//
//        PumpSettingDTO checkValue;
//
//        medtronicPumpPlugin.getRileyLinkService().verifyConfiguration();
//
//        // check profile
//        if (!"Yes".equals(settings.get("PCFG_BASAL_PROFILES_ENABLED").getValue())) {
//            aapsLogger.error(LTag.PUMP, "Basal profiles are not enabled on pump.");
//            medtronicUtil.sendNotification(MedtronicNotificationType.PumpBasalProfilesNotEnabled, resourceHelper, rxBus);
//
//        } else {
//            checkValue = settings.get("PCFG_ACTIVE_BASAL_PROFILE");
//
//            if (!"STD".equals(checkValue.getValue())) {
//                aapsLogger.error("Basal profile set on pump is incorrect (must be STD).");
//                medtronicUtil.sendNotification(MedtronicNotificationType.PumpIncorrectBasalProfileSelected, resourceHelper, rxBus);
//            }
//        }
//
//        // TBR
//
//        checkValue = settings.get("PCFG_TEMP_BASAL_TYPE");
//
//        if (!"Units".equals(checkValue.getValue())) {
//            aapsLogger.error("Wrong TBR type set on pump (must be Absolute).");
//            medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongTBRTypeSet, resourceHelper, rxBus);
//        }
//
//        // MAXes
//
//        checkValue = settings.get("PCFG_MAX_BOLUS");
//
//        if (!medtronicUtil.isSame(Double.parseDouble(checkValue.getValue()),
//                medtronicPumpStatus.maxBolus)) {
//            aapsLogger.error("Wrong Max Bolus set on Pump (current={}, required={}).",
//                    checkValue.getValue(), medtronicPumpStatus.maxBolus);
//            medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongMaxBolusSet, resourceHelper, rxBus, medtronicPumpStatus.maxBolus);
//        }
//
//        checkValue = settings.get("PCFG_MAX_BASAL");
//
//        if (!medtronicUtil.isSame(Double.parseDouble(checkValue.getValue()),
//                medtronicPumpStatus.maxBasal)) {
//            aapsLogger.error("Wrong Max Basal set on Pump (current={}, required={}).",
//                    checkValue.getValue(), medtronicPumpStatus.maxBasal);
//            medtronicUtil.sendNotification(MedtronicNotificationType.PumpWrongMaxBasalSet, resourceHelper, rxBus, medtronicPumpStatus.maxBasal);
//        }
//
//    }
}
