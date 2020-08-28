package info.nightscout.androidaps.plugins.pump.medtronic;


import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.joda.time.LocalDateTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.MicrobolusPumpInterface;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.pump.common.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.events.EventRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUITask;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedtronicHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicroBolusPair;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicrobolusOperations;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicNotificationType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicStatusRefreshType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


/**
 * Created by dirceu on 10.07.2020.
 */
public class MedLinkMedtronicPumpPlugin extends PumpPluginAbstract implements PumpInterface, MicrobolusPumpInterface {

    private final SP sp;
    private final MedtronicUtil medtronicUtil;
    private final MedtronicPumpStatus medtronicPumpStatus;
    private final MedtronicHistoryData medtronicHistoryData;

    private final RileyLinkServiceData medlinkServiceData;

    private RileyLinkMedtronicService medlinkService;

    private final ServiceTaskExecutor serviceTaskExecutor;


    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean isRefresh = false;
    private Map<MedtronicStatusRefreshType, Long> statusRefreshMap = new HashMap<>();
    private boolean isInitialized = false;
    private PumpHistoryEntry lastPumpHistoryEntry;

    public static boolean isBusy = false;
    private List<Long> busyTimestamps = new ArrayList<>();
    private boolean hasTimeDateOrTimeZoneChanged = false;
    private TempBasalMicrobolusOperations tempbasalMicrobolusOperations;

    private PumpDescription pumpDescription = new PumpDescription();

    @Inject
    public MedLinkMedtronicPumpPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            Context context,
            ResourceHelper resourceHelper,
            ActivePluginProvider activePlugin,
            SP sp,
            CommandQueueProvider commandQueue,
            FabricPrivacy fabricPrivacy,
            MedtronicUtil medtronicUtil,
            MedtronicPumpStatus medtronicPumpStatus,
            MedtronicHistoryData medtronicHistoryData,
            RileyLinkServiceData rileyLinkServiceData,
            ServiceTaskExecutor serviceTaskExecutor,
            DateUtil dateUtil
    ) {

        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(MedtronicFragment.class.getName()) //
                        .pluginName(R.string.medtronic_name) //
                        .shortName(R.string.medtronic_name_short) //
                        .preferencesId(R.xml.pref_medtronic)
                        .description(R.string.description_pump_medtronic), //
                PumpType.Medlink_Medtronic_554_754_Veo, // we default to most basic model, correct model from config is loaded later
                injector, resourceHelper, aapsLogger, commandQueue, rxBus, activePlugin, sp, context, fabricPrivacy, dateUtil
        );

        this.medtronicUtil = medtronicUtil;
        this.sp = sp;
        this.medtronicPumpStatus = medtronicPumpStatus;
        this.medtronicHistoryData = medtronicHistoryData;
        this.medlinkServiceData = rileyLinkServiceData;
        this.serviceTaskExecutor = serviceTaskExecutor;
        this.tempbasalMicrobolusOperations = new TempBasalMicrobolusOperations();
        displayConnectionMessages = false;

        serviceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkMedtronicService is disconnected");
//                    rileyLinkMedtronicService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkMedtronicService is connected");
                RileyLinkMedtronicService.LocalBinder mLocalBinder = (RileyLinkMedtronicService.LocalBinder) service;
//                    rileyLinkMedtronicService = mLocalBinder.getServiceInstance();

                new Thread(() -> {

                    for (int i = 0; i < 20; i++) {
                        SystemClock.sleep(5000);

                        aapsLogger.debug(LTag.PUMP, "Starting Medtronic-RileyLink service");
//                            if (rileyLinkMedtronicService.setNotInPreInit()) {
//                                break;
//                            }
                    }
                }).start();
            }
        };
    }

    @Override
    public boolean isFakingTempsByMicroBolus() {
        return tempbasalMicrobolusOperations != null && tempbasalMicrobolusOperations.getRemainingOperations() > 0;
    }


    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
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

    @NonNull @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
