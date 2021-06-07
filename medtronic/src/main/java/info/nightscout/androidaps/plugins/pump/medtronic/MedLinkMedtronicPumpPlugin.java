package info.nightscout.androidaps.plugins.pump.medtronic;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.Seconds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.MedLinkTemporaryBasal;
import info.nightscout.androidaps.db.SensorDataReading;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.Constraint;
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
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.MedLinkPumpPluginAbstract;
import info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpStatusType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpTempBasalType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.events.EventRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BolusAnswer;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BolusMedLinkMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.PumpResponses;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.BGHistoryCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.BasalCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.BolusHistoryCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.ChangeStatusCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.IsigHistoryCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.ProfileCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.StatusCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedLinkMedtronicUITaskCp;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BasalMedLinkMessage;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.MedLinkModelParser;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkService;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedLinkMedtronicHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfileEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicroBolusDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicroBolusPair;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicrobolusOperations;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicStatusRefreshType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCustomActionType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicNotificationType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.medtronic.service.MedLinkMedtronicService;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicPumpPluginInterface;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.CustomCommand;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.ToastUtils;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import static info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicStatusRefreshType.PumpHistory;


/**
 * Created by dirceu on 10.07.2020.
 */
@Singleton
public class MedLinkMedtronicPumpPlugin extends MedLinkPumpPluginAbstract implements PumpInterface, MicrobolusPumpInterface, MedLinkPumpDevice, MedtronicPumpPluginInterface {

    private final SP sp;
    private final MedLinkMedtronicUtil medtronicUtil;
    private final MedLinkMedtronicPumpStatus medLinkPumpStatus;
    private final MedLinkMedtronicHistoryData medtronicHistoryData;

    private final MedLinkServiceData medLinkServiceData;

    private MedLinkMedtronicService medLinkService;

    private final ServiceTaskExecutor serviceTaskExecutor;

    private int missedBGs = 5;
    private long firstMissedBGTimestamp = 0l;
    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean isRefresh = false;
    private Map<MedLinkMedtronicStatusRefreshType, Long> statusRefreshMap = new HashMap<>();
    private boolean isInitialized = false;
    private PumpHistoryEntry lastPumpHistoryEntry;

    public static boolean isBusy = false;
    private List<Long> busyTimestamps = new ArrayList<>();
    private boolean hasTimeDateOrTimeZoneChanged = false;
    private TempBasalMicrobolusOperations tempbasalMicrobolusOperations;

    private Integer percent;
    private Integer durationInMinutes;
    private Profile profile;
    private PumpEnactResult result;
    private BolusDeliveryType bolusDeliveryType = BolusDeliveryType.Idle;
    private List<CustomAction> customActions = null;


    private String lastStatus = PumpStatusType.Initializing.getStatus();
    protected long lastBGHistoryRead = 0l;
    private BasalProfile basalProfile;
    private long lastPreviousHistory = 0l;
    private long lastTryToConnect = 0l;
    private long lastBolusTime;
    private Integer[] lastBgs;
    private int bgIndex = 0;
    private long lastBolusHistoryRead = 0l;
    private double lastDeliveredBolus = 0d;
    private Long bolusDeliveryTime = 0l;
    private long pumpTimeDelta = 0l;
    private DetailedBolusInfo lastDetailedBolusInfo;
    private Set<String> initCommands;


    @NotNull @Override public Constraint<Double> applyBasalConstraints(@NotNull Constraint<Double> absoluteRate, @NotNull Profile profile) {
        return null;
    }

    @org.jetbrains.annotations.Nullable public void stopPump(Callback callback) {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::stopPump - ");
        if (!medLinkPumpStatus.pumpStatusType.equals(PumpStatusType.Suspended)) {

            ChangeStatusCallback function = new ChangeStatusCallback(aapsLogger,
                    ChangeStatusCallback.OperationType.STOP, this);
            Function<Supplier<Stream<String>>, MedLinkStandardReturn<PumpDriverState>> startStopFunction =
                    function.andThen(f -> {
                        if (f.getFunctionResult() == PumpDriverState.Suspended) {
                            result = new PumpEnactResult(getInjector()).success(true).enacted(true);
                        } else if (f.getFunctionResult() == PumpDriverState.Initialized) {
                            result = new PumpEnactResult(getInjector()).success(false).enacted(true);
                        } else {
                            result = new PumpEnactResult(getInjector()).success(false).enacted(false);
                        }
                        sendPumpUpdateEvent();
                        callback.result(result).run();
                        return f;
                    });

            MedLinkPumpMessage message = new MedLinkPumpMessage(MedLinkCommandType.StopStartPump,
                    MedLinkCommandType.StopPump,
                    startStopFunction,
                    medLinkServiceData,
                    aapsLogger);
            medLinkService.getMedtronicUIComm().executeCommandCP(message);
        }
    }

    @org.jetbrains.annotations.Nullable public void startPump(Callback callback) {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::startPump - ");
        if (!medLinkPumpStatus.pumpStatusType.equals(PumpStatusType.Running)) {
            Function activity = new ChangeStatusCallback(aapsLogger,
                    ChangeStatusCallback.OperationType.START, this).andThen(f -> {
                if (f.getFunctionResult() == PumpDriverState.Initialized) {
                    result = new PumpEnactResult(getInjector()).success(true).enacted(true);
                } else if (f.getFunctionResult() == PumpDriverState.Suspended) {
                    result = new PumpEnactResult(getInjector()).success(false).enacted(true);
                } else {
                    result = new PumpEnactResult(getInjector()).success(false).enacted(false);
                }
                sendPumpUpdateEvent();
                callback.result(result).run();
                return f;
            });

            MedLinkPumpMessage message = new MedLinkPumpMessage(MedLinkCommandType.StopStartPump,
                    MedLinkCommandType.StartPump,
                    activity,
                    medLinkServiceData,
                    aapsLogger);
            medLinkService.getMedtronicUIComm().executeCommandCP(message);
        }
    }

    public void alreadyRunned() {
        firstRun = false;
    }


    protected enum StatusRefreshAction {
        Add, //
        GetData
    }

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
            MedLinkMedtronicUtil medtronicUtil,
            MedLinkMedtronicPumpStatus medLinkPumpStatus,
            MedLinkMedtronicHistoryData medtronicHistoryData,
            MedLinkServiceData medLinkServiceData,
            ServiceTaskExecutor serviceTaskExecutor,
            DateUtil dateUtil
    ) {

        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(MedLinkMedtronicFragment.class.getName()) //
                        .pluginName(R.string.medlink_medtronic_name) //
                        .pluginIcon(R.drawable.ic_veo_128)
                        .shortName(R.string.medlink_medtronic_name_short) //
                        .preferencesId(R.xml.pref_medtronic_medlink)
                        .description(R.string.description_pump_medtronic_medlink), //
                PumpType.MedLink_Medtronic_554_754_Veo, // we default to most basic model, correct model from config is loaded later
                injector, resourceHelper, aapsLogger, commandQueue, rxBus, activePlugin, sp, context, fabricPrivacy, dateUtil
        );

        this.medtronicUtil = medtronicUtil;
        this.sp = sp;
        this.medLinkPumpStatus = medLinkPumpStatus;
        this.medtronicHistoryData = medtronicHistoryData;
        this.medLinkServiceData = medLinkServiceData;
        this.serviceTaskExecutor = serviceTaskExecutor;
        this.tempbasalMicrobolusOperations = new TempBasalMicrobolusOperations();
        displayConnectionMessages = false;

        MedLinkStatusParser.parseStatus(new String[0], medLinkPumpStatus, getInjector());
        serviceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                aapsLogger.debug(LTag.PUMP, "MedLinkMedtronicService is disconnected");
                medLinkService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                aapsLogger.debug(LTag.PUMP, "MedLinkMedtronicService is connected");
                MedLinkMedtronicService.LocalBinder mLocalBinder = (MedLinkMedtronicService.LocalBinder) service;
                medLinkService = mLocalBinder.getServiceInstance();
                medLinkService.verifyConfiguration();

                new Thread(() -> {

                    for (int i = 0; i < 20; i++) {
                        SystemClock.sleep(5000);

                        aapsLogger.debug(LTag.PUMP, "Starting Medtronic-MedLink service");
                        if (medLinkService.setNotInPreInit()) {
                            aapsLogger.debug("MedlinkService setnotinpreinit");
                            break;
                        }
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

    @Override
    public void updatePreferenceSummary(@NotNull Preference pref) {
        super.updatePreferenceSummary(pref);

        if (pref.getKey().equals(getResourceHelper().gs(R.string.key_medlink_mac_address))) {
            String value = sp.getStringOrNull(R.string.key_medlink_mac_address, null);
            pref.setSummary(value == null ? getResourceHelper().gs(R.string.not_set_short) : value);
        }
    }

    @Override public void initPumpStatusData() {
        medLinkPumpStatus.lastConnection = sp.getLong(MedLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
        medLinkPumpStatus.lastDateTime = medLinkPumpStatus.lastConnection;
        medLinkPumpStatus.previousConnection = medLinkPumpStatus.lastConnection;

        //if (rileyLinkMedtronicService != null) rileyLinkMedtronicService.verifyConfiguration();

        aapsLogger.debug(LTag.PUMP, "initPumpStatusData: " + this.medLinkPumpStatus);

        // this is only thing that can change, by being configured
        pumpDescription.maxTempAbsolute = (medLinkPumpStatus.maxBasal != null) ? medLinkPumpStatus.maxBasal : 35.0d;

        pumpDescription.tempBasalStyle = PumpDescription.PERCENT;

        // set first Medtronic Pump Start
        if (!sp.contains(MedtronicConst.Statistics.FirstPumpStart)) {
            sp.putLong(MedtronicConst.Statistics.FirstPumpStart, System.currentTimeMillis());
        }

        migrateSettings();
    }

    private void migrateSettings() {

        if ("US (916 MHz)".equals(sp.getString(MedLinkMedtronicConst.Prefs.PumpFrequency, "US (916 MHz)"))) {
            sp.putString(MedLinkMedtronicConst.Prefs.PumpFrequency, getResourceHelper().gs(R.string.key_medtronic_pump_frequency_us_ca));
        }

        this.initCommands = sp.getStringSet(resourceHelper.gs(R.string.key_medlink_init_command), Collections.emptySet());

//        String encoding = sp.getString(MedtronicConst.Prefs.Encoding, "RileyLink 4b6b Encoding");
//
//        if ("RileyLink 4b6b Encoding".equals(encoding)) {
//            sp.putString(MedtronicConst.Prefs.Encoding, getResourceHelper().gs(R.string.key_medtronic_pump_encoding_4b6b_rileylink));
//        }
//
//        if ("Local 4b6b Encoding".equals(encoding)) {
//            sp.putString(MedtronicConst.Prefs.Encoding, getResourceHelper().gs(R.string.key_medtronic_pump_encoding_4b6b_local));
//        }
    }

    public void onStartCustomActions() {
        // check status every minute (if any status needs refresh we send readStatus command)
        new Thread(() -> {
            do {
                SystemClock.sleep(60000);

                if (this.isInitialized) {

                    Map<MedLinkMedtronicStatusRefreshType, Long> statusRefresh = workWithStatusRefresh(
                            StatusRefreshAction.GetData, null, null);


                    if (doWeHaveAnyStatusNeededRefreshing(statusRefresh)) {
                        if (!getCommandQueue().statusInQueue()) {
                            getCommandQueue().readStatus("Scheduled Status Refresh", null);
                        }
                    }

                    if (System.currentTimeMillis() - getPumpStatusData().lastConnection > 590000) {
                        readPumpHistory();
                    }

                    clearBusyQueue();
                }

            } while (serviceRunning);

        }).start();
    }

    private synchronized void clearBusyQueue() {

        if (busyTimestamps.size() == 0) {
            return;
        }

        Set<Long> deleteFromQueue = new HashSet<>();

        for (Long busyTimestamp : busyTimestamps) {

            if (System.currentTimeMillis() > busyTimestamp) {
                deleteFromQueue.add(busyTimestamp);
            }
        }

        if (deleteFromQueue.size() == busyTimestamps.size()) {
            busyTimestamps.clear();
            setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, false);
        }

        if (deleteFromQueue.size() > 0) {
            busyTimestamps.removeAll(deleteFromQueue);
        }
    }

    @Override public Class getServiceClass() {
        return MedLinkMedtronicService.class;
    }

    @Override public MedLinkPumpStatus getPumpStatusData() {
        return medLinkPumpStatus;
    }

    private boolean isServiceSet() {
        return medLinkService != null;
    }

    @Override
    public boolean isInitialized() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isInitialized");
        return isServiceSet() && isInitialized;

    }

    //TODO implement
    @Override
    public boolean isSuspended() {
        return getPumpStatusData().pumpStatusType == PumpStatusType.Suspended;
    }


    @Override
    public boolean isBusy() {
        return isBusy;
    }


    @Override public void setBusy(boolean busy) {

    }

    @Override public void triggerPumpConfigurationChangedEvent() {

    }

    @Override public MedLinkService getRileyLinkService() {
        return this.medLinkService;
    }

    @Override public MedLinkService getService() {
        return medLinkService;
    }

//    @Override public MedLinkService getMedLinkService() {
//        return medlinkService;
//    }

    @Override
    public boolean isConnected() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "MedLinkMedtronicPumpPlugin::isConnected");
        return isServiceSet() && medLinkService.isInitialized();
    }

    @Override
    public boolean isConnecting() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isConnecting");
        return !isServiceSet() || !medLinkService.isInitialized();
    }


    @Override
    public void finishHandshaking() {
    }

    @Override
    public void connect(String reason) {
//        medLinkService.getMedLinkRFSpy().e()
    }
