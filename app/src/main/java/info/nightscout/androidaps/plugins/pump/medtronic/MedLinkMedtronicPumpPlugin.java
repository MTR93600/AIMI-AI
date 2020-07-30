package info.nightscout.androidaps.plugins.pump.medtronic;


import org.joda.time.DurationFieldType;
import org.joda.time.LocalTime;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.MicrobolusPumpInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.pump.common.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicroBolusPair;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicrobolusOperations;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.InstanceId;
import info.nightscout.androidaps.utils.TimeChangeType;


/**
 * Created by dirceu on 10.07.2020.
 */
public class MedLinkMedtronicPumpPlugin extends PumpPluginAbstract implements PumpInterface, MicrobolusPumpInterface {


    private static Logger log = LoggerFactory.getLogger(info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin.class);

    private static info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin plugin = null;

    private TempBasalMicrobolusOperations tempbasalMicrobolusOperations;

    public static info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin getPlugin() {
        if (plugin == null)
            plugin = new info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin();
        return plugin;
    }

    private PumpDescription pumpDescription = new PumpDescription();

    private MedLinkMedtronicPumpPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .pluginName(R.string.medlink)
                .description(R.string.description_pump_medlink)
        );
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.1d;

        pumpDescription.isExtendedBolusCapable = false;
        pumpDescription.isTempBasalCapable = true;
        pumpDescription.isSetBasalProfileCapable = false;
        pumpDescription.isRefillingCapable = true;
        tempbasalMicrobolusOperations = new TempBasalMicrobolusOperations();
    }


    @Override
    public boolean isFakingTempsByMicroBolus() {
        return tempbasalMicrobolusOperations != null && tempbasalMicrobolusOperations.getRemainingOperations() > 0;
    }


    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override
    public PumpEnactResult loadTDDs() {
        //no result, could read DB in the future?
        PumpEnactResult result = new PumpEnactResult();
        return result;
    }

    @Override public void initPumpStatusData() {

    }

    @Override public void onStartCustomActions() {

    }

    @Override public Class getServiceClass() {
        return null;
    }

    @Override public PumpStatus getPumpStatusData() {
        return null;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public boolean isHandshakeInProgress() {
        return false;
    }

    @Override
    public void finishHandshaking() {
    }

    @Override
    public void connect(String reason) {
    }

    @Override
    public void disconnect(String reason) {
    }

    @Override
    public void stopConnecting() {
    }

    @Override
    public void getPumpStatus() {
    }

    public TempBasalMicrobolusOperations getTempbasalMicrobolusOperations() {
        return tempbasalMicrobolusOperations;
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        // Do nothing here. we are using ConfigBuilderPlugin.getPlugin().getActiveProfile().getProfile();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        return false;
    }

    @Override
    public long lastDataTime() {
        return System.currentTimeMillis();
    }

    @Override
    public double getBaseBasalRate() {
        return 0d;
    }

    @Override
    public double getReservoirLevel() {
        return -1;
    }

    @Override
    public int getBatteryLevel() {
        return -1;
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.bolusDelivered = detailedBolusInfo.insulin;
        result.carbsDelivered = detailedBolusInfo.carbs;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);
        return result;
    }

    @Override
    public void stopBolusDelivering() {
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.gs(R.string.pumperror);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Setting temp basal absolute: " + result);
        return result;
    }

    private PumpEnactResult clearTempBasal(PumpEnactResult result) {
        //TODO implement
        this.tempbasalMicrobolusOperations.clearOperations();
        return result;
    }

    private List<TempBasalMicroBolusPair> buildSuspensionScheduler(Integer totalSuspendedMinutes,
                                                                   Integer suspensions, Long interval) {
        List<TempBasalMicroBolusPair> operations = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now();
        LocalDateTime operation = time.plusMinutes(totalSuspendedMinutes.longValue());
        for (int index = 0; index < suspensions; index++) {
            operations.add(new TempBasalMicroBolusPair(totalSuspendedMinutes, 0d, time,
                    TempBasalMicroBolusPair.OperationType.SUSPEND));
            operations.add(new TempBasalMicroBolusPair(totalSuspendedMinutes, 0d,
                    operation, TempBasalMicroBolusPair.OperationType.REACTIVATE));
            time = time.plusMinutes(interval);
            operation = operation.plusMinutes(interval);
        }
        return operations;
    }

    private PumpEnactResult scheduleSuspension(Integer percent, Long durationInMinutes,
                                               Profile profile, PumpEnactResult result) {
        Integer totalSuspendedMinutes = (int) Math.round(durationInMinutes * (1 - (percent / 100d)));
        if (totalSuspendedMinutes < Constants.MIN_INTERVAL_BETWEEN_TEMP_SMB) {
            Integer suspensions = 1;
            Long interval = durationInMinutes / suspensions;
            List<TempBasalMicroBolusPair> operations = this.buildSuspensionScheduler(
                    totalSuspendedMinutes, suspensions, interval);
            this.tempbasalMicrobolusOperations.updateOperations(suspensions, 0d,
                    operations, totalSuspendedMinutes);
        } else if (totalSuspendedMinutes % 5 == 0) {
            Integer suspensions = new Double(
                    totalSuspendedMinutes / Constants.MIN_INTERVAL_BETWEEN_TEMP_SMB).intValue();
            Long interval = durationInMinutes / suspensions;
            List<TempBasalMicroBolusPair> operations = this.buildSuspensionScheduler(
                    totalSuspendedMinutes, suspensions, interval);
            this.tempbasalMicrobolusOperations.updateOperations(suspensions, 0d,
                    operations, totalSuspendedMinutes);
        } else {
            Integer suspensions = new Double(
                    totalSuspendedMinutes / Constants.MIN_INTERVAL_BETWEEN_TEMP_SMB).intValue();
            Long interval = durationInMinutes / suspensions;
            //TODO need to reavaluate some cases, used floor here to avoid two possible up rounds
            Integer suspensionsQuantity = (int) Math.round(totalSuspendedMinutes / durationInMinutes);
            List<TempBasalMicroBolusPair> operations = this.buildSuspensionScheduler(
                    totalSuspendedMinutes, suspensions, interval);
            this.tempbasalMicrobolusOperations.updateOperations(suspensionsQuantity, 0d,
                    operations,
                    totalSuspendedMinutes);

        }
        //criar fila de comandos aqui, esta fila deverá ser consumida a cada execução de checagem de status
        return result;
    }

    private TempBasalMicrobolusOperations buildFirstLevelTempBasalMicroBolusOperations(
            Integer percent, LinkedList<Profile.ProfileValue> basalProfilesFromTemp,
            List<TempBasalPair> insulinPeriod) {
        Profile.ProfileValue previous = null;
        double totalAmount = 0d;
        while (!basalProfilesFromTemp.isEmpty()) {
            Profile.ProfileValue currentProfileValue = basalProfilesFromTemp.pollFirst();
            if (previous == null) {
                previous = currentProfileValue;
                continue;
            }
            final Integer deltaPreviousBasalMinutes = (previous.timeAsSeconds -
                    currentProfileValue.timeAsSeconds) / 60;
            final double deltaPreviousBasal = deltaPreviousBasalMinutes / (60);
            final double profileDosage = deltaPreviousBasal * previous.value * ((percent / 100) - 1);
            totalAmount += profileDosage;
            insulinPeriod.add(new TempBasalPair(profileDosage, false, deltaPreviousBasal.intValue()));
        }

        return buildTempBasalSMBOperations(totalAmount, insulinPeriod);
    }

    private TempBasalMicrobolusOperations buildTempBasalSMBOperations(double totalAmount,
                                                                      List<TempBasalPair> insulinPeriod) {

        new TempBasalMicrobolusOperations(0, totalAmount, insulinPeriod);
        return null;
    }

    private PumpEnactResult scheduleTempBasalBolus(Integer percent, Long durationInMinutes,
                                                   Profile profile, PumpEnactResult result) {
        double currentBasal = profile.getBasal();
        LocalTime currentTime = LocalTime.now();
        Integer currentTas = currentTime.getMillisOfDay() / 1000;
        Long endTas = currentTas + (durationInMinutes * 60l);
        LinkedList<Profile.ProfileValue> basalProfilesFromTemp = extractTempProfiles(profile, endTas, currentTas);

        List<TempBasalMicroBolusPair> operations = Collections.emptyList();
        List<TempBasalPair> insulinPeriod = Collections.emptyList();
        buildFirstLevelTempBasalMicroBolusOperations(percent, basalProfilesFromTemp, insulinPeriod);

        //            if(profileDosage < pumpDescription.bolusStep){
//                double bolusPercent = profileDosage / pumpDescription.bolusStep;
//                if(bolusPercent>= 0.75){
//
//                }
//            }
//            final double microBolusSteps = profileDosage / pumpDescription.bolusStep;
//            final double bolusSteps = deltaPreviousBasalMinutes /
//                    Constants.MIN_INTERVAL_BETWEEN_TEMP_SMB;
//            for (int index = 1; index < microBolusOperations; index ++){
//                operations.add(new TempBasalMicroBolusPair(0,,))
//            }

        return null;
    }

    private LinkedList<Profile.ProfileValue> extractTempProfiles(Profile profile, Long endTas, Integer currentTas) {
        LinkedList<Profile.ProfileValue> tempBasalProfiles = new LinkedList<>();
        Profile.ProfileValue previousProfile = null;
        Profile.ProfileValue[] basalValues = profile.getBasalValues();
        for (Profile.ProfileValue basalValue : basalValues) {
            if (endTas < basalValue.timeAsSeconds) {
                if (endTas > previousProfile.timeAsSeconds) {
                    tempBasalProfiles.add(basalValue);
                }
                break;
            }
            if (currentTas <= basalValue.timeAsSeconds) {
                if (tempBasalProfiles.isEmpty()) {
                    tempBasalProfiles.add(basalValue);
                } else if (basalValue.value != previousProfile.value) {
                    tempBasalProfiles.add(basalValue);
                }
            }
            previousProfile = basalValue;
        }
        if (endTas >= Constants.SECONDS_PER_DAY) {
            tempBasalProfiles.addAll(extractTempProfiles(profile, endTas - Constants.SECONDS_PER_DAY, 0));
        }
        return tempBasalProfiles;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Long durationInMinutes, Profile profile, boolean enforceNew) {

        PumpEnactResult result = new PumpEnactResult();
        if (percent == 100) {
            result = clearTempBasal(result);
        } else if (percent < 100) {
            result = scheduleSuspension(percent, durationInMinutes, profile, result);
        } else {
            result = scheduleTempBasalBolus(percent, durationInMinutes, profile, result);
        }
//        result.success = false;
//        result.comment = MainApp.gs(R.string.pumperror);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Settings temp basal percent: " + result);
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.gs(R.string.pumperror);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Setting extended bolus: " + result);
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.gs(R.string.pumperror);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Cancel temp basal: " + result);
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.comment = MainApp.gs(R.string.pumperror);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Canceling extended bolus: " + result);
        return result;
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        long now = System.currentTimeMillis();
        JSONObject pump = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            status.put("status", "normal");
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", profileName);
            } catch (Exception e) {
            }
            status.put("timestamp", DateUtil.toISOString(now));

            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("clock", DateUtil.toISOString(now));
        } catch (JSONException e) {
        }
        return pump;
    }

    @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.AndroidAPS;
    }

    @Override
    public PumpType model() {
        return PumpType.Medtronic_554_754_Veo_MedLink;
    }

    @Override
    public String serialNumber() {
        return InstanceId.INSTANCE.instanceId();
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        return model().getModel();
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return null;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {

    }

    @Override public void timezoneOrDSTChanged(TimeChangeType timeChangeType) {

    }

    @Override public boolean isUnreachableAlertTimeoutExceeded(long alertTimeoutMilliseconds) {
        return false;
    }

    @Override public boolean setNeutralTempAtFullHour() {
        return false;
    }

    @Override
    public boolean canHandleDST() {
        return true;
    }

    @Override protected PumpEnactResult deliverBolus(DetailedBolusInfo detailedBolusInfo) {
        return null;
    }

    @Override protected void triggerUIChange() {

    }

    @Override
    public void timeDateOrTimeZoneChanged() {

    }


}