//        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setNewBasalProfile");
//
//        // this shouldn't be needed, but let's do check if profile setting we are setting is same as current one
//        if (isProfileSame(profile)) {
//            return new PumpEnactResult(getInjector()) //
//                    .success(true) //
//                    .enacted(false) //
//                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_basal_profile_not_set_is_same));
//        }
//
//        setRefreshButtonEnabled(false);
//
//        if (isPumpNotReachable()) {
//
//            setRefreshButtonEnabled(true);
//
//            return new PumpEnactResult(getInjector()) //
//                    .success(false) //
//                    .enacted(false) //
//                    .comment(getResourceHelper().gs(R.string.medtronic_pump_status_pump_unreachable));
//        }
//
//        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);
//
//        BasalProfile basalProfile = convertProfileToMedtronicProfile(profile);
//
//        String profileInvalid = isProfileValid(basalProfile);
//
//        if (profileInvalid != null) {
//            return new PumpEnactResult(getInjector()) //
//                    .success(false) //
//                    .enacted(false) //
//                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_set_profile_pattern_overflow, profileInvalid));
//        }
//
//        MedtronicUITask responseTask = rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.SetBasalProfileSTD,
//                basalProfile);
//
//        Boolean response = (Boolean) responseTask.returnData;
//
//        aapsLogger.info(LTag.PUMP, getLogPrefix() + "Basal Profile was set: " + response);
//
//        if (response) {
//            return new PumpEnactResult(getInjector()).success(true).enacted(true);
//        } else {
//            return new PumpEnactResult(getInjector()).success(response).enacted(response) //
//                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_basal_profile_could_not_be_set));
//        }

        return new PumpEnactResult(getInjector()).success(false).enacted(false).
                comment(getResourceHelper().gs(
                        R.string.medtronic_error_pump_basal_profiles_not_enabled));
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
    public void stopBolusDelivering() {
    }

    private void setRefreshButtonEnabled(boolean enabled) {
        rxBus.send(new EventRefreshButtonState(enabled));
    }

    private boolean isPumpNotReachable() {

        RileyLinkServiceState rileyLinkServiceState = medlinkServiceData.rileyLinkServiceState;

        if (rileyLinkServiceState == null) {
            aapsLogger.debug(LTag.PUMP, "RileyLink unreachable. RileyLinkServiceState is null.");
            return false;
        }

        if (rileyLinkServiceState != RileyLinkServiceState.PumpConnectorReady //
                && rileyLinkServiceState != RileyLinkServiceState.RileyLinkReady //
                && rileyLinkServiceState != RileyLinkServiceState.TuneUpDevice) {
            aapsLogger.debug(LTag.PUMP, "RileyLink unreachable.");
            return false;
        }

        return false;
//        return (!rileyLinkMedtronicService.getDeviceCommunicationManager().isDeviceReachable());
    }

    private String getLogPrefix() {
        return "MedLinkMedtronicPumpPlugin::";
    }


    private TempBasalPair readTBR() {
        MedtronicUITask responseTask = null;//rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.ReadTemporaryBasal);

        if (responseTask.hasData()) {
            TempBasalPair tbr = (TempBasalPair) responseTask.returnData;

            // we sometimes get rate returned even if TBR is no longer running
            if (tbr.getDurationMinutes() == 0) {
                tbr.setInsulinRate(0.0d);
            }

            return tbr;
        } else {
            return null;
        }
    }

    private void finishAction(String overviewKey) {

        if (overviewKey != null)
            rxBus.send(new EventRefreshOverview(overviewKey, false));

        triggerUIChange();

        setRefreshButtonEnabled(true);
    }


    private void incrementStatistics(String statsKey) {
        long currentCount = sp.getLong(statsKey, 0L);
        currentCount++;
        sp.putLong(statsKey, currentCount);
    }

    // if enforceNew===true current temp basal is canceled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
                                                boolean enforceNew) {

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);

            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute: rate: " + absoluteRate + ", duration=" + durationInMinutes);

        // read current TBR
        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            aapsLogger.warn(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - Could not read current TBR, canceling operation.");
            finishAction("TBR");
            return new PumpEnactResult(getInjector()).success(false).enacted(false)
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_cant_read_tbr));
        } else {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute: Current Basal: duration: " + tbrCurrent.getDurationMinutes() + " min, rate=" + tbrCurrent.getInsulinRate());
        }

        if (!enforceNew) {

            if (medtronicUtil.isSame(tbrCurrent.getInsulinRate(), absoluteRate)) {

                boolean sameRate = true;
                if (medtronicUtil.isSame(0.0d, absoluteRate) && durationInMinutes > 0) {
                    // if rate is 0.0 and duration>0 then the rate is not the same
                    sameRate = false;
                }

                if (sameRate) {
                    aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                    finishAction("TBR");
                    return new PumpEnactResult(getInjector()).success(true).enacted(false);
                }
            }
            // if not the same rate, we cancel and start new
        }

        // if TBR is running we will cancel it.
        if (tbrCurrent.getInsulinRate() != 0.0f && tbrCurrent.getDurationMinutes() > 0) {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - TBR running - so canceling it.");

            // CANCEL

            MedtronicUITask responseTask2 = null;//rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.CancelTBR);

            Boolean response = (Boolean) responseTask2.returnData;

            if (response) {
                aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - Current TBR cancelled.");
            } else {
                aapsLogger.error(getLogPrefix() + "setTempBasalAbsolute - Cancel TBR failed.");

                finishAction("TBR");

                return new PumpEnactResult(getInjector()).success(false).enacted(false)
                        .comment(getResourceHelper().gs(R.string.medtronic_cmd_cant_cancel_tbr_stop_op));
            }
        }

        // now start new TBR
        MedtronicUITask responseTask = null;//rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.SetTemporaryBasal,
        //absoluteRate, durationInMinutes);

        Boolean response = (Boolean) responseTask.returnData;

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - setTBR. Response: " + response);

        if (response) {
            // FIXME put this into UIPostProcessor
            medtronicPumpStatus.tempBasalStart = new Date();
            medtronicPumpStatus.tempBasalAmount = absoluteRate;
            medtronicPumpStatus.tempBasalLength = durationInMinutes;

            TemporaryBasal tempStart = new TemporaryBasal(getInjector()) //
                    .date(System.currentTimeMillis()) //
                    .duration(durationInMinutes) //
                    .absolute(absoluteRate) //
                    .source(Source.USER);

            activePlugin.getActiveTreatments().addToHistoryTempBasal(tempStart);

            incrementStatistics(MedtronicConst.Statistics.TBRsSet);

            finishAction("TBR");

            return new PumpEnactResult(getInjector()).success(true).enacted(true) //
                    .absolute(absoluteRate).duration(durationInMinutes);

        } else {
            finishAction("TBR");

            return new PumpEnactResult(getInjector()).success(false).enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_tbr_could_not_be_delivered));
        }

    }

    private PumpEnactResult clearTempBasal(PumpEnactResult result) {
        //TODO implement
        this.tempbasalMicrobolusOperations.clearOperations();
        return result;
    }

    private LinkedList<TempBasalMicroBolusPair> buildSuspensionScheduler(Integer totalSuspendedMinutes,
                                                                         Integer suspensions, Integer interval) {
        LinkedList<TempBasalMicroBolusPair> operations = new LinkedList<>();
        LocalDateTime time = getCurrentTime();
        LocalDateTime operation = time.plusMinutes(totalSuspendedMinutes.intValue());
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

    private PumpEnactResult scheduleSuspension(Integer percent, Integer durationInMinutes,
                                               Profile profile, PumpEnactResult result) {
        Integer totalSuspendedMinutes = (int) Math.round(durationInMinutes * (1 - (percent / 100d)));
        if (totalSuspendedMinutes < Constants.MIN_INTERVAL_BETWEEN_TEMP_SMB) {
            Integer suspensions = 1;
            Integer interval = durationInMinutes / suspensions;
            LinkedList<TempBasalMicroBolusPair> operations = this.buildSuspensionScheduler(
                    totalSuspendedMinutes, suspensions, interval);
            this.tempbasalMicrobolusOperations.updateOperations(suspensions, 0d,
                    operations, totalSuspendedMinutes);
        } else if (totalSuspendedMinutes % 5 == 0) {
            Integer suspensions = new Double(
                    totalSuspendedMinutes / Constants.MIN_INTERVAL_BETWEEN_TEMP_SMB).intValue();
            Integer interval = durationInMinutes / suspensions;
            LinkedList<TempBasalMicroBolusPair> operations = this.buildSuspensionScheduler(
                    totalSuspendedMinutes, suspensions, interval);
            this.tempbasalMicrobolusOperations.updateOperations(suspensions, 0d,
                    operations, totalSuspendedMinutes);
        } else {
            Integer suspensions = new Double(
                    totalSuspendedMinutes / Constants.MIN_INTERVAL_BETWEEN_TEMP_SMB).intValue();
            Integer interval = durationInMinutes / suspensions;
            //TODO need to reavaluate some cases, used floor here to avoid two possible up rounds
            Integer suspensionsQuantity = (int) Math.round(totalSuspendedMinutes / durationInMinutes);
            LinkedList<TempBasalMicroBolusPair> operations = this.buildSuspensionScheduler(
                    totalSuspendedMinutes, suspensions, interval);
            this.tempbasalMicrobolusOperations.updateOperations(suspensionsQuantity, 0d,
                    operations,
                    totalSuspendedMinutes);

        }
        //criar fila de comandos aqui, esta fila deverá ser consumida a cada execução de checagem de status
        return result;
    }

    private TempBasalMicrobolusOperations buildFirstLevelTempBasalMicroBolusOperations(
            Integer percent, LinkedList<Profile.ProfileValue> basalProfilesFromTemp, Integer durationInMinutes) {
        List<TempBasalPair> insulinPeriod = new ArrayList<>();
        Profile.ProfileValue previous = null;
        Double totalAmount = 0d;
        LocalDateTime time = getCurrentTime();
        int startedTime = time.getHourOfDay() * 3600 + time.getMinuteOfHour() * 60 + time.getSecondOfMinute();
        while (!basalProfilesFromTemp.isEmpty()) {
            Profile.ProfileValue currentProfileValue = basalProfilesFromTemp.pollFirst();
            if (previous == null) {
                previous = currentProfileValue;
                int delta = 0;
                if (basalProfilesFromTemp.isEmpty()) {
                    delta = durationInMinutes;
                    totalAmount = calcTotalAmount(percent, currentProfileValue.value, delta);
                    TempBasalPair tempBasalPair = createTempBasalPair(totalAmount, delta);
                    insulinPeriod.add(tempBasalPair);
                    previous.timeAsSeconds = startedTime;
                    continue;
                }
            }
            final Double deltaPreviousBasal = (currentProfileValue.timeAsSeconds
                    - previous.timeAsSeconds) / 3600d;
//            final Double deltaPreviousBasal = deltaPreviousBasalMinutes / (60d);
            final double profileDosage = deltaPreviousBasal * previous.value * ((percent / 100) - 1);
            totalAmount += profileDosage;
            insulinPeriod.add(new TempBasalPair(profileDosage, false, deltaPreviousBasal.intValue()));
        }

        double roundedTotalAmount = new BigDecimal(totalAmount.toString()).setScale(1,
                RoundingMode.HALF_UP).doubleValue();
        if (roundedTotalAmount == getPumpType().getBolusSize()) {
            LinkedList<TempBasalMicroBolusPair> tempBasalList = new LinkedList<TempBasalMicroBolusPair>();
            tempBasalList.add(new TempBasalMicroBolusPair(0, totalAmount,
                    getCurrentTime().plusMinutes(durationInMinutes / 2),
                    TempBasalMicroBolusPair.OperationType.BOLUS));
            return new TempBasalMicrobolusOperations(1, totalAmount, tempBasalList);
        } else if (roundedTotalAmount < getPumpType().getBolusSize()) {
            return new TempBasalMicrobolusOperations(0, 0, new LinkedList<>());
        } else {
            return buildTempBasalSMBOperations(totalAmount, insulinPeriod);
        }

    }

    private TempBasalPair createTempBasalPair(Double totalAmount, Integer durationInMinutes) {
        return new TempBasalPair(totalAmount, false, durationInMinutes);
    }

    private Double calcTotalAmount(Integer percent, double value, int durationInMinutes) {
        return (percent.doubleValue() / 100 - 1) * value * (durationInMinutes / 60d);
    }

    public LocalDateTime getCurrentTime() {
        return LocalDateTime.now();
    }

    private TempBasalMicrobolusOperations buildTempBasalSMBOperations(double totalAmount,
                                                                      List<TempBasalPair> insulinPeriod) {
        TempBasalMicrobolusOperations result = new TempBasalMicrobolusOperations();
        LocalDateTime operationTime = getCurrentTime();
        double accumulatedNextPeriodDose = 0d;
        for (TempBasalPair period : insulinPeriod) {
            double periodDose = period.getInsulinRate() + accumulatedNextPeriodDose;
            int time = period.getDurationMinutes();
            int periodAvailableOperations = time / Constants.MIN_INTERVAL_BETWEEN_TEMP_SMB;
            double minBolusDose = getPumpType().getBolusSize();
            if (periodDose >= minBolusDose) {
                int doses = new BigDecimal(
                        new Double(periodDose / minBolusDose).toString()
                ).setScale(0, RoundingMode.HALF_DOWN).intValue();
                double doseAmount = new BigDecimal(totalAmount / doses).setScale(2, RoundingMode.HALF_DOWN).doubleValue();
                if (doses > periodAvailableOperations) {

                } else {
                    double doseInterval = periodAvailableOperations / (double) doses;
                    //fazer a parte onde doses são menores que o intervalo
                    //fazer a parte da
                    double mod = periodAvailableOperations % doses;
                    List<Integer> list = buildOperations(doses, periodAvailableOperations, Collections.emptyList());
                    for (int dose : list) {
                        if (dose == 1) {
                            TempBasalMicroBolusPair pair = new TempBasalMicroBolusPair(0, doseAmount, operationTime, TempBasalMicroBolusPair.OperationType.BOLUS);
                            result.operations.add(pair);
                        }
                        operationTime = operationTime.plusMinutes(Constants.MIN_INTERVAL_BETWEEN_TEMP_SMB);
                    }
//                    for (int count = 0; count < doses; count++) {
//                        TempBasalMicroBolusPair pair = null;
//                        if (count == Math.floorDiv(doses, 2)) {
//                            pair = new TempBasalMicroBolusPair(0, doseAmount + mod, operationTime, TempBasalMicroBolusPair.OperationType.BOLUS);
//                        } else {
//                            pair = new TempBasalMicroBolusPair(0, doseAmount, operationTime, TempBasalMicroBolusPair.OperationType.BOLUS);
//                        }
//
//                    }
                }
            } else {
                accumulatedNextPeriodDose += periodDose;
            }
        }
        return result;
    }

    private List<Integer> buildOperationsList(int doses, int operations, int dose) {
        Integer[] result = new Integer[operations];
        Arrays.fill(result, dose);
        return buildOperations(doses - operations, operations, Arrays.asList(result));
    }

    protected List<Integer> buildOperations(int doses, int operations, List<Integer> list) {
        if (list.isEmpty()) {
            Integer[] values = new Integer[operations];
            Arrays.fill(values, 0);
            list = Arrays.asList(values);
        }
        if (doses == 0) {
            return list;
        } else if (doses >= operations) {
            List<Integer> returnValue;
            if (list.isEmpty()) {
                return buildOperationsList(doses, operations, 1);
            } else {
                Integer val = list.get(0);
                return buildOperationsList(doses, operations, val + 1);
            }
//            return buildOperations(doses - operations, operations, returnValue);
        } else
            //TODO nao funciona pra muitas operacoes
            if (doses != operations) {
                double step = (double) operations / doses;
                if (doses == 1) {
                    int position = Math.floorDiv(operations, 2);
                    list.set(position, list.get(position) + 1);
                    return list;
                }
                if (step < 2.5) {
                    int diff = operations - doses;
                    if (diff == 3) {
                        int newStep = Double.valueOf(Math.floor(operations * 0.33)).intValue();

                        int secondPosition = 2 * newStep;
                        int thirdPosition = 3 * newStep;
                        return fillNot(list, newStep, secondPosition, thirdPosition);
                    } else if (diff == 2) {
                        int newStep = Double.valueOf(Math.floor(operations * 0.25)).intValue();
                        int half = operations / diff;
                        int secondPosition = half + newStep;
                        return fillNot(list, newStep, secondPosition);
                    } else if(diff == 1){
                        return fillNot(list, Math.floorDiv(operations,2));
                    }

                }
                double nextStep = step;
                double value = 0d;

                list.set(0, list.get(0) + 1);
                doses--;
//                operations--;
                for (int index = 1; index < operations; index++) {
                    if (doses > 0 && nextStep < index) {
                        doses--;
//                        Stream
                        list.set(index, list.get(index) + 1);
                        nextStep = index + step;
                        value = 0;
                    }
//                    else {
//                        walkedStep++;
//                        value = walkedStep * step;
//                    }
                }
                if (doses == 0) {
                    return list;
                } else {
                    return buildOperations(doses, operations, list);
                }
            } else {
                int step = operations / doses;
                int dif = operations - doses;
                int walkedStep = 0;
                List<Integer> result = new ArrayList<>();
                result.add(1);
                doses--;
                operations--;
                for (int index = 0; index < operations; index++) {
                    if (doses > 0 && walkedStep == step) {
                        doses--;
                        result.add(1);
                        walkedStep = 0;
                    } else {
                        walkedStep++;
                        result.add(0);
                    }
                }
                return result;
            }
    }

    private List<Integer> fillNot(List<Integer> list, Integer... exclusions) {
        List<Integer> result = new ArrayList<>();
        int index = 0;
        for (Integer value : list) {
            if (!Arrays.asList(exclusions).contains(index)) {
                result.add(value + 1);
            } else {
                result.add(value);
            }
            index++;
        }
        return result;
    }

    @NonNull @Override public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        return super.deliverTreatment(detailedBolusInfo);
    }

    private PumpEnactResult scheduleTempBasalBolus(Integer percent, Integer durationInMinutes,
                                                   Profile profile, PumpEnactResult result) {
        double currentBasal = profile.getBasal();
        LocalDateTime currentTime = getCurrentTime();
        Integer currentTas = currentTime.getMillisOfDay() / 1000;
        Long endTas = currentTas + (durationInMinutes * 60l);
        LinkedList<Profile.ProfileValue> basalProfilesFromTemp = extractTempProfiles(profile, endTas, currentTas);

        List<TempBasalMicroBolusPair> operations = Collections.emptyList();

        tempbasalMicrobolusOperations = buildFirstLevelTempBasalMicroBolusOperations(percent, basalProfilesFromTemp, durationInMinutes);

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

        return result;
    }

    private LinkedList<Profile.ProfileValue> extractTempProfiles(Profile profile, Long endTas, Integer currentTas) {
        LinkedList<Profile.ProfileValue> tempBasalProfiles = new LinkedList<>();
        Profile.ProfileValue previousProfile = null;
        Profile.ProfileValue[] basalValues = profile.getBasalValues();
        for (Profile.ProfileValue basalValue : basalValues) {
            if (endTas < basalValue.timeAsSeconds) {
                if (previousProfile == null || endTas > previousProfile.timeAsSeconds) {
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

    protected PumpEnactResult buildPumpEnactResult() {
        return new PumpEnactResult(getInjector());
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {

        PumpEnactResult result = buildPumpEnactResult();
        if (percent == 100) {
            result = clearTempBasal(result);
        } else if (percent < 100) {
            result = scheduleSuspension(percent, durationInMinutes, profile, result);
        } else {
            result = scheduleTempBasalBolus(percent, durationInMinutes, profile, result);
        }
//        result.success = false;
//        result.comment = MainApp.gs(R.string.pumperror);
        aapsLogger.debug("Settings temp basal percent: " + result);
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = buildPumpEnactResult();
        result.success = false;
        medtronicUtil.sendNotification(MedtronicNotificationType.PumpExtendedBolusNotEnabled, getResourceHelper(), rxBus);
        aapsLogger.debug("Setting extended bolus: " + result);
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = false;
        medtronicUtil.sendNotification(MedtronicNotificationType.PumpExtendedBolusNotEnabled, getResourceHelper(), rxBus);
        aapsLogger.debug("Cancel temp basal: " + result);
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = false;
        medtronicUtil.sendNotification(MedtronicNotificationType.PumpExtendedBolusNotEnabled, getResourceHelper(), rxBus);
        aapsLogger.debug("Canceling extended bolus: " + result);
        return result;
    }

    @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.AndroidAPS;
    }

    @Override
    public PumpType model() {
        return PumpType.Medlink_Medtronic_554_754_Veo;
    }

    @Override
    public String serialNumber() {
        return medtronicPumpStatus.serialNumber;
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
    public void timezoneOrDSTChanged(TimeChangeType changeType) {
        aapsLogger.warn(LTag.PUMP, getLogPrefix() + "Time or TimeZone changed. ");
        this.hasTimeDateOrTimeZoneChanged = true;
    }


    @Nullable
    public RileyLinkMedtronicService getMedLinkService() {
        return medlinkService;
    }

    void resetStatusState() {
        firstRun = true;
        isRefresh = true;
    }
}