//
//    @Override
//    public void disconnect(String reason) {
//    }
//
//    @Override
//    public void stopConnecting() {
//    }

    private boolean doWeHaveAnyStatusNeededRefreshing(Map<MedLinkMedtronicStatusRefreshType, Long> statusRefresh) {

        for (Map.Entry<MedLinkMedtronicStatusRefreshType, Long> refreshType : statusRefresh.entrySet()) {

            aapsLogger.info(LTag.PUMP, "Next Command " + Math.round(
                    (refreshType.getValue() - System.currentTimeMillis())));
            aapsLogger.info(LTag.PUMP, refreshType.getKey().name());

            if (((refreshType.getValue() -
                    System.currentTimeMillis())) < 30000L) {
                return true;
            }
        }
        for (TempBasalMicroBolusPair oper : tempbasalMicrobolusOperations.operations) {

            aapsLogger.info(LTag.PUMP, "Next Command " +
                    oper.getReleaseTime().toDateTime());
            aapsLogger.info(LTag.PUMP, oper.toString());

            if (oper.getReleaseTime().isAfter(LocalDateTime.now().minus(Seconds.seconds(30)))) {
                return true;
            }
        }

        return hasTimeDateOrTimeZoneChanged;
    }

    private Long getLastPumpEntryTime() {
        Long lastPumpEntryTime = sp.getLong(MedtronicConst.Statistics.LastPumpHistoryEntry, 0L);

        try {
            LocalDateTime localDateTime = DateTimeUtil.toLocalDateTime(lastPumpEntryTime);

            if (localDateTime.getYear() != (new GregorianCalendar().get(Calendar.YEAR))) {
                aapsLogger.warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid. Year was not the same.");
                return 0L;
            }

            return lastPumpEntryTime;

        } catch (Exception ex) {
            aapsLogger.warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid.");
            return 0L;
        }

    }


    private void readPumpHistoryLogic() {

        boolean debugHistory = false;

        LocalDateTime targetDate = null;

        if (lastPumpHistoryEntry == null) {

            if (debugHistory)
                aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: null");

            Long lastPumpHistoryEntryTime = getLastPumpEntryTime();

            LocalDateTime timeMinus36h = new LocalDateTime();
            timeMinus36h = timeMinus36h.minusHours(36);
            medtronicHistoryData.setIsInInit(true);

            if (lastPumpHistoryEntryTime == 0L) {
                if (debugHistory)
                    aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: 0L - targetDate: "
                            + targetDate);
                targetDate = timeMinus36h;
            } else {
                // LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);

                if (debugHistory)
                    aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: " + lastPumpHistoryEntryTime + " - targetDate: " + targetDate);

                medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntryTime);

                LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);

                lastHistoryRecordTime = lastHistoryRecordTime.minusHours(12); // we get last 12 hours of history to
                // determine pump state
                // (we don't process that data), we process only

                if (timeMinus36h.isAfter(lastHistoryRecordTime)) {
                    targetDate = timeMinus36h;
                }

                targetDate = (timeMinus36h.isAfter(lastHistoryRecordTime) ? timeMinus36h : lastHistoryRecordTime);

                if (debugHistory)
                    aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): targetDate: " + targetDate);
            }
        } else {
            if (debugHistory)
                aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: not null - " + medtronicUtil.gsonInstance.toJson(lastPumpHistoryEntry));
            medtronicHistoryData.setIsInInit(false);
            // medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntry.atechDateTime);

            // targetDate = lastPumpHistoryEntry.atechDateTime;
        }

        //aapsLogger.debug(LTag.PUMP, "HST: Target Date: " + targetDate);


        MedLinkPumpMessage msg = new MedLinkPumpMessage(MedLinkCommandType.GetState,
                MedLinkCommandType.NoCommand, new StatusCallback(aapsLogger, this,
                medLinkPumpStatus),
                medLinkServiceData,
                aapsLogger);
        medLinkService.getMedtronicUIComm().executeCommandCP(msg);

        if (debugHistory)
            aapsLogger.debug(LTag.PUMP, "HST: After task");

//        PumpHistoryResult historyResult = (PumpHistoryResult) responseTask2.returnData;

//        if (debugHistory)
//            aapsLogger.debug(LTag.PUMP, "HST: History Result: " + historyResult.toString());

//        PumpHistoryEntry latestEntry = historyResult.getLatestEntry();

//        if (debugHistory)
//            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "Last entry: " + latestEntry);

//        if (latestEntry == null) // no new history to read
//            return;

//        this.lastPumpHistoryEntry = latestEntry;
//        sp.putLong(MedtronicConst.Statistics.LastPumpHistoryEntry, latestEntry.atechDateTime);

//        if (debugHistory)
//            aapsLogger.debug(LTag.PUMP, "HST: History: valid=" + historyResult.validEntries.size() + ", unprocessed=" + historyResult.unprocessedEntries.size());
//
//        this.medtronicHistoryData.addNewHistory(historyResult);
        this.medtronicHistoryData.filterNewEntries();

        // determine if first run, if yes detrmine how much of update do we need
        // first run:
        // get last hiostory entry, if not there download 1.5 days of data
        // - there: check if last entry is older than 1.5 days
        // - yes: download 1.5 days
        // - no: download with last entry
        // - not there: download 1.5 days
        //
        // upload all new entries to NightScout (TBR, Bolus)
        // determine pump status
        //
        // save last entry
        //
        // not first run:
        // update to last entry
        // - save
        // - determine pump status
    }

    private void readPumpBGHistory(boolean force) {
        //TODO fazer ele não ficar lendo o histórico quando o sensor estiver em warmup
        if ((firstMissedBGTimestamp > 0l &&
                System.currentTimeMillis() - lastBGHistoryRead > 300000 &&
                (System.currentTimeMillis() - firstMissedBGTimestamp > 1800000) ||
                missedBGs > 4 || force)) {
            if (initCommands.contains(resourceHelper.gs(R.string.key_medlink_init_commands_last_bg_history))) {

                this.lastBGHistoryRead = System.currentTimeMillis();
                BGHistoryCallback func =
                        new BGHistoryCallback(getInjector(), this, aapsLogger, false);

                IsigHistoryCallback isigFunc =
                        new IsigHistoryCallback(getInjector(), this, aapsLogger, false, func);

                MedLinkPumpMessage msg = new MedLinkPumpMessage(MedLinkCommandType.BGHistory,
                        MedLinkCommandType.IsigHistory,
                        func,
                        isigFunc,
                        medLinkServiceData,
                        aapsLogger);
                medLinkService.getMedtronicUIComm().executeCommandCP(msg);
            }
//            MedLinkPumpMessage msg = new MedLinkPumpMessage(MedLinkCommandType.BGHistory);
//            medLinkService.getMedtronicUIComm().executeCommandCP(msg);
        }
    }

    protected void readPumpHistory() {
//        if (isLoggingEnabled())
//            LOG.error(getLogPrefix() + "readPumpHistory WIP.");

        scheduleNextReadState();
        aapsLogger.info(LTag.CONFIGBUILDER, "read pump history");
        readPumpHistoryLogic();

//        if (bgDelta == 1 || bgDelta > 5) {
//            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory);
//        } else if (bgDelta == 0) {
//            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory, 1);
//        } else {
//            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpHistory, -1 * bgDelta);
//        }

        if (medtronicHistoryData.hasRelevantConfigurationChanged()) {
            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.Configuration, -1);
        }

        if (medtronicHistoryData.hasPumpTimeChanged()) {
            scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpTime, -1);
        }

        if (this.medLinkPumpStatus.basalProfileStatus != BasalProfileStatus.NotInitialized
                && medtronicHistoryData.hasBasalProfileChanged()) {
            medtronicHistoryData.processLastBasalProfileChange(pumpDescription.pumpType, medLinkPumpStatus);
        }

        PumpDriverState previousState = this.pumpState;

        if (medtronicHistoryData.isPumpSuspended()) {
            this.pumpState = PumpDriverState.Suspended;
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isPumpSuspended: true");
        } else {
            if (previousState == PumpDriverState.Suspended) {
                this.pumpState = PumpDriverState.Ready;
            }
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isPumpSuspended: false");
        }

        medtronicHistoryData.processNewHistoryData();

        this.medtronicHistoryData.finalizeNewHistoryRecords();
        // this.medtronicHistoryData.setLastHistoryRecordTime(this.lastPumpHistoryEntry.atechDateTime);

    }

    protected int scheduleNextReadState() {
        if (getPumpStatusData().getLastBGTimestamp() != 0) {
            int bgDelta = Math.toIntExact((getPumpStatusData().getLastDateTime() -
                    getPumpStatusData().getLastBGTimestamp()) / 60000);

            pumpTimeDelta = (getPumpStatusData().getLastConnection() -
                    getPumpStatusData().getLastDateTime());
            int minutesDelta = Math.toIntExact(TimeUnit.MILLISECONDS.toMinutes(pumpTimeDelta));

            if (calculateBGDeltaAvg(bgDelta) > 6d) {
                minutesDelta++;
            }
            aapsLogger.info(LTag.CONFIGBUILDER, "Last Connection" + getPumpStatusData().lastConnection);
            aapsLogger.info(LTag.CONFIGBUILDER, "Next Delta " + minutesDelta);
            aapsLogger.info(LTag.CONFIGBUILDER, "bgdelta  " + bgDelta);
            aapsLogger.info(LTag.CONFIGBUILDER, "Last connection " + DateUtil.timeFullString(medLinkPumpStatus.lastConnection));
            aapsLogger.info(LTag.CONFIGBUILDER, "Last bg " + DateUtil.timeFullString(medLinkPumpStatus.lastBGTimestamp));

            while (bgDelta >= 5) {
                bgDelta -= 5;
            }
            int lostContactDelta = 0;
            if (getPumpStatusData().lastConnection - medLinkPumpStatus.lastBGTimestamp > 600000) {
                lostContactDelta = 1;
            }
            scheduleNextRefresh(PumpHistory, lostContactDelta + minutesDelta + 1 - bgDelta);
        } else {
            aapsLogger.info(LTag.CONFIGBUILDER, "scheduling");
            scheduleNextRefresh(PumpHistory,
                    0);
        }

        return 0;
    }

    private double calculateBGDeltaAvg(int bgDelta) {
        if (lastBgs == null) {
            lastBgs = new Integer[5];
            Arrays.fill(lastBgs, 0);
        }
        bgIndex++;
        if (bgIndex == 5) {
            bgIndex = 0;
        }
        lastBgs[bgIndex] = bgDelta;
        int sum = 0;
        for (int value : lastBgs) {
            sum += value;
        }
        return sum / lastBgs.length;
    }

    private void refreshAnyStatusThatNeedsToBeRefreshed() {

        Map<MedLinkMedtronicStatusRefreshType, Long> statusRefresh = workWithStatusRefresh(MedLinkMedtronicPumpPlugin.StatusRefreshAction.GetData, null,
                null);

        if (!doWeHaveAnyStatusNeededRefreshing(statusRefresh)) {
            return;
        }

        boolean resetTime = false;

        if (isPumpNotReachable()) {
            aapsLogger.error("Pump unreachable.");
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable, getResourceHelper(), rxBus);

            return;
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);


        if (hasTimeDateOrTimeZoneChanged) {

            checkTimeAndOptionallySetTime();

            // read time if changed, set new time
            hasTimeDateOrTimeZoneChanged = false;
        }

        // execute
        Set<MedLinkMedtronicStatusRefreshType> refreshTypesNeededToReschedule = new HashSet<>();

        if (!tempbasalMicrobolusOperations.operations.isEmpty() &&
                tempbasalMicrobolusOperations.operations.getFirst().getReleaseTime().
                        isBefore(LocalDateTime.now().plus(Seconds.seconds(30))) &&
                !tempbasalMicrobolusOperations.operations.getFirst().isCommandIssued()) {
            TempBasalMicroBolusPair oper = tempbasalMicrobolusOperations.operations.getFirst();
            aapsLogger.info(LTag.PUMP, "Next Command " +
                    oper.getReleaseTime().toDateTime());
            aapsLogger.info(LTag.PUMP, oper.toString());
            oper.setCommandIssued(true);
            switch (oper.getOperationType()) {
                case SUSPEND: {
                    if (!getPumpStatusData().pumpStatusType.equals(PumpStatusType.Suspended)) {
                        stopPump(new Callback() {
                            @Override public void run() {
                                if(medLinkPumpStatus.pumpStatusType.equals(PumpStatusType.Suspended)){
                                    tempbasalMicrobolusOperations.operations.poll();
                                    oper.setCommandIssued(true);
                                }

                            }
                        });
                    }
                }
                break;
                case REACTIVATE: {
                    reactivatePump(oper, oper.getCallback());
                }
                break;
                case BOLUS: {
                    if (getPumpStatusData().pumpStatusType.equals(PumpStatusType.Suspended)) {
                        reactivatePump(oper, new Function1() {
                            @Override public Object invoke(Object o) {
                                return o;
                            }
                        });
                    }
                    DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                    detailedBolusInfo.lastKnownBolusTime = medLinkPumpStatus.lastBolusTime.getTime();
                    detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
                    detailedBolusInfo.insulin = oper.getDose().doubleValue();
                    detailedBolusInfo.source = Source.PUMP;
                    detailedBolusInfo.isTBR = true;
                    detailedBolusInfo.deliverAt = System.currentTimeMillis();
                    getAapsLogger().debug(LTag.APS, "applyAPSRequest: bolus()");
                    Function1 callback = new Function1<PumpEnactResult, Unit>() {
                        @Override public Unit invoke(PumpEnactResult o) {
                            aapsLogger.info(LTag.PUMPBTCOMM, "temp basal bolus " + o.toString());
                            if (o.success) {
                                tempbasalMicrobolusOperations.operations.poll();
                            } else {
                                oper.setCommandIssued(false);
                            }
                            return Unit.INSTANCE;
                        }
                    };
                    deliverTreatment(detailedBolusInfo, callback);
                }
            }

        }

        for (Map.Entry<MedLinkMedtronicStatusRefreshType, Long> refreshType : statusRefresh.entrySet()) {

            aapsLogger.info(LTag.PUMP, "Next Command " + Math.round(
                    (refreshType.getValue() - System.currentTimeMillis())));
            aapsLogger.info(LTag.PUMP, refreshType.getKey().name());

            if ((refreshType.getValue() -
                    System.currentTimeMillis()) < 30000L) {
                scheduleNextRefresh(refreshType.getKey());
                switch (refreshType.getKey()) {
                    case PumpHistory: {
                        aapsLogger.info(LTag.PUMPBTCOMM, "refreshing");
                        readPumpHistory();
                        refreshTypesNeededToReschedule.add(refreshType.getKey());
                    }
                    break;

                    case PumpTime: {
                        checkTimeAndOptionallySetTime();
                        refreshTypesNeededToReschedule.add(refreshType.getKey());
                        resetTime = true;
                    }
                    break;

                    case BatteryStatus:
                    case RemainingInsulin: {
                        medLinkService.getMedtronicUIComm().executeCommand(refreshType.getKey().getCommandType(medtronicUtil.getMedtronicPumpModel()));
                        refreshTypesNeededToReschedule.add(refreshType.getKey());
                        resetTime = true;
                    }
                    break;

                    case Configuration: {
                        medLinkService.getMedtronicUIComm().executeCommand(refreshType.getKey().getCommandType(medtronicUtil.getMedtronicPumpModel()));
                        resetTime = true;
                    }
                    break;
                }
            }
        }


        // reschedule

        if (statusRefreshMap.isEmpty() || System.currentTimeMillis() - lastTryToConnect >= 600000) {
            lastTryToConnect = System.currentTimeMillis();
            readPumpHistory();
            scheduleNextRefresh(PumpHistory);
            aapsLogger.info(LTag.PUMPBTCOMM, "posthistory");
//            scheduleNextRefresh(PumpHistory);
        }

        if (resetTime)
            medLinkPumpStatus.setLastCommunicationToNow();
    }

    private Function<Supplier<Stream<String>>, MedLinkStandardReturn<PumpDriverState>>
    getStartStopCallback(TempBasalMicroBolusPair oper, Function1 callback,
                         ChangeStatusCallback function, boolean isConnect) {
        return function.andThen(f -> {
            switch (f.getFunctionResult()) {
                case Busy:
                    aapsLogger.info(LTag.PUMPBTCOMM, "busy " + oper);
                    callback.invoke(new PumpEnactResult(getInjector()) //
                            .success(false) //
                            .enacted(false) //
                            .comment(getResourceHelper().gs(R.string.tempbasaldeliveryerror)));
                    break;
                case Connected:
                    aapsLogger.info(LTag.PUMPBTCOMM, "connected " + oper);
                    callback.invoke(new PumpEnactResult(getInjector()) //
                            .success(isConnect) //
                            .enacted(isConnect) //
                            .comment(getResourceHelper().gs(R.string.careportal_tempbasalend)));
                    if (isConnect) {
                        lastStatus = PumpStatusType.Running.getStatus();
                        this.tempbasalMicrobolusOperations.operations.peek();
                    } else {
                        oper.setCommandIssued(false);
                    }
                    break;
                case Suspended:
                    aapsLogger.info(LTag.PUMPBTCOMM, "suspended " + oper);
                    callback.invoke(new PumpEnactResult(getInjector()) //
                            .success(!isConnect) //
                            .enacted(!isConnect) //
                            .comment(getResourceHelper().gs(R.string.careportal_tempbasalstart)));
                    if (!isConnect) {
                        lastStatus = PumpStatusType.Suspended.getStatus();
                        this.tempbasalMicrobolusOperations.operations.peek();
//                        createTemporaryBasalData(oper.getDuration(),
//                                oper.getDose().setScale(2).doubleValue());
                    } else {
                        oper.setCommandIssued(false);
                    }
                    break;
            }
            return f;
        });
    }

    public void createTemporaryBasalData(int duration, double dose) {
        TemporaryBasal tempBasal = new TemporaryBasal(getInjector()) //
                .date(System.currentTimeMillis()) //
                .duration(duration) //
                .source(Source.USER);
        if (duration != 0) {
            tempBasal = tempBasal.absolute(dose);
        }
        aapsLogger.info(LTag.EVENTS, "CreateTemporaryData");
        activePlugin.getActiveTreatments().addToHistoryTempBasal(tempBasal);
    }


    private void reactivatePump(TempBasalMicroBolusPair oper, Function1 callback) {
        getAapsLogger().debug(LTag.APS, "reactivate pump");
        ChangeStatusCallback function = new ChangeStatusCallback(aapsLogger,
                ChangeStatusCallback.OperationType.START, this);
        Function<Supplier<Stream<String>>, MedLinkStandardReturn<PumpDriverState>> startStopFunction =
                getStartStopCallback(oper, callback, function, true);
        MedLinkPumpMessage msg = new MedLinkPumpMessage(
                MedLinkCommandType.StopStartPump,
                MedLinkCommandType.StartPump, startStopFunction,
                medLinkServiceData, aapsLogger);
        medLinkService.getMedtronicUIComm().executeCommandCP(msg);
    }

    @Override
    public void getPumpStatus(String status) {
        if (status.toLowerCase().equals("clicked refresh")) {
            readPumpHistory();
        } else if (firstRun) {
            initializePump(!isRefresh);
        } else {
            refreshAnyStatusThatNeedsToBeRefreshed();
        }

        rxBus.send(new EventMedtronicPumpValuesChanged());

    }

    public void sendPumpUpdateEvent() {
        rxBus.send(new EventMedtronicPumpValuesChanged());
    }

    private void initializePump(boolean b) {
        aapsLogger.info(LTag.PUMP, getLogPrefix() + "initializePump - start");
        medLinkService.getDeviceCommunicationManager().setDoWakeUpBeforeCommand(false);
        setRefreshButtonEnabled(false);
        readPumpHistory();
    }

    public void postInit() {
        if (isRefresh) {
            if (isPumpNotReachable()) {
                aapsLogger.error(getLogPrefix() + "initializePump::Pump unreachable.");
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable, getResourceHelper(), rxBus);
                setRefreshButtonEnabled(true);
                return;
            }

            medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);
        }

        // model (once)
        if (medtronicUtil.getMedtronicPumpModel() == null) {


            Function<Supplier<Stream<String>>, MedLinkStandardReturn> func = f -> {
                MedLinkStandardReturn<MedLinkMedtronicDeviceType> res = MedLinkModelParser.parse(f);
                medtronicUtil.setMedtronicPumpModel(res.getFunctionResult());
                return res;
            };
            MedLinkPumpMessage message = new MedLinkPumpMessage(MedLinkCommandType.Connect,
                    MedLinkCommandType.NoCommand,
                    func,
                    medLinkServiceData,
                    aapsLogger);
            medLinkService.getMedtronicUIComm().executeCommandCP(message);
        } else {
            if (medLinkPumpStatus.medtronicDeviceType != medtronicUtil.getMedtronicPumpModel()) {
//                aapsLogger.warn(LTag.PUMP, getLogPrefix() + "Configured pump is not the same as one detected.");
//                medtronicUtil.sendNotification(MedtronicNotificationType.PumpTypeNotSame, getResourceHelper(), rxBus);
            }
        }

        this.pumpState = PumpDriverState.Connected;

        // time (1h)
//        checkTimeAndOptionallySetTime();

//        readPumpHistory();

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m)
//        medlinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.GetRemainingInsulin);
//        scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.RemainingInsulin, 10);

        // remaining power (1h)
//        medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.GetBatteryStatus);
//        scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.BatteryStatus, 20);
        aapsLogger.info(LTag.PUMPBTCOMM, "postinit");
        scheduleNextRefresh(PumpHistory);


        // configuration (once and then if history shows config changes)
//        medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.getSettings(medtronicUtil.getMedtronicPumpModel()));

        // read profile (once, later its controlled by isThisProfileSet method)

        getPumpProfile();


        readPumpBGHistory(true);

        getPreviousBGHistory();
        readBolusHistory();


        int errorCount = medLinkService.getMedtronicUIComm().getInvalidResponsesCount();

        if (errorCount >= 5) {
            aapsLogger.error("Number of error counts was 5 or more. Starting tunning.");
            setRefreshButtonEnabled(true);
            serviceTaskExecutor.startTask(new WakeAndTuneTask(getInjector()));
            return;
        }

        medLinkPumpStatus.setLastCommunicationToNow();
        setRefreshButtonEnabled(true);
        rxBus.send(new EventMedtronicPumpValuesChanged());

        if (!isRefresh) {
            pumpState = PumpDriverState.Initialized;
        }

        isInitialized = true;
        this.pumpState = PumpDriverState.Initialized;


        this.firstRun = false;
        aapsLogger.info(LTag.EVENTS, "pump initialized");
    }

    private void readBolusHistory() {
        if (initCommands.contains(resourceHelper.gs(R.string.key_medlink_init_commands_last_bolus_history))) {

            aapsLogger.info(LTag.PUMPBTCOMM, "get full bolus history");

            lastBolusHistoryRead = System.currentTimeMillis();
            BolusHistoryCallback func =
                    new BolusHistoryCallback(aapsLogger, this);

            MedLinkPumpMessage msg = new MedLinkPumpMessage(MedLinkCommandType.BolusHistory,
                    MedLinkCommandType.NoCommand,
                    func,
                    medLinkServiceData,
                    aapsLogger);
            medLinkService.getMedtronicUIComm().executeCommandCP(msg);
        }
    }

    private void getPreviousBGHistory() {
        aapsLogger.info(LTag.PUMPBTCOMM, "get bolus history");
        if (initCommands.contains(resourceHelper.gs(R.string.key_medlink_init_commands_penultimate_bg_history))) {
            BGHistoryCallback func =
                    new BGHistoryCallback(getInjector(), this, aapsLogger, true);

            MedLinkPumpMessage msg = new MedLinkPumpMessage(MedLinkCommandType.PreviousBGHistory,
                    MedLinkCommandType.NoCommand, func,
                    medLinkServiceData,
                    aapsLogger);
            medLinkService.getMedtronicUIComm().executeCommandCP(msg);
        }
    }

    private void checkTimeAndOptionallySetTime() {

        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Start");

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Pump Unreachable.");
            setRefreshButtonEnabled(true);
            return;
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

//        medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.GetRealTimeClock);

        ClockDTO clock = medtronicUtil.getPumpTime();

//        if (clock == null) { // retry
//            medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.GetRealTimeClock);
//
//            clock = medtronicUtil.getPumpTime();
//        }

        if (clock == null)
            return;

        int timeDiff = Math.abs(clock.timeDifference);

        if (timeDiff > 20) {

            if ((clock.localDeviceTime.getYear() <= 2015) || (timeDiff <= 24 * 60 * 60)) {

                aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Set time on pump.", timeDiff);

                medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.SetRealTimeClock);

                if (clock.timeDifference == 0) {
                    Notification notification = new Notification(Notification.INSIGHT_DATE_TIME_UPDATED, getResourceHelper().gs(R.string.pump_time_updated), Notification.INFO, 60);
                    rxBus.send(new EventNewNotification(notification));
                }
            } else {
                if ((clock.localDeviceTime.getYear() > 2015)) {
                    aapsLogger.error("MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference over 24h requested [diff={} s]. Doing nothing.", timeDiff);
                    medtronicUtil.sendNotification(MedtronicNotificationType.TimeChangeOver24h, getResourceHelper(), rxBus);
                }
            }

        } else {
            aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Do nothing.", timeDiff);
        }

        scheduleNextRefresh(MedLinkMedtronicStatusRefreshType.PumpTime, 0);
    }

    public void setPumpTime(long currentTime) {
        ClockDTO clockDTO = new ClockDTO();
        clockDTO.localDeviceTime = new LocalDateTime();
        clockDTO.pumpTime = new LocalDateTime(currentTime);
        clockDTO.timeDifference = clockDTO.localDeviceTime.compareTo(clockDTO.pumpTime);
        medtronicUtil.setPumpTime(clockDTO);
    }

    private void getPumpProfile() {

        aapsLogger.info(LTag.PUMPBTCOMM, "get basal profiles");
        if (initCommands.contains(resourceHelper.gs(R.string.key_medlink_init_commands_profile))) {
            BasalCallback func =
                    new BasalCallback(aapsLogger, this);
            ProfileCallback profileCallback =
                    new ProfileCallback(injector, aapsLogger, context, this);
            MedLinkPumpMessage msg = new BasalMedLinkMessage(MedLinkCommandType.ActiveBasalProfile,
                    MedLinkCommandType.BaseProfile, func, profileCallback,
                    medLinkServiceData,
                    aapsLogger);
            medLinkService.getMedtronicUIComm().executeCommandCP(msg);
        }
//        if (medtronicUITask.getResponseType() == MedtronicUIResponseType.Error) {
//            aapsLogger.info(LTag.PUMP, "reprocessing due to error response type");
//            medLinkService.getMedtronicUIComm().executeCommandCP(msg);
//        }
    }

    public void setBasalProfile(BasalProfile basalProfile) {
        this.basalProfile = basalProfile;
    }

    public BasalProfile getBasalProfile() {
        return basalProfile;
    }

    private void scheduleNextRefresh(MedLinkMedtronicStatusRefreshType refreshType) {
        scheduleNextRefresh(refreshType, 0);
    }


    protected int scheduleNextRefresh(MedLinkMedtronicStatusRefreshType refreshType, int additionalTimeInMinutes) {
        switch (refreshType) {

            case RemainingInsulin:
//                {
//                double remaining = medtronicPumpStatus.reservoirRemainingUnits;
//                int min;
//                if (remaining > 50)
//                    min = 4 * 60;
//                else if (remaining > 20)
//                    min = 60;
//                else
//                    min = 15;
//
//                workWithStatusRefresh(MedLinkMedtronicPumpPlugin.StatusRefreshAction.Add, refreshType, getTimeInFutureFromMinutes(min));
//            }
//            break;

            case PumpTime:
            case Configuration:
            case BatteryStatus: {
                workWithStatusRefresh(StatusRefreshAction.GetData, refreshType,
                        getTimeInFutureFromMinutes(refreshType.getRefreshTime() + additionalTimeInMinutes));
            }
            break;
            case PumpHistory: {
                aapsLogger.info(LTag.PUMPBTCOMM, "Next refresh will be in " +
                        getTimeInFutureFromMinutes(refreshType.getRefreshTime() + additionalTimeInMinutes));
                workWithStatusRefresh(StatusRefreshAction.Add, refreshType,
                        getTimeInFutureFromMinutes(refreshType.getRefreshTime() + additionalTimeInMinutes));
            }
            break;
        }
        return 0;
    }

    protected long getTimeInFutureFromMinutes(int minutes) {
        return getCurrentTime().toDateTime().toInstant().getMillis() + getTimeInMs(minutes);
    }


    protected long getTimeInMs(int minutes) {
        return minutes * 60 * 1000L;
    }


    protected synchronized Map<MedLinkMedtronicStatusRefreshType, Long> workWithStatusRefresh(MedLinkMedtronicPumpPlugin.StatusRefreshAction action, //
                                                                                              MedLinkMedtronicStatusRefreshType statusRefreshType, //
                                                                                              Long time) {

        switch (action) {

            case Add: {
                aapsLogger.info(LTag.PUMPBTCOMM, new DateTime(time).toString());
                statusRefreshMap.put(statusRefreshType, time);
                return null;
            }

            case GetData: {
                return new HashMap<>(statusRefreshMap);
            }

            default:
                return null;

        }

    }

    public TempBasalMicrobolusOperations getTempBasalMicrobolusOperations() {
        return tempbasalMicrobolusOperations;
    }


    @Override
    public boolean isThisProfileSet(Profile profile) {
        aapsLogger.debug(LTag.PUMP, "isThisProfileSet: basalInitalized=" + medLinkPumpStatus.basalProfileStatus);

        if (!isInitialized)
            return true;

//        if (medtronicPumpStatus.basalProfileStatus == BasalProfileStatus.NotInitialized) {
//            // this shouldn't happen, but if there was problem we try again
//            getPumpProfile();
//            return isProfileSame(profile);
//        } else
        if (medLinkPumpStatus.basalProfileStatus == BasalProfileStatus.ProfileChanged) {
            return false;
        }
        return (medLinkPumpStatus.basalProfileStatus != BasalProfileStatus.ProfileOK) || isProfileSame(profile);
    }

    private boolean isProfileSame(Profile profile) {

        boolean invalid = false;
        Double[] basalsByHour = medLinkPumpStatus.basalsByHour;

        aapsLogger.debug(LTag.PUMP, "Current Basals (h):   "
                + (basalsByHour == null ? "null" : BasalProfile.getProfilesByHourToString(basalsByHour)));

        // int index = 0;

        if (basalsByHour == null)
            return true; // we don't want to set profile again, unless we are sure

        StringBuilder stringBuilder = new StringBuilder("Requested Basals (h): ");

        for (Profile.ProfileValue basalValue : profile.getBasalValues()) {

            double basalValueValue = pumpDescription.pumpType.determineCorrectBasalSize(basalValue.value);

            int hour = basalValue.timeAsSeconds / (60 * 60);

            if (!MedLinkMedtronicUtil.isSame(basalsByHour[hour], basalValueValue)) {
                invalid = true;
            }

            stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue));
            stringBuilder.append(" ");
        }

        aapsLogger.debug(LTag.PUMP, stringBuilder.toString());

        if (!invalid) {
            aapsLogger.debug(LTag.PUMP, "Basal profile is same as AAPS one.");
        } else {
            aapsLogger.debug(LTag.PUMP, "Basal profile on Pump is different than the AAPS one.");
        }

        return (!invalid);
    }

    @Override
    public long lastDataTime() {
        if (medLinkPumpStatus.lastConnection != 0) {
            return medLinkPumpStatus.lastConnection;
        }

        return System.currentTimeMillis();
    }

    @Override
    public double getBaseBasalRate() {
        return medLinkPumpStatus.getBasalProfileForHour();
    }

    @Override
    public double getReservoirLevel() {
        return medLinkPumpStatus.reservoirRemainingUnits;
    }

    @Override
    public int getBatteryLevel() {
        return medLinkPumpStatus.batteryRemaining;
    }

    @Override
    public void stopBolusDelivering() {
        this.bolusDeliveryType = BolusDeliveryType.CancelDelivery;
    }

    private void setRefreshButtonEnabled(boolean enabled) {
        rxBus.send(new EventRefreshButtonState(enabled));
    }

    private boolean isPumpNotReachable() {

        MedLinkServiceState medLinkServiceState = medLinkServiceData.medLinkServiceState;

        if (medLinkServiceState == null) {
            aapsLogger.debug(LTag.PUMP, "MedLink unreachable. MedLinkServiceState is null.");
            return false;
        }

        if (medLinkServiceState != MedLinkServiceState.PumpConnectorReady //
                && medLinkServiceState != MedLinkServiceState.MedLinkReady //
                && medLinkServiceState != MedLinkServiceState.TuneUpDevice) {
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
        return new TempBasalPair(medLinkPumpStatus.tempBasalRatio, true,
                medLinkPumpStatus.tempBasalRemainMin);
//        MedtronicUITask responseTask = null;//rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.ReadTemporaryBasal);
//
//        if (responseTask.hasData()) {
//            TempBasalPair tbr = (TempBasalPair) responseTask.returnData;
//
//            // we sometimes get rate returned even if TBR is no longer running
//            if (tbr.getDurationMinutes() == 0) {
//                tbr.setInsulinRate(0.0d);
//            }
//
//            return tbr;
//        } else {
//            return null;
//        }
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
                                                boolean enforceNew, Function1 callback) {
        PumpEnactResult result;
        aapsLogger.info(LTag.PUMPBTCOMM, "absolute rate " + absoluteRate + " " + durationInMinutes);
        if (absoluteRate != 0d && (absoluteRate == getBaseBasalRate() ||
                Math.abs(absoluteRate - getBaseBasalRate()) < getPumpDescription().bolusStep)) {
            tempbasalMicrobolusOperations.operations.clear();
            clearTempBasal();
            return new PumpEnactResult(getInjector()).enacted(true).success(true);
        } else if (getTemporaryBasal() != null && getTemporaryBasal().getDesiredRate() == absoluteRate && absoluteRate == 0) {
            result = extendBasalTreatment(durationInMinutes, callback);
        } else {


            if (absoluteRate == medLinkPumpStatus.currentBasal ||
                    Math.abs(absoluteRate - medLinkPumpStatus.currentBasal) < pumpDescription.bolusStep) {
                result = clearTempBasal();
            } else if (absoluteRate < medLinkPumpStatus.currentBasal) {
                tempbasalMicrobolusOperations.clearOperations();
                result = scheduleSuspension(0, durationInMinutes, profile, callback,
                        absoluteRate,
                        PumpTempBasalType.Absolute);
            } else {

                clearTempBasal();
                result = scheduleTempBasalBolus(0, durationInMinutes, profile, callback,
                        absoluteRate, PumpTempBasalType.Absolute);
            }
//        result.success = false;
//        result.comment = MainApp.gs(R.string.pumperror);

        }
        aapsLogger.debug("Settings temp basal percent: " + result);
        return result;

    }

    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
                                                boolean enforceNew) {

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);
            aapsLogger.info(LTag.PUMP, "pump unreachable");
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

            if (MedLinkMedtronicUtil.isSame(tbrCurrent.getInsulinRate(), absoluteRate)) {

                boolean sameRate = true;
                if (MedLinkMedtronicUtil.isSame(0.0d, absoluteRate) && durationInMinutes > 0) {
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

            cancelTempBasal(true, new Callback() {
                @Override public void run() {
                    aapsLogger.info(LTag.PUMPBTCOMM, "tbr cancelled");
                }
            });

            ToastUtils.showToastInUiThread(context, resourceHelper.gs(info.nightscout.androidaps.plugins.pump.common.R.string.tempbasaldeliveryerror));
//            Boolean response = (Boolean) responseTask2.returnData;

//            if (response) {
//                aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - Current TBR cancelled.");
//            } else {
//                aapsLogger.error(getLogPrefix() + "setTempBasalAbsolute - Cancel TBR failed.");
//
//                finishAction("TBR");

            return new PumpEnactResult(getInjector()).success(false).enacted(false)
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_cant_cancel_tbr_stop_op));
//            }
        }
        return new PumpEnactResult(getInjector()).success(false).enacted(false)
                .comment(getResourceHelper().gs(R.string.medtronic_cmd_cant_cancel_tbr_stop_op));
        // now start new TBR
//        MedtronicUITask responseTask = null;//rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.SetTemporaryBasal,
//        //absoluteRate, durationInMinutes);
//
//        Boolean response = (Boolean) responseTask.returnData;
//
//        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - setTBR. Response: " + response);
//
//        if (response) {
//            // FIXME put this into UIPostProcessor
//            medtronicPumpStatus.tempBasalStart = new Date();
//            medtronicPumpStatus.tempBasalAmount = absoluteRate;
//            medtronicPumpStatus.tempBasalLength = durationInMinutes;
//
//            TemporaryBasal tempStart = new TemporaryBasal(getInjector()) //
//                    .date(System.currentTimeMillis()) //
//                    .duration(durationInMinutes) //
//                    .absolute(absoluteRate) //
//                    .source(Source.USER);
//
//            activePlugin.getActiveTreatments().addToHistoryTempBasal(tempStart);
//
//            incrementStatistics(MedtronicConst.Statistics.TBRsSet);
//
//            finishAction("TBR");
//
//            return new PumpEnactResult(getInjector()).success(true).enacted(true) //
//                    .absolute(absoluteRate).duration(durationInMinutes);
//
//        } else {
//            finishAction("TBR");
//
//            return new PumpEnactResult(getInjector()).success(false).enacted(false) //
//                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_tbr_could_not_be_delivered));
//        }

    }

    private PumpEnactResult clearTempBasal() {
        aapsLogger.info(LTag.EVENTS, " clearing temp basal");
        PumpEnactResult result = buildPumpEnactResult();
        this.tempbasalMicrobolusOperations.clearOperations();
        result.success = true;
        result.comment = resourceHelper.gs(R.string.canceltemp);
        if (getPumpStatusData().pumpStatusType.equals(PumpStatusType.Suspended)) {
            startPump(new Callback() {
                @Override public void run() {
                    aapsLogger.info(LTag.EVENTS, "temp basal cleared");
                }
            });
        }
//        createTemporaryBasalData(0, 0);
        return result;
    }

    private ConcurrentLinkedDeque<TempBasalMicroBolusPair> buildSuspensionScheduler(Integer totalSuspendedMinutes,
                                                                                    Integer suspensions,
                                                                                    Double operationInterval, Function1 callback) {
        double operationDuration = Double.valueOf(totalSuspendedMinutes) / Double.valueOf(suspensions);
        if (operationDuration < Constants.INTERVAL_BETWEEN_OPERATIONS) {
            operationDuration = Constants.INTERVAL_BETWEEN_OPERATIONS;
        }
        double mod = operationDuration % Constants.INTERVAL_BETWEEN_OPERATIONS;
        if ((mod / Constants.INTERVAL_BETWEEN_OPERATIONS) > 0.5) {
            operationDuration += Constants.INTERVAL_BETWEEN_OPERATIONS - mod;
        } else {
            operationDuration -= mod;
        }
        LocalDateTime startOperationTime = getCurrentTime();
        LocalDateTime endOperationTime = startOperationTime.plusMinutes((int) operationDuration);
        ConcurrentLinkedDeque<TempBasalMicroBolusPair> operations = new ConcurrentLinkedDeque<TempBasalMicroBolusPair>();
        for (int oper = 0; oper < suspensions; oper += 1) {
            operations.add(new TempBasalMicroBolusPair(totalSuspendedMinutes, 0d,
                    0d, startOperationTime,
                    TempBasalMicroBolusPair.OperationType.SUSPEND, callback));
            operations.add(new TempBasalMicroBolusPair(totalSuspendedMinutes, 0d,
                    0d, endOperationTime,
                    TempBasalMicroBolusPair.OperationType.REACTIVATE, callback));
            startOperationTime = startOperationTime.plusMinutes(operationInterval.intValue());
            endOperationTime = endOperationTime.plusMinutes(operationInterval.intValue());
        }
        return removeExtraOperations(operations, mod, suspensions);
    }

    private ConcurrentLinkedDeque<TempBasalMicroBolusPair> removeExtraOperations(
            ConcurrentLinkedDeque<TempBasalMicroBolusPair> operations, double mod,
            Integer suspensions) {
        double deltaMod = mod * suspensions;
        LocalDateTime previousReleaseTime = null;
        List<Integer> toRemove = new ArrayList<>();
        int index = 0;
        for (TempBasalMicroBolusPair operation : operations) {
            if (previousReleaseTime != null && operation.getOperationType() ==
                    TempBasalMicroBolusPair.OperationType.SUSPEND &&
                    operation.getReleaseTime().compareTo(previousReleaseTime) >= 0) {
                toRemove.add(index);
                toRemove.add(index + 1);
            }
            if (deltaMod > Constants.INTERVAL_BETWEEN_OPERATIONS &&
                    operation.getOperationType() == TempBasalMicroBolusPair.OperationType.REACTIVATE) {
                operation.delayInMinutes(Constants.INTERVAL_BETWEEN_OPERATIONS);
                previousReleaseTime = operation.getReleaseTime();
                deltaMod -= Constants.INTERVAL_BETWEEN_OPERATIONS;
            } else {
                break;
            }
            index++;
        }
        Collections.reverse(toRemove);
        for (int rem : toRemove) {
            operations.remove(rem);
        }
        return operations;
    }

    private PumpEnactResult scheduleSuspension(Integer percent,
                                               Integer durationInMinutes,
                                               Profile profile,
                                               Function1 callback,
                                               double absoluteBasalValue,
                                               PumpTempBasalType pumpTempBasalType) {
        this.durationInMinutes = durationInMinutes;
        int totalSuspendedMinutes = 0;
        double calcPercent = percent;
        if (pumpTempBasalType.equals(PumpTempBasalType.Absolute)) {
            if (absoluteBasalValue != 0d) {
                calcPercent = absoluteBasalValue / profile.getBasal();
                totalSuspendedMinutes = calculateTotalSuspended(calcPercent);
            } else if (percent == 0) {
                totalSuspendedMinutes = durationInMinutes;
            }
        } else {
            calcPercent = (1 - percent / 100d);
            totalSuspendedMinutes = calculateTotalSuspended(calcPercent);
        }
        this.percent = percent;

        if (totalSuspendedMinutes < Constants.INTERVAL_BETWEEN_OPERATIONS) {
            return result;
        } else {
            int suspensions;
            if (totalSuspendedMinutes < durationInMinutes / 2) {
                suspensions = new BigDecimal(totalSuspendedMinutes)
                        .divide(new BigDecimal(Constants.INTERVAL_BETWEEN_OPERATIONS), RoundingMode.HALF_UP)
                        .setScale(0, RoundingMode.HALF_UP).intValue();
            } else {
                int delta = durationInMinutes - totalSuspendedMinutes;
                suspensions = delta / Constants.INTERVAL_BETWEEN_OPERATIONS;
                if (suspensions == 0) {
                    suspensions++;
                }
            }
            double operationInterval = Double.valueOf(durationInMinutes) / suspensions;
            double mod = operationInterval % Constants.INTERVAL_BETWEEN_OPERATIONS;
            operationInterval -= mod;
            //TODO need to reavaluate some cases, used floor here to avoid two possible up rounds
            ConcurrentLinkedDeque<TempBasalMicroBolusPair> operations = this.buildSuspensionScheduler(
                    totalSuspendedMinutes, suspensions, operationInterval, callback);
            this.tempbasalMicrobolusOperations.updateOperations(suspensions, 0d,
                    operations,
                    totalSuspendedMinutes);
        }
        refreshAnyStatusThatNeedsToBeRefreshed();
        //criar fila de comandos aqui, esta fila deverá ser consumida a cada execução de checagem de status
        return buildPumpEnactResult().success(true).comment(getResourceHelper().gs(R.string.medtronic_cmd_desc_set_tbr));
    }

    private int calculateTotalSuspended(double calcPercent) {
        double suspended = durationInMinutes * calcPercent / 10;
        return new BigDecimal(Double.toString(suspended)).multiply(new BigDecimal(10)).setScale(0, RoundingMode.HALF_UP).intValue();
    }


    private TempBasalMicrobolusOperations buildFirstLevelTempBasalMicroBolusOperations(
            Integer percent, LinkedList<Profile.ProfileValue> basalProfilesFromTemp,
            Integer durationInMinutes, Function1 callback, double absoluteBasalValue,
            PumpTempBasalType basalType) {
        List<TempBasalMicroBolusDTO> insulinPeriod = new ArrayList<>();
        Profile.ProfileValue previousProfileValue = null;
        int spentBasalTimeInSeconds = 0;
        int currentStep = 0;
        double totalAmount = 0d;
        int durationInSeconds = durationInMinutes * 60;
        LocalDateTime time = getCurrentTime();
        int startedTime = time.getHourOfDay() * 3600 + time.getMinuteOfHour() * 60 + time.getSecondOfMinute();
        while (!basalProfilesFromTemp.isEmpty()) {
            Profile.ProfileValue currentProfileValue = basalProfilesFromTemp.pollFirst();
            if (previousProfileValue == null) {
                previousProfileValue = currentProfileValue;
            }
            if (currentProfileValue.timeAsSeconds == 0) {
                if (currentStep > 0) {
                    startedTime = 0;
                }
                durationInSeconds -= spentBasalTimeInSeconds;
                spentBasalTimeInSeconds = 0;
            }
            TempBasalMicroBolusDTO tempBasalPair = calculateTempBasalDosage(startedTime + spentBasalTimeInSeconds,
                    currentProfileValue, basalProfilesFromTemp, percent, absoluteBasalValue,
                    durationInSeconds - spentBasalTimeInSeconds, basalType);
            totalAmount += tempBasalPair.getInsulinRate();
            spentBasalTimeInSeconds += tempBasalPair.getDurationMinutes() * 60;
            insulinPeriod.add(tempBasalPair);
            currentStep++;
        }


        BigDecimal roundedTotalAmount = new BigDecimal(totalAmount).setScale(1,
                RoundingMode.HALF_UP);
        if (roundedTotalAmount.doubleValue() == getPumpType().getBolusSize()) {
            ConcurrentLinkedDeque<TempBasalMicroBolusPair> tempBasalList = new ConcurrentLinkedDeque<>();
            tempBasalList.add(new TempBasalMicroBolusPair(0, totalAmount, totalAmount,
                    getCurrentTime().plusMinutes(durationInMinutes / 2),
                    TempBasalMicroBolusPair.OperationType.BOLUS, callback));
            return new TempBasalMicrobolusOperations(1, totalAmount,
                    durationInMinutes,
                    tempBasalList);
        } else if (roundedTotalAmount.doubleValue() < getPumpType().getBolusSize()) {
            cancelTempBasal(true);
            return new TempBasalMicrobolusOperations(0, 0, 0,
                    new ConcurrentLinkedDeque<>());
        } else {
            return buildTempBasalSMBOperations(roundedTotalAmount, insulinPeriod, callback,
                    durationInMinutes, absoluteBasalValue);
        }
    }

    private TempBasalMicroBolusDTO calculateTempBasalDosage(int startedTime,
                                                            Profile.ProfileValue currentProfileValue,
                                                            LinkedList<Profile.ProfileValue> basalProfilesFromTemp,
                                                            int percent, double abs,
                                                            int remainingDurationInSeconds,
                                                            PumpTempBasalType basalType) {
        int delta = 0;
        if (basalProfilesFromTemp.isEmpty()) {
            delta = remainingDurationInSeconds;
        } else if (basalProfilesFromTemp.peekFirst().timeAsSeconds < startedTime) {
            if (basalProfilesFromTemp.peekFirst().timeAsSeconds == 0) {
                delta = Constants.SECONDS_PER_DAY - currentProfileValue.timeAsSeconds;
            }
        } else {
            delta = basalProfilesFromTemp.peekFirst().timeAsSeconds - startedTime;
        }
        double profileDosage = 0d;
        if (basalType.equals(PumpTempBasalType.Percent)) {
            profileDosage = calcTotalAmount(percent, currentProfileValue.value, delta / 60);
        } else {
            profileDosage = calcTotalAmount(abs, currentProfileValue.value, delta / 60);
        }
        return createTempBasalPair(profileDosage, delta, currentProfileValue.timeAsSeconds);

    }

    private TempBasalMicroBolusDTO createTempBasalPair(Double totalAmount, Integer durationInSeconds, Integer startTime) {
        return new TempBasalMicroBolusDTO(totalAmount, false, durationInSeconds / 60,
                startTime, startTime + durationInSeconds);
    }

    private Double calcTotalAmount(double tempBasal, double currentBasalValue,
                                   int durationInMinutes) {
        return (tempBasal - currentBasalValue) * (durationInMinutes / 60d);
    }

    private Double calcTotalAmount(Integer percent, double value, int durationInMinutes) {
        return (percent.doubleValue() / 100 - 1) * value * (durationInMinutes / 60d);
    }

    public LocalDateTime getCurrentTime() {
        return LocalDateTime.now();
    }

    private TempBasalMicrobolusOperations buildTempBasalSMBOperations(BigDecimal totalAmount,
                                                                      List<TempBasalMicroBolusDTO> insulinPeriod,
                                                                      Function1 callback,
                                                                      Integer durationInMinutes,
                                                                      Double absoluteRate) {
        TempBasalMicrobolusOperations result = new TempBasalMicrobolusOperations();
        result.setDurationInMinutes(durationInMinutes);
        result.setAbsoluteRate(absoluteRate);
        LocalDateTime operationTime = getCurrentTime();
        double minDosage = 0d;
        for (TempBasalMicroBolusDTO period : insulinPeriod) {
            double periodDose = period.getInsulinRate();// + accumulatedNextPeriodDose;
            double roundedPeriodDose = new BigDecimal(periodDose).setScale(1, RoundingMode.HALF_DOWN).doubleValue();
            int time = (period.getEndTimeInSeconds() - period.getSartTimeInSeconds()) / 60;
            int periodAvailableOperations = time / Constants.INTERVAL_BETWEEN_OPERATIONS;
            aapsLogger.info(LTag.PUMPBTCOMM, "" + time);
            aapsLogger.info(LTag.PUMPBTCOMM, "" + periodAvailableOperations);
            double minBolusDose = getPumpType().getBolusSize();
            if (roundedPeriodDose >= minBolusDose) {
                int doses = new BigDecimal(
                        periodDose / minBolusDose
                ).setScale(0, RoundingMode.HALF_DOWN).intValue();
                BigDecimal calculatedDose = new BigDecimal(periodDose).divide(new BigDecimal(doses), 2, RoundingMode.HALF_DOWN);
                minDosage = new BigDecimal(periodDose / doses).setScale(1, RoundingMode.HALF_DOWN).doubleValue();
                List<Double> list = buildOperations(doses, periodAvailableOperations, Collections.emptyList());
                //TODO convert build operations to return list of tempmicroboluspair
                for (double dose : list) {
                    if (dose > 0) {
                        TempBasalMicroBolusPair pair = new TempBasalMicroBolusPair(0,
                                new BigDecimal(dose), calculatedDose, operationTime,
                                TempBasalMicroBolusPair.OperationType.BOLUS, callback);
                        result.operations.add(pair);
                    }
                    operationTime = operationTime.plusMinutes(Constants.INTERVAL_BETWEEN_OPERATIONS);
                }
            }
        }
        BigDecimal totalDose = result.operations.stream().map(TempBasalMicroBolusPair::getDose).reduce(BigDecimal.ZERO, BigDecimal::add);
        double doseDiff = totalDose.subtract(totalAmount).setScale(1,
                RoundingMode.HALF_UP).doubleValue();
        if (totalDose.compareTo(totalAmount) > 0 && Math.abs(doseDiff) >= minDosage) {
            result.operations = excludeExtraDose(totalDose, totalAmount, result);
            aapsLogger.info(LTag.AUTOMATION, "Error in temp basal microbolus calculation, totalDose: " + totalDose + ", totalAmount " + totalAmount + ", profiles " + insulinPeriod + ", absoluteRate: " + absoluteRate + ", insulin period" + insulinPeriod);
        } else if (totalAmount.compareTo(totalDose) > 0 && Math.abs(doseDiff) >= minDosage) {
            //TODO need a test to verify if this is reacheable
            aapsLogger.info(LTag.AUTOMATION, "Error in temp basal microbolus calculation, totalDose: " + totalDose + ", totalAmount " + totalAmount + ", profiles " + insulinPeriod + ", absoluteRate: " + absoluteRate + ", insulin period" + insulinPeriod);
//            throw new RuntimeException("Error in temp basal microbolus calculation, totalDose: " + totalDose + ", totalAmount " + totalAmount + ", profiles " + insulinPeriod);
        }
        return result;
    }

    private ConcurrentLinkedDeque<TempBasalMicroBolusPair> excludeExtraDose(BigDecimal totalDose,
                                                                            BigDecimal totalAmount,
                                                                            TempBasalMicrobolusOperations result) {
        int dosesToDecrease = totalDose.subtract(totalAmount).divide(new BigDecimal(getPumpType().getBolusSize().toString()), RoundingMode.HALF_DOWN).intValue();
        final BigDecimal maxDosage = result.operations.stream().map(
                TempBasalMicroBolusPair::getDose).max(BigDecimal::compareTo).orElse(new BigDecimal(0));
        final BigDecimal minDosage = result.operations.stream().map(
                TempBasalMicroBolusPair::getDose).min(BigDecimal::compareTo).orElse(new BigDecimal(0));
        ConcurrentLinkedDeque<TempBasalMicroBolusPair> operations = new ConcurrentLinkedDeque<>();
        if (maxDosage.equals(minDosage)) {
            Stream<TempBasalMicroBolusPair> sortedOperations = result.operations.stream().sorted((prev, curr) -> prev.getDelta().compareTo(curr.getDelta()));
            operations = sortedOperations.skip(dosesToDecrease).sorted((prev, curr) ->
                    prev.getReleaseTime().compareTo(curr.getReleaseTime())).
                    collect(Collectors.toCollection(ConcurrentLinkedDeque::new));
        } else {

            while (!result.operations.isEmpty()) {
                TempBasalMicroBolusPair tmp = result.operations.pollFirst();
                if (tmp.getDose().equals(maxDosage) && dosesToDecrease > 0) {
                    dosesToDecrease -= 1;
                    if (tmp.getDose().compareTo(new BigDecimal(getPumpType().getBolusSize().toString())) > 0) {
                        operations.add(tmp.decreaseDosage(getPumpType().getBolusSize()));
                    }
                } else {
                    operations.add(tmp);
                }
            }
        }
        return operations;

    }

    private List<Double> buildOperationsList(double doses, int operations, double dose) {
        Double[] result = new Double[operations];
        Arrays.fill(result, dose);
        return buildOperations(doses - operations, operations, Arrays.asList(result));
    }

    protected List<Double> buildOperations(double doses, int operations, List<Double> list) {
        if (list.isEmpty()) {
            Double[] values = new Double[operations];
            Arrays.fill(values, 0d);
            list = Arrays.asList(values);
        }
        if (doses == 0) {
            return list;
        } else if (doses >= operations) {
            Double val = list.get(0);
            return buildOperationsList(doses, operations, val + getPumpType().getBolusSize());
        } else {
            double step = (double) operations / doses;
            if (doses == 1) {
                int position = Math.floorDiv(operations, 2) - 1;
                list.set(position, list.get(position) + getPumpType().getBolusSize());
                return list;
            }
            if (step < 2.5) {
                return buildSmallStepsTempSMBDosage(operations, doses, list, step);
            } else {
                return buildBigStepsTempSMBDosage(operations, doses, list, step);
            }
        }
    }

    private List<Double> buildBigStepsTempSMBDosage(int operations, double doses, List<Double> list, double step) {
        double nextStep = step;
        list.set(0, list.get(0) + getPumpType().getBolusSize());
        doses--;
        for (int index = 1; index < operations; index++) {
            if (doses > 0 && nextStep < index) {
                doses--;
                list.set(index, list.get(index) + getPumpType().getBolusSize());
                nextStep = index + step;
            }
        }
        if (doses == 0) {
            return list;
        } else {
            //TODO unreachable code
            return buildOperations(doses, operations, list);
        }
    }

    private List<Double> buildSmallStepsTempSMBDosage(int operations, double doses, List<Double> list, double step) {
        double diff = operations - doses;
        if (diff == 3) {
            int newStep = Double.valueOf(Math.floor(operations * 0.33)).intValue();
            int secondPosition = 2 * newStep;
            int thirdPosition = 3 * newStep;
            return fillTempSMBWithExclusions(list, newStep, secondPosition, thirdPosition);
        } else if (diff == 2) {
            int newStep = Double.valueOf(Math.floor(operations * 0.25)).intValue();
            int half = (int) Math.round(operations / diff);
            int secondPosition = half + newStep;
            return fillTempSMBWithExclusions(list, newStep, secondPosition);
        } else if (diff == 1) {
            return fillTempSMBWithExclusions(list, Math.floorDiv(operations, 2));
        } else {
            return fillTempSMBWithStep(list, step, doses);
        }
    }

    private List<Double> fillTempSMBWithStep(List<Double> list, double step, double doses) {
        List<Double> result = new ArrayList<>();
        int index = 0;
        int stepIndex = 0;
        for (Double value : list) {
            double currentStep = step * stepIndex;
            if (doses == 0d) {
                result.add(value);
            } else if (index == 0) {
                stepIndex++;
                result.add(value + getPumpType().getBolusSize());
                doses--;
            } else if (currentStep <= index) {
                stepIndex++;
                result.add(value + getPumpType().getBolusSize());
                doses--;
            } else {
                result.add(value);
            }

            index++;
        }
        return result;
    }

    private List<Double> fillTempSMBWithExclusions(List<Double> list, Integer... exclusions) {
        List<Double> result = new ArrayList<>();
        int index = 0;
        for (Double value : list) {
            if (!Arrays.asList(exclusions).contains(index)) {
                result.add(value + getPumpType().getBolusSize());
            } else {
                result.add(value);
            }
            index++;
        }
        return result;
    }

    @NonNull @Override public void deliverTreatment(@NotNull DetailedBolusInfo detailedBolusInfo,
                                                    @NotNull Function1 func) {
        super.deliverTreatment(detailedBolusInfo, func);

    }

    @NonNull @Override public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        return super.deliverTreatment(detailedBolusInfo);
    }

    private PumpEnactResult scheduleTempBasalBolus(Integer percent, Integer durationInMinutes,
                                                   Profile profile, Function1 callback, double absoluteBasalValue, PumpTempBasalType basalType) {
        LocalDateTime currentTime = getCurrentTime();
        int currentTas = currentTime.getMillisOfDay() / 1000;
        Long endTas = currentTas + (durationInMinutes * 60L);
        LinkedList<Profile.ProfileValue> basalProfilesFromTemp = extractTempProfiles(profile, endTas, currentTas);

        tempbasalMicrobolusOperations = buildFirstLevelTempBasalMicroBolusOperations(percent,
                basalProfilesFromTemp, durationInMinutes, callback, absoluteBasalValue, basalType);
        refreshAnyStatusThatNeedsToBeRefreshed();
        return buildPumpEnactResult().success(true).comment(getResourceHelper().gs(R.string.medtronic_cmd_desc_set_tbr));
    }

    private LinkedList<Profile.ProfileValue> extractTempProfiles(Profile profile, Long endTas, Integer currentTas) {
        LinkedList<Profile.ProfileValue> tempBasalProfiles = new LinkedList<>();
        Profile.ProfileValue previousProfile = null;

        Profile.ProfileValue[] basalValues = cloneProfileValue(profile);

        for (Profile.ProfileValue basalValue : basalValues) {
            if (endTas < basalValue.timeAsSeconds) {
                if (previousProfile == null) {
                    tempBasalProfiles.add(basalValue);
                }
                break;
            }
            if (currentTas <= basalValue.timeAsSeconds) {
                if (tempBasalProfiles.isEmpty()) {
                    if (currentTas < basalValue.timeAsSeconds) {
                        tempBasalProfiles.add(previousProfile);
                    }
                    tempBasalProfiles.add(basalValue);
                } else if (previousProfile == null || basalValue.value != previousProfile.value) {
                    tempBasalProfiles.add(basalValue);
                }
            }
            previousProfile = basalValue;
        }
        if (tempBasalProfiles.isEmpty()) {
            if (previousProfile.timeAsSeconds < endTas && previousProfile.timeAsSeconds < currentTas) {
                tempBasalProfiles.add(previousProfile);
            }
        }
        if (endTas >= Constants.SECONDS_PER_DAY) {
            if (tempBasalProfiles.isEmpty() && previousProfile != null) {
                previousProfile.timeAsSeconds = currentTas;
                tempBasalProfiles.add(previousProfile);
            }
            tempBasalProfiles.addAll(extractTempProfiles(profile, endTas - Constants.SECONDS_PER_DAY, 0));
        }
        return tempBasalProfiles;
    }

    private Profile.ProfileValue[] cloneProfileValue(Profile profile) {
        Profile.ProfileValue[] basalValues = profile.getBasalValues();
        Profile.ProfileValue[] cloned = new Profile.ProfileValue[basalValues.length];
        for (int index = 0; index < cloned.length; index++) {
            Profile.ProfileValue profileValue = basalValues[index];
            cloned[index] = profile.new ProfileValue(profileValue.timeAsSeconds, profileValue.value);
        }
        return cloned;
    }

    protected PumpEnactResult buildPumpEnactResult() {
        return new PumpEnactResult(getInjector());
    }

    @NotNull @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        aapsLogger.debug(LTag.PUMP, "setTempBasalPercent [PumpPluginAbstract] - Not implemented.");
        return new PumpEnactResult(getInjector()).success(false).enacted(false).
                comment(getResourceHelper().gs(
                        info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver));
    }

    @NotNull @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes,
                                               Profile profile, boolean enforceNew, Function1 callback) {

        PumpEnactResult result;
        tempbasalMicrobolusOperations.operations.clear();
        if (percent == 100) {
            result = clearTempBasal();
        } else if (percent < 100) {
            result = scheduleSuspension(percent, durationInMinutes, profile, callback, 0d,
                    PumpTempBasalType.Percent);
        } else {
            result = scheduleTempBasalBolus(percent, durationInMinutes, profile, callback,
                    0d, PumpTempBasalType.Percent);
        }
//        result.success = false;
//        result.comment = MainApp.gs(R.string.pumperror);
        aapsLogger.debug("Settings temp basal percent: " + result);
        return result;
    }

    @NotNull @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = buildPumpEnactResult();
        result.success = false;
        medtronicUtil.sendNotification(MedtronicNotificationType.PumpExtendedBolusNotEnabled, getResourceHelper(), rxBus);
        aapsLogger.debug("Setting extended bolus: " + result);
        return result;
    }


    @NotNull @Override
    public void cancelTempBasal(Boolean enforceNew, Callback callback) {
        aapsLogger.info(LTag.EVENTS, "canceling temp basal");
        if (getPumpStatusData().pumpStatusType.equals(PumpStatusType.Suspended)) {
            startPump(callback);
        }
        this.tempbasalMicrobolusOperations.clearOperations();

        aapsLogger.debug("Cancel temp basal: " + result);

    }

    public PumpEnactResult extendBasalTreatment(int duration, Function1 callback) {
//TODO implement
        PumpEnactResult result = new PumpEnactResult(getInjector()).success(true).enacted(true).
                comment(getResourceHelper().gs(R.string.let_temp_basal_run));
        Optional<TempBasalMicroBolusPair> reactivateOper =
                tempbasalMicrobolusOperations.operations.stream().filter(f ->
                        f.getOperationType().equals(
                                TempBasalMicroBolusPair.OperationType.REACTIVATE)).findFirst();
        if (reactivateOper.isPresent()) {
            reactivateOper.get().setReleaseTime(duration);
            callback.invoke(result);
        }

        return result;
    }

    @NotNull @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = false;
        medtronicUtil.sendNotification(MedtronicNotificationType.PumpExtendedBolusNotEnabled, getResourceHelper(), rxBus);
        aapsLogger.debug("Canceling extended bolus: " + result);
        return result;
    }

    @NotNull @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.Medtronic;
    }

    @NotNull @Override
    public PumpType model() {
        return PumpType.MedLink_Medtronic_554_754_Veo;
    }

    @Override public PumpType getPumpType() {
        return super.getPumpType();
    }

    @NotNull @Override
    public String serialNumber() {
        return medLinkPumpStatus.serialNumber;
    }

    @NotNull @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @NotNull @Override
    public String shortStatus(boolean veryShort) {
        return model().getModel();
    }

    @Override
    public List<CustomAction> getCustomActions() {
        if (customActions == null) {
            this.customActions = Arrays.asList(
                    customActionClearBolusBlock);
        }

        return this.customActions;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {
        MedtronicCustomActionType mcat = (MedtronicCustomActionType) customActionType;

        switch (mcat) {

            case WakeUpAndTune: {
                if (medLinkService.verifyConfiguration()) {
                    serviceTaskExecutor.startTask(new WakeAndTuneTask(getInjector()));
                } else {
                    aapsLogger.debug("Medronic Pump plugin intent");
                    Intent i = new Intent(context, ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.boluserror);
                    i.putExtra("status", getResourceHelper().gs(R.string.medtronic_error_operation_not_possible_no_configuration));
                    i.putExtra("title", getResourceHelper().gs(R.string.medtronic_warning));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                }
            }
            break;

            case ClearBolusBlock: {
                this.busyTimestamps.clear();

//                this.customActionClearBolusBlock.setEnabled(false);
                refreshCustomActionsList();
            }
            break;

            case ResetRileyLinkConfiguration: {
                serviceTaskExecutor.startTask(new ResetRileyLinkConfigurationTask(getInjector()));
            }
            break;

            default:
                break;
        }

    }

    @Nullable @Override public PumpEnactResult executeCustomCommand(CustomCommand customCommand) {
        return null;
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

    public PumpEnactResult comparePumpBasalProfile(BasalProfile profile) {
        String validProfile = isProfileValid(profile);
        aapsLogger.info(LTag.PUMPBTCOMM, "valid profile " + validProfile);
        if (validProfile != null && !validProfile.isEmpty()) {
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_set_profile_pattern_overflow, validProfile));

        }

        if (this.profile == null) {

//            this.setNewBasalProfile(profile);
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_set_profile_pattern_overflow, profile.basalProfileToString()));

        } else if (!convertProfileToMedtronicProfile(this.profile).equals(profile)) {
//            aapsLogger.info(LTag.PUMPBTCOMM,profile.toString());
            aapsLogger.info(LTag.PUMPBTCOMM, this.profile.toString());
            ToastUtils.showToastInUiThread(context, resourceHelper.gs(info.nightscout.androidaps.plugins.pump.common.R.string.need_manual_profile_set, 40),
                    Toast.LENGTH_LONG);

            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_set_profile_pattern_overflow, profile.basalProfileToString()));
        } else {
            return new PumpEnactResult(getInjector()) //
                    .success(true) //
                    .enacted(true) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_set_profile_pattern_overflow));

        }

    }

    @NonNull @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setNewBasalProfile");

        // this shouldn't be needed, but let's do check if profile setting we are setting is same as current one

        if (isThisProfileSet(profile)) {
            return new PumpEnactResult(getInjector()) //
                    .success(true) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_basal_profile_not_set_is_same));
        }


        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);
            aapsLogger.info(LTag.PUMP, "pump unreachable");
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

        BasalProfile basalProfile = convertProfileToMedtronicProfile(profile);

        String profileInvalid = isProfileValid(basalProfile);

        if (profileInvalid != null) {
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_set_profile_pattern_overflow, profileInvalid));
        }

        ToastUtils.showToastInUiThread(context, resourceHelper.gs(info.nightscout.androidaps.plugins.pump.common.R.string.need_manual_profile_set, 40),
                Toast.LENGTH_LONG);

//        MedtronicUITask responseTask = medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.SetBasalProfileSTD,
//                basalProfile);
//
//        Boolean response = (Boolean) responseTask.returnData;
//
//        aapsLogger.info(LTag.PUMP, getLogPrefix() + "Basal Profile was set: " + response);
//
//        if (response) {
        return new PumpEnactResult(getInjector()).success(false).enacted(false);
//        } else {
//            return new PumpEnactResult(getInjector()).success(response).enacted(response) //
//                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_basal_profile_could_not_be_set));
//        }
    }

    private String isProfileValid(BasalProfile basalProfile) {

        StringBuilder stringBuilder = new StringBuilder();

        if (medLinkPumpStatus.maxBasal == null)
            return null;

        for (BasalProfileEntry profileEntry : basalProfile.getEntries()) {

            if (profileEntry.rate > medLinkPumpStatus.maxBasal) {
                stringBuilder.append(profileEntry.startTime.toString("HH:mm"));
                stringBuilder.append("=");
                stringBuilder.append(profileEntry.rate);
            }
        }

        return stringBuilder.length() == 0 ? null : stringBuilder.toString();
    }


    @NonNull
    private BasalProfile convertProfileToMedtronicProfile(Profile profile) {

        BasalProfile basalProfile = new BasalProfile(aapsLogger);

        for (int i = 0; i < 24; i++) {
            double rate = profile.getBasalTimeFromMidnight(i * 60 * 60);

            double v = pumpDescription.pumpType.determineCorrectBasalSize(rate);

            BasalProfileEntry basalEntry = new BasalProfileEntry(v, i, 0);
            basalProfile.addEntry(basalEntry);

        }

        basalProfile.generateRawDataFromEntries();

        return basalProfile;
    }


    private PumpEnactResult setNotReachable(boolean isBolus, boolean success) {
        setRefreshButtonEnabled(true);

        if (isBolus) {
            bolusDeliveryType = BolusDeliveryType.Idle;
        }

        if (success) {
            return new PumpEnactResult(getInjector()) //
                    .success(true) //
                    .enacted(false);
        } else {
            aapsLogger.info(LTag.PUMP, "pump unreachable");
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_pump_status_pump_unreachable));
        }
    }

    private CustomAction customActionClearBolusBlock = new CustomAction(
            R.string.medtronic_custom_action_clear_bolus_block, MedtronicCustomActionType.ClearBolusBlock, false);

    private void setEnableCustomAction(MedtronicCustomActionType customAction, boolean isEnabled) {

        if (customAction == MedtronicCustomActionType.ClearBolusBlock) {
            this.customActionClearBolusBlock.setEnabled(isEnabled);
        } else if (customAction == MedtronicCustomActionType.ResetRileyLinkConfiguration) {
            //TODO see if medlink will need this resetconfig
            //            this.customActionResetRLConfig.setEnabled(isEnabled);
        }

        refreshCustomActionsList();
    }

    @Override protected PumpEnactResult deliverBolus(DetailedBolusInfo detailedBolusInfo) {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - " + BolusDeliveryType.DeliveryPrepared);

        setRefreshButtonEnabled(false);

        if (detailedBolusInfo.insulin > medLinkPumpStatus.reservoirRemainingUnits) {
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_bolus_could_not_be_delivered_no_insulin,
                            medLinkPumpStatus.reservoirRemainingUnits,
                            detailedBolusInfo.insulin));
        }

        bolusDeliveryType = BolusDeliveryType.DeliveryPrepared;

        if (isPumpNotReachable()) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - Pump Unreachable.");
            return setNotReachable(true, false);
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

        if (bolusDeliveryType == MedtronicPumpPlugin.BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled.");
            return setNotReachable(true, true);
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Starting wait period.");

        int sleepTime = sp.getInt(MedtronicConst.Prefs.BolusDelay, 10) * 1000;

//        SystemClock.sleep(sleepTime);

        if (bolusDeliveryType == MedtronicPumpPlugin.BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled, before wait period.");
            return setNotReachable(true, true);
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - End wait period. Start delivery");

        try {

            bolusDeliveryType = MedtronicPumpPlugin.BolusDeliveryType.Delivering;

            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Start delivery");
            AtomicReference<Boolean> response = new AtomicReference<>(false);

            long bolusTimesstamp = 0l;

            BolusCallback bolusCallback = new BolusCallback(aapsLogger);
            Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> andThem = bolusCallback.andThen(f -> {

                BolusAnswer answer = f.getFunctionResult();
                if (answer.getResponse().equals(PumpResponses.BolusDelivered) &&
                        answer.getBolusAmount() == detailedBolusInfo.insulin) {

                    detailedBolusInfo.deliverAt = answer.getBolusDeliveryTime().
                            toInstant().toEpochMilli();
                    handleNewTreatmentData(Stream.of(detailedBolusInfo));
                } else if (answer.getResponse().equals(PumpResponses.DeliveringBolus)) {
                    lastDetailedBolusInfo = detailedBolusInfo;
                    lastBolusTime = System.currentTimeMillis();
                    aapsLogger.info(LTag.PUMPBTCOMM, "pump is deliverying");
                    response.set(true);
                    bolusInProgress(detailedBolusInfo, bolusDeliveryTime);
//                    processDeliveredBolus(answer, detailedBolusInfo);
                } else if (answer.getResponse().equals(PumpResponses.UnknowAnswer)) {
                    aapsLogger.info(LTag.PUMPBTCOMM, "pump is not deliverying");
//                    processDeliveredBolus(answer, detailedBolusInfo);
                    Intent i = new Intent(context, ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.boluserror);
                    i.putExtra("status", result.comment);
                    i.putExtra("title", resourceHelper.gs(R.string.medtronic_cmd_bolus_could_not_be_delivered));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                }
//                answer.get().forEach(x -> aapsLogger.info(LTag.PUMPBTCOMM, x));
//                bolusDeliveryTime = answer.get().findFirst().map(bolTime -> Long.valueOf(bolTime)).
//                        orElse(0l);
//                aapsLogger.info(LTag.PUMPBTCOMM, answer.get().collect(Collectors.joining()));
//                if (answer.get().anyMatch(ans -> ans.trim().equals("pump is delivering a bolus"))) {
//                    aapsLogger.info(LTag.PUMPBTCOMM, "pump is deliverying");
//                    response.set(true);
//                    bolusInProgress(detailedBolusInfo, bolusDeliveryTime);
//                    processDeliveredBolus(answer, detailedBolusInfo);
//                } else if (answer.get().anyMatch(ans -> ans.trim().equals("pump is not delivering a bolus"))) {
//                    aapsLogger.info(LTag.PUMPBTCOMM, detailedBolusInfo.toString());
//                    aapsLogger.info(LTag.PUMPBTCOMM, f.getFunctionResult());
//                    if (detailedBolusInfo.insulin < 0.5 && f.getFunctionResult().equals(
//                            PumpResponses.BolusDelivered.getAnswer())) {
//                        processDeliveredBolus(answer, detailedBolusInfo);
//                    } else {
//                        aapsLogger.info(LTag.PUMPBTCOMM, "pump is not deliverying");
//                        processDeliveredBolus(answer, detailedBolusInfo);
//                        Intent i = new Intent(context, ErrorHelperActivity.class);
//                        i.putExtra("soundid", R.raw.boluserror);
//                        i.putExtra("status", result.comment);
//                        i.putExtra("title", resourceHelper.gs(R.string.medtronic_cmd_bolus_could_not_be_delivered));
//                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                        context.startActivity(i);
//                    }
//                } else {
//                    aapsLogger.info(LTag.PUMPBTCOMM, "and themmmm");
//                }

                return new MedLinkStandardReturn<String>(() -> f.getAnswer(), f.getFunctionResult().getAnswer());
            });
            BolusMedLinkMessage msg = new BolusMedLinkMessage(detailedBolusInfo.insulin,
                    andThem,
                    medLinkServiceData,
                    aapsLogger);


            MedLinkMedtronicUITaskCp responseTask = medLinkService.getMedtronicUIComm().executeCommandCP(msg);

            setRefreshButtonEnabled(true);

//            int count = 0;
//            while ((!isPumpNotReachable() || bolusTimesstamp == 0l) && count < 15) {
//                SystemClock.sleep(5000);
//                count++;
//            }
//            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Response: {}", response);

            if (response.get() && !isPumpNotReachable()) {

                if (bolusDeliveryType == MedtronicPumpPlugin.BolusDeliveryType.CancelDelivery) {
                    // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled after Bolus started.");

                    new Thread(() -> {
                        // Looper.prepare();
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog - before");
                        SystemClock.sleep(2000);
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog. Context: "
                        // + MainApp.instance().getApplicationContext());

                        Intent i = new Intent(context, ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", getResourceHelper().gs(R.string.medtronic_cmd_cancel_bolus_not_supported));
                        i.putExtra("title", getResourceHelper().gs(R.string.medtronic_warning));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);

                    }).start();
                }

                long now = System.currentTimeMillis();

                detailedBolusInfo.date = now;
                detailedBolusInfo.deliverAt = now; // not sure about that one

//                activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);

                // we subtract insulin, exact amount will be visible with next remainingInsulin update.
                medLinkPumpStatus.reservoirRemainingUnits -= detailedBolusInfo.insulin;

                incrementStatistics(detailedBolusInfo.isSMB ? MedtronicConst.Statistics.SMBBoluses
                        : MedtronicConst.Statistics.StandardBoluses);


                // calculate time for bolus and set driver to busy for that time
                int bolusTime = (int) (detailedBolusInfo.insulin * 42.0d);
                long time = now + (bolusTime * 1000);

                this.busyTimestamps.add(time);
                setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, true);

                return new PumpEnactResult(getInjector()).success(true) //
                        .enacted(true) //
                        .bolusDelivered(detailedBolusInfo.insulin) //
                        .carbsDelivered(detailedBolusInfo.carbs);

            } else {
                return new PumpEnactResult(getInjector()) //
                        .success(bolusDeliveryType == MedtronicPumpPlugin.BolusDeliveryType.CancelDelivery) //
                        .enacted(false) //
                        .comment(getResourceHelper().gs(R.string.medtronic_cmd_bolus_could_not_be_delivered));
            }

        } finally {
            finishAction("Bolus");
            this.bolusDeliveryType = MedtronicPumpPlugin.BolusDeliveryType.Idle;
        }

    }

    private void processDeliveredBolus(BolusAnswer answer, DetailedBolusInfo detailedBolusInfo) {
        detailedBolusInfo.insulin = answer.getBolusAmount();
        detailedBolusInfo.date = answer.getBolusDeliveryTime().toInstant().toEpochMilli();
        activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);
        lastBolusTime = detailedBolusInfo.date;
        lastDeliveredBolus = detailedBolusInfo.insulin;
        lastDetailedBolusInfo = null;
//        if (answer.get().anyMatch(ans -> ans.trim().contains("recent bolus bl"))) {
//            answer.get().filter(ans -> ans.trim().contains("recent bolus bl")).forEach(
//                    bolusStr -> {
//                        Pattern bolusPattern = Pattern.compile("\\d{1,2}\\.\\d{1,2}");
//                        Matcher matcher = bolusPattern.matcher(bolusStr);
//                        if (matcher.find() &&
//                                detailedBolusInfo.insulin == Double.parseDouble(matcher.group(0))) {
//                            lastDeliveredBolus = detailedBolusInfo.insulin;
//                            lastDetailedBolusInfo = detailedBolusInfo;
//                        }
//                        Pattern datePattern = Pattern.compile("\\d{1,2}:\\d{2}");
//                        Matcher dateMatcher = datePattern.matcher(bolusStr);
//                        if (dateMatcher.find()) {
//                            String date = dateMatcher.group(0);
//                        }
//
//                    });
//        }
    }

    private void bolusInProgress(DetailedBolusInfo detailedBolusInfo, long actionTime) {
        aapsLogger.info(LTag.EVENTS, "bolus in progress");
        Treatment t = new Treatment();
        t.isSMB = detailedBolusInfo.isSMB;
        t.isTBR = detailedBolusInfo.isTBR;
        final EventOverviewBolusProgress bolusEvent = EventOverviewBolusProgress.INSTANCE;
        bolusEvent.setT(t);
        bolusEvent.setStatus(resourceHelper.gs(R.string.bolusdelivering, 0d, detailedBolusInfo.insulin));
        bolusEvent.setPercent(0);
        rxBus.send(bolusEvent);

//        Function<Supplier<Stream<String>>, MedLinkStandardReturn> function = new BolusHistoryCallback();
//        MedLinkPumpMessage msg = new BolusStatusMedLinkMessage(
//                MedLinkCommandType.StopStartPump,function, medLinkServiceData,
//                aapsLogger, bolusingEvent);
//        medLinkService.getMedtronicUIComm().executeCommandCP(msg);
        int bolusTime = (int) (detailedBolusInfo.insulin * 42.0d);
        long bolusEndTime = actionTime + (bolusTime * 1000);
        long bolusDelta = System.currentTimeMillis() - actionTime;
//        Thread bolusEventThread = new Thread() {
//            @Override public void run() {
//                super.run();
        while (bolusEndTime > System.currentTimeMillis() + 100) {
            long remaining = (bolusEndTime - System.currentTimeMillis()) / bolusDelta;
            final EventOverviewBolusProgress bolusEvt = EventOverviewBolusProgress.INSTANCE;
            bolusEvt.setT(t);
            bolusEvt.setPercent((int) remaining);
            bolusEvt.setStatus(resourceHelper.gs(R.string.medlink_delivered, remaining * detailedBolusInfo.insulin, detailedBolusInfo.insulin));
            rxBus.send(bolusEvt);
        }
        final EventOverviewBolusProgress bolusEvt = EventOverviewBolusProgress.INSTANCE;
        bolusEvt.setPercent(100);
        bolusEvt.setStatus(resourceHelper.gs(R.string.medlink_delivered, detailedBolusInfo.insulin, detailedBolusInfo.insulin));
        rxBus.send(bolusEvt);
        SystemClock.sleep(200);
        scheduleNextRefresh(PumpHistory, -5);
//                rxBus.send(new EventDismissBolusProgressIfRunning(new PumpEnactResult(getInjector()).
//                        success(true).enacted(true).bolusDelivered(detailedBolusInfo.insulin) //
//                        .carbsDelivered(detailedBolusInfo.carbs)));
//                rxBus.send(new ());
//                rxBus.send(new EventDismissBolusProgressIfRunning());
//            }
//        };
//        bolusEventThread.start();
    }

    @Override protected void deliverBolus(DetailedBolusInfo detailedBolusInfo,
                                          Function1<? super PumpEnactResult, Unit> func) {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - " + BolusDeliveryType.DeliveryPrepared);

        setRefreshButtonEnabled(false);

        if (detailedBolusInfo.insulin > medLinkPumpStatus.reservoirRemainingUnits) {
            func.invoke(new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_bolus_could_not_be_delivered_no_insulin,
                            medLinkPumpStatus.reservoirRemainingUnits,
                            detailedBolusInfo.insulin)));
        }

        bolusDeliveryType = BolusDeliveryType.DeliveryPrepared;

        if (isPumpNotReachable()) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - Pump Unreachable.");
            func.invoke(setNotReachable(true, false));
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

        if (bolusDeliveryType == MedtronicPumpPlugin.BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled.");
            func.invoke(setNotReachable(true, true));
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Starting wait period.");

        int sleepTime = sp.getInt(MedtronicConst.Prefs.BolusDelay, 10) * 1000;

//        SystemClock.sleep(sleepTime);

        if (bolusDeliveryType == MedtronicPumpPlugin.BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled, before wait period.");
            func.invoke(setNotReachable(true, true));
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - End wait period. Start delivery");

        try {

            bolusDeliveryType = MedtronicPumpPlugin.BolusDeliveryType.Delivering;

            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Start delivery");
            AtomicReference<Boolean> response = new AtomicReference<>(false);

            long bolusTimesstamp = 0l;

            BolusCallback bolusCallback = new BolusCallback(aapsLogger);
            Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> andThen = bolusCallback.andThen(f -> {
                Supplier<Stream<String>> answer = f::getAnswer;
                answer.get().forEach(x -> aapsLogger.info(LTag.PUMPBTCOMM, x));
//                aapsLogger.info(LTag.PUMPBTCOMM, f.getFunctionResult().getAnswer());
                if (PumpResponses.BolusDelivered.equals(f.getFunctionResult().getResponse())) {

                    processDeliveredBolus(f.getFunctionResult(), detailedBolusInfo);
                    bolusInProgress(detailedBolusInfo, bolusDeliveryTime);
                    func.invoke(new PumpEnactResult(getInjector()).success(true) //
                            .enacted(true) //
                            .bolusDelivered(detailedBolusInfo.insulin) //
                            .carbsDelivered(detailedBolusInfo.carbs));
                } else if (PumpResponses.UnknowAnswer.equals(f.getFunctionResult().getResponse())) {

                    aapsLogger.info(LTag.PUMPBTCOMM, "pump is not deliverying");
//                    processDeliveredBolus(f.getFunctionResult(), detailedBolusInfo);
                    func.invoke(new PumpEnactResult(getInjector()) //
                            .success(bolusDeliveryType == MedtronicPumpPlugin.BolusDeliveryType.CancelDelivery) //
                            .enacted(false) //
                            .comment(getResourceHelper().gs(R.string.medtronic_cmd_bolus_could_not_be_delivered)));
                    Intent i = new Intent(context, ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.boluserror);
                    i.putExtra("status", f.getAnswer().collect(Collectors.joining()));
                    i.putExtra("title", resourceHelper.gs(R.string.medtronic_cmd_bolus_could_not_be_delivered));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                } else if (PumpResponses.DeliveringBolus.equals(f.getFunctionResult().getResponse())){
                    lastDetailedBolusInfo = detailedBolusInfo;
                    lastBolusTime = System.currentTimeMillis();
                    aapsLogger.info(LTag.PUMPBTCOMM, "and themmmm");
                }
                Stream<String> recentBolus = answer.get().filter(ans -> ans.contains("recent bolus"));
                if (answer.get().findAny().isPresent()) {
                    String result = recentBolus.findAny().get();
                    aapsLogger.info(LTag.PUMPBTCOMM, result);
                    Pattern pattern = Pattern.compile("\\d{1,2}\\.\\d{1,2}");
                    Matcher matcher = pattern.matcher(result);
                    if (matcher.find()) {
                        Double bolusAmount = Double.valueOf(matcher.group(0));
                        detailedBolusInfo.insulin = bolusAmount;
                    }
                }
                return new MedLinkStandardReturn<String>(() -> f.getAnswer(), f.getFunctionResult().getAnswer());
            });
            BolusMedLinkMessage msg = new BolusMedLinkMessage(detailedBolusInfo.insulin,
                    andThen,
                    medLinkServiceData,
                    aapsLogger);


            medLinkService.getMedtronicUIComm().executeCommandCP(msg);

            setRefreshButtonEnabled(true);

//            int count = 0;
//            while ((!isPumpNotReachable() || bolusTimesstamp == 0l) && count < 15) {
//                SystemClock.sleep(5000);
//                count++;
//            }
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Response: {}", response);

            if (!isPumpNotReachable()) {

                if (bolusDeliveryType == MedtronicPumpPlugin.BolusDeliveryType.CancelDelivery) {
                    // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled after Bolus started.");

                    new Thread(() -> {
                        // Looper.prepare();
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog - before");
                        SystemClock.sleep(2000);
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog. Context: "
                        // + MainApp.instance().getApplicationContext());

                        Intent i = new Intent(context, ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", getResourceHelper().gs(R.string.medtronic_cmd_cancel_bolus_not_supported));
                        i.putExtra("title", getResourceHelper().gs(R.string.medtronic_warning));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);

                    }).start();
                }

                long now = System.currentTimeMillis();

                detailedBolusInfo.date = now;
                detailedBolusInfo.deliverAt = now; // not sure about that one

//                activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);

                // we subtract insulin, exact amount will be visible with next remainingInsulin update.
                medLinkPumpStatus.reservoirRemainingUnits -= detailedBolusInfo.insulin;

                incrementStatistics(detailedBolusInfo.isSMB ? MedtronicConst.Statistics.SMBBoluses
                        : MedtronicConst.Statistics.StandardBoluses);


                // calculate time for bolus and set driver to busy for that time
                int bolusTime = (int) (detailedBolusInfo.insulin * 42.0d);
                long time = now + (bolusTime * 1000);

                this.busyTimestamps.add(time);
//                setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, true);
            }

        } finally {
            finishAction("Bolus");
            this.bolusDeliveryType = MedtronicPumpPlugin.BolusDeliveryType.Idle;
        }

    }

    @Override protected void triggerUIChange() {
        rxBus.send(new EventMedtronicPumpValuesChanged());
    }

    @Override
    public void timezoneOrDSTChanged(TimeChangeType changeType) {
        aapsLogger.warn(LTag.PUMP, getLogPrefix() + "Time or TimeZone changed. ");
        this.hasTimeDateOrTimeZoneChanged = true;
    }


    @Nullable
    public MedLinkMedtronicService getMedLinkService() {
        return medLinkService;
    }

    @Override public RileyLinkPumpInfo getPumpInfo() {
        //TODO corrigir
        return null;
    }

    @Override public long getLastConnectionTimeMillis() {
        return getPumpStatusData().lastConnection;
    }

    @Override public void setLastCommunicationToNow() {
        medLinkPumpStatus.setLastCommunicationToNow();
    }

    void resetStatusState() {
        firstRun = true;
        isRefresh = true;
    }

    private Intent buildIntentSensValues(BgReading... bgs) {
        Intent intent = new Intent();
        intent.putExtra("sensorType", "Enlite");


        Bundle glucoseValues = new Bundle();
        Bundle fingerValues = new Bundle();
        int gvPosition = 0;
        int meterPosition = 0;
        for (BgReading bg : bgs) {
            if (bg.source == Source.USER) {
                aapsLogger.info(LTag.BGSOURCE, "User bg source");
                Bundle bgBundle = new Bundle();
                bgBundle.putDouble("meterValue", bg.value);
                bgBundle.putLong("timestamp", bg.date);
                fingerValues.putBundle("" + meterPosition, bgBundle);
                meterPosition++;
            } else {
                Bundle bgBundle = new Bundle();
                bgBundle.putDouble("value", bg.value);
                bgBundle.putLong("date", bg.date);
                bgBundle.putString("direction", bg.direction);
//            bgBundle.putString("raw", bg.raw);
                glucoseValues.putBundle("" + gvPosition, bgBundle);
                gvPosition++;
            }
        }
        intent.putExtra("glucoseValues", glucoseValues);
        intent.putExtra("meters", fingerValues);
        intent.putExtra("isigValues", new Bundle());
        return intent;
    }

    private Intent buildIntentSensValues(SensorDataReading... sensorDataReadings) {
        Intent intent = new Intent();
        intent.putExtra("sensorType", "Enlite");


        Bundle glucoseValues = new Bundle();
        Bundle fingerValues = new Bundle();
        Bundle isigValues = new Bundle();
        int gvPosition = 0;
        int meterPosition = 0;
        int isigPosition = 0;
        for (SensorDataReading sens : sensorDataReadings) {
            if (sens != null) {
                BgReading bg = sens.getBgReading();

                if (bg != null && bg.source == Source.USER) {
                    aapsLogger.info(LTag.BGSOURCE, "User bg source");
                    Bundle bgBundle = new Bundle();
                    bgBundle.putDouble("meterValue", sens.bgValue);
                    bgBundle.putLong("timestamp", sens.date);
                    fingerValues.putBundle("" + meterPosition, bgBundle);
                    meterPosition++;
                } else {
                    Bundle bgBundle = new Bundle();
                    bgBundle.putDouble("value", sens.bgValue);
                    bgBundle.putLong("date", sens.date);
                    bgBundle.putString("direction", sens.direction);
//                bgBundle.putString("raw", bg.raw);
                    glucoseValues.putBundle("" + gvPosition, bgBundle);
                    gvPosition++;
                    Bundle sensBundle = new Bundle();
                    sensBundle.putDouble("value", sens.bgValue);
                    sensBundle.putLong("date", sens.date);
                    sensBundle.putString("direction", sens.direction);
                    sensBundle.putDouble("calibrationFactor", sens.calibrationFactor);
                    sensBundle.putInt("sensorUptime", sens.sensorUptime);
                    sensBundle.putDouble("isig", sens.isig);
                    sensBundle.putDouble("delta", sens.deltaSinceLastBG);
                    isigValues.putBundle("" + isigPosition, sensBundle);
                    isigPosition++;
                }
            }
        }
        intent.putExtra("glucoseValues", glucoseValues);
        intent.putExtra("meters", fingerValues);
        intent.putExtra("isigValues", isigValues);
        return intent;
    }

//    private Bundle buildIntentSensValues(SensorDataReading... sens) {
//        Bundle isigValues = new Bundle();
//
//        int gvPosition = 0;
//        int meterPosition = 0;
//        for (SensorDataReading bg : sens) {
//            Bundle bgBundle = new Bundle();
//            bgBundle.putDouble("value", bg.bgValue);
//            bgBundle.putLong("date", bg.date);
//            bgBundle.putString("direction", bg.direction);
//            bgBundle.putDouble("calibrationFactor", bg.calibrationFactor);
//            bgBundle.putInt("sensorUptime", bg.sensorUptime);
//            bgBundle.putDouble("isig", bg.isig);
//            bgBundle.putDouble("delta", bg.deltaSinceLastBG);
//            isigValues.putBundle("" + gvPosition, bgBundle);
//            gvPosition++;
//        }
//        return isigValues;
//    }


    public void handleNewTreatmentData(Stream<DetailedBolusInfo> bolusInfos) {
        bolusInfos.forEachOrdered(bolusInfo -> {
            activePlugin.getActiveTreatments().addToHistoryTreatment(bolusInfo, false);
            if (bolusInfo.deliverAt > lastBolusTime) {
                lastBolusTime = bolusInfo.deliverAt;
            }
        });
    }

    public void handleNewEvent() {
        aapsLogger.info(LTag.EVENTS, " new event ");
        aapsLogger.info(LTag.EVENTS, "" + isInitialized);
        aapsLogger.info(LTag.EVENTS, "" + lastBolusTime);
        aapsLogger.info(LTag.EVENTS, "" + medLinkPumpStatus.lastBolusTime.getTime());
        aapsLogger.info(LTag.EVENTS, "" + pumpTimeDelta);
        if (isInitialized) {
            if (lastBolusTime != medLinkPumpStatus.lastBolusTime.getTime() &&
                    lastDeliveredBolus == medLinkPumpStatus.lastBolusAmount &&
                    Math.abs(lastBolusTime - medLinkPumpStatus.lastBolusTime.getTime()) >
                            pumpTimeDelta + 60000) {
                readBolusHistory();
            } else if (lastBolusTime > 0 && lastDetailedBolusInfo != null) {
                if (getPumpStatusData().lastBolusAmount == lastDetailedBolusInfo.insulin) {
                    lastDetailedBolusInfo.deliverAt = getPumpStatusData().lastBolusTime.getTime();
                    lastDetailedBolusInfo.date = getPumpStatusData().lastBolusTime.getTime();
                    activePlugin.getActiveTreatments().addToHistoryTreatment(lastDetailedBolusInfo, true);
                    lastDetailedBolusInfo = null;
                } else {
                    readBolusHistory();
                }
            }
        } else {
            lastBolusTime = medLinkPumpStatus.lastBolusTime.getTime();
        }
    }


    public void handleNewBgData(BgReading... sensorDataReadings) {
        if (lastStatus != medLinkPumpStatus.pumpStatusType.getStatus()) {
            if (medLinkPumpStatus.pumpStatusType.equals(PumpStatusType.Suspended) &&
                    (PumpStatusType.Running.getStatus().equals(lastStatus) ||
                            PumpStatusType.Initializing.getStatus().equals(lastStatus))) {
                createTemporaryBasalData(30, 0d);
            } else if (medLinkPumpStatus.pumpStatusType.equals(PumpStatusType.Running) &&
                    (PumpStatusType.Suspended.getStatus().equals(lastStatus) ||
                            PumpStatusType.Initializing.getStatus().equals(lastStatus))) {
                createTemporaryBasalData(0, 0d);
            }
        }
        if (sensorDataReadings.length == 1 &&
                medLinkPumpStatus.needToGetBGHistory()) {
            missedBGs++;
            if (firstMissedBGTimestamp == 0l) {
                firstMissedBGTimestamp = lastBGHistoryRead;
            }
        } else if (sensorDataReadings.length > 1) {
            if (sensorDataReadings[0].date > firstMissedBGTimestamp && System.currentTimeMillis() - lastPreviousHistory > 500000) {

                getPreviousBGHistory();

                lastPreviousHistory = System.currentTimeMillis();
            }
            missedBGs = 0;
            firstMissedBGTimestamp = 0l;
        }
        Intent intent = buildIntentSensValues(sensorDataReadings);
        activePlugin.getActiveBgSource().handleNewData(intent);
        handleNewEvent();
//        medtronicHistoryData.addNewHistory();
//        long latestbg = Arrays.stream(bgs).mapToLong(f -> f.date).max().orElse(0l);
//        if (bgFailedToRead > 6) {
//            ToastUtils.showToastInUiThread(context, R.string.pump_status_pump_unreachable);
//        } else if (System.currentTimeMillis() - latestBGHistoryRead > 60000 * 5) { //TODO
//            bgFailedToRead++;
        if(sensorDataReadings[0].value!=0d) {
            readPumpBGHistory(false);
        }
//        } else {
//            bgFailedToRead = 0;
//        }
    }


    public void handleNewSensorData(SensorDataReading... sens) {
        if (sens.length == 1 &&
                medLinkPumpStatus.needToGetBGHistory()) {
            missedBGs++;
            if (firstMissedBGTimestamp == 0l) {
                firstMissedBGTimestamp = lastBGHistoryRead;
            }
        } else if (sens.length > 1) {
            if (sens[0].date > firstMissedBGTimestamp && System.currentTimeMillis() - lastPreviousHistory > 500000) {
                getPreviousBGHistory();
                lastPreviousHistory = System.currentTimeMillis();
            }
            missedBGs = 0;
            firstMissedBGTimestamp = 0l;
        }
        Intent intent = buildIntentSensValues(sens);
        activePlugin.getActiveBgSource().handleNewData(intent);
        handleNewEvent();
        if(sens[0].bgValue !=0d){
            readPumpBGHistory(false);
        }
    }

    @Override public MedLinkTemporaryBasal getTemporaryBasal() {
        if (!tempbasalMicrobolusOperations.operations.isEmpty()) {
            MedLinkTemporaryBasal tempBasal = new MedLinkTemporaryBasal(getInjector());
            tempBasal.date(tempbasalMicrobolusOperations.operations.getFirst().getReleaseTime().
                    toDate().getTime());
            tempBasal.duration(
                    tempbasalMicrobolusOperations.getDurationInMinutes());
            tempBasal.setDesiredRate(
                    tempbasalMicrobolusOperations.getAbsoluteRate());
            if (tempbasalMicrobolusOperations.getAbsoluteRate() == 0d) {
                tempBasal.absolute(0d);
            } else {
                tempBasal.absolute(getBaseBasalRate());
            }
            return tempBasal;
        }
        return null;
    }
}
