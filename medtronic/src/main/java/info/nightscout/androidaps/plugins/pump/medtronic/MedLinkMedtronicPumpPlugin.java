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
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.joda.time.LocalDateTime;
import org.joda.time.Seconds;
import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.concurrent.LinkedBlockingDeque;
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
import info.nightscout.androidaps.db.ExtendedBolus;
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
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusDeliverCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BlePartialCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleStartCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleStopCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BolusAnswer;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BolusMedLinkMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BolusStatusMedLinkMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkStatusParser;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.PumpResponses;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.BGHistoryCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.BasalCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.BolusHistoryCallback;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BolusProgressCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.ChangeStatusCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.IsigHistoryCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.ProfileCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.activities.StatusCallback;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryResult;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedLinkMedtronicUITaskCp;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.BasalMedLinkMessage;
import info.nightscout.androidaps.plugins.pump.medtronic.data.NextStartStop;
import info.nightscout.androidaps.plugins.pump.medtronic.data.StartStopDateTime;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BatteryStatusDTO;
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
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicStatusRefreshType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCustomActionType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;
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
import info.nightscout.androidaps.receivers.ReceiverStatusStore;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
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
    private int minimumBatteryLevel = 5;

    private long batteryLastRead = 0l;
    private int lastBatteryLevel = 0;
    private double batteryDelta = 0;
    private final SP sp;
    private final MedLinkMedtronicUtil medtronicUtil;
    private final MedLinkMedtronicPumpStatus medLinkPumpStatus;
    private final MedLinkMedtronicHistoryData medtronicHistoryData;

    private final MedLinkServiceData medLinkServiceData;
    private final ReceiverStatusStore receiverStatusStore;

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
    private boolean late1Min;
    private boolean checkBolusAtNextStatus;
    private long lastProfileRead;


    @NotNull @Override public Constraint<Double> applyBasalConstraints(@NotNull Constraint<Double> absoluteRate, @NotNull Profile profile) {
        return null;
    }


    public void stopPump(Callback callback) {
        getAapsLogger().info(LTag.PUMP, "MedtronicPumpPlugin::stopPump - ");
        getAapsLogger().info(LTag.PUMP, "batteryDelta " + batteryDelta);
        int currentLevel = receiverStatusStore.getBatteryLevel();
        if (currentLevel - batteryDelta * 5 <= minimumBatteryLevel ||
                currentLevel <= minimumBatteryLevel || (
                getPumpStatusData().batteryRemaining != 0 &&
                        getPumpStatusData().batteryRemaining <= minimumBatteryLevel)) {
            Intent i = new Intent(context, ErrorHelperActivity.class);
            i.putExtra("soundid", R.raw.boluserror);
            i.putExtra("status", getResourceHelper().gs(R.string.medlink_medtronic_cmd_stop_could_not_be_delivered));
            i.putExtra("title", getResourceHelper().gs(R.string.medtronic_errors));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return;
        }
        if (!PumpStatusType.Suspended.equals(medLinkPumpStatus.pumpStatusType)) {

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
                    getBtSleepTime(), new BleStopCommand(aapsLogger,
                    getMedLinkService().getMedLinkServiceData()));
            getMedLinkService().getMedtronicUIComm().executeCommandCP(message);
        }
    }

    public long getBtSleepTime() {
        Integer sleepTime = sp.getInt(resourceHelper.gs(R.string
                .medlink_key_interval_between_bt_connections), 5);
        return sleepTime * 1000L;
    }

    public void startPump(Callback callback) {
        this.startPump(callback, false);
    }

    public void startPump(Callback callback, boolean prepend) {
        getAapsLogger().info(LTag.PUMP, "MedtronicPumpPlugin::startPump - ");

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
                    getBtSleepTime(), prepend,
                    new BleStartCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));
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
            ReceiverStatusStore receiverStatusStore,
            DateUtil dateUtil
    ) {

        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(MedLinkMedtronicFragment.class.getName()) //
                        .pluginName(R.string.medlink_medtronic_name) //
                        .pluginIcon(R.drawable.ic_veo_medlink)
                        .shortName(R.string.medlink_medtronic_name_short) //
                        .preferencesId(R.xml.pref_medtronic_medlink)
                        .description(R.string.description_pump_medtronic_medlink), //
                PumpType.MedLink_Medtronic_554_754_Veo, // we default to most basic model, correct model from config is loaded later
                injector, resourceHelper, aapsLogger, commandQueue, rxBus, activePlugin, sp, context, fabricPrivacy, dateUtil
        );

        this.receiverStatusStore = receiverStatusStore;
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
                getAapsLogger().debug(LTag.PUMP, "MedLinkMedtronicService is disconnected");
                medLinkService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                getAapsLogger().debug(LTag.PUMP, "MedLinkMedtronicService is connected");
                MedLinkMedtronicService.LocalBinder mLocalBinder = (MedLinkMedtronicService.LocalBinder) service;
                medLinkService = mLocalBinder.getServiceInstance();
                medLinkService.verifyConfiguration();

                new Thread(() -> {

                    for (int i = 0; i < 20; i++) {
                        SystemClock.sleep(5000);

                        getAapsLogger().debug(LTag.PUMP, "Starting Medtronic-MedLink service");
                        if (medLinkService.setNotInPreInit()) {
                            getAapsLogger().debug("MedlinkService setnotinpreinit");
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

        getAapsLogger().debug(LTag.PUMP, "initPumpStatusData: " + this.medLinkPumpStatus);

        // this is only thing that can change, by being configured
        pumpDescription.maxTempAbsolute = (medLinkPumpStatus.maxBasal != null) ? medLinkPumpStatus.maxBasal : 35.0d;

        pumpDescription.tempBasalStyle = PumpDescription.PERCENT;
        pumpDescription.tempPercentStep = 1;

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

        Object initC = resourceHelper.gs(R.string.key_medlink_init_command);
//        if(initC instanceof HashSet) {
        this.initCommands = sp.getStringSet(resourceHelper.gs(R.string.key_medlink_init_command), Collections.emptySet());
//        }else{
//            this.initCommands= new HashSet<>();
//        }

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
            getAapsLogger().debug(LTag.PUMP, "MedtronicPumpPlugin::isInitialized");
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
            getAapsLogger().debug(LTag.PUMP, "MedLinkMedtronicPumpPlugin::isConnected");
        return isServiceSet() && medLinkService.isInitialized();
    }

    @Override
    public boolean isConnecting() {
        if (displayConnectionMessages)
            getAapsLogger().debug(LTag.PUMP, "MedtronicPumpPlugin::isConnecting");
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

            getAapsLogger().info(LTag.PUMP, "Next Command " + Math.round(
                    (refreshType.getValue() - System.currentTimeMillis())));
            getAapsLogger().info(LTag.PUMP, refreshType.getKey().name());

            if (((refreshType.getValue() -
                    System.currentTimeMillis())) <= 0) {
                return true;
            }
        }
        for (TempBasalMicroBolusPair oper : tempbasalMicrobolusOperations.getOperations()) {

            getAapsLogger().info(LTag.PUMP, "Next Command " +
                    oper.getReleaseTime().toDateTime());
            getAapsLogger().info(LTag.PUMP, oper.toString());

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
                getAapsLogger().warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid. Year was not the same.");
                return 0L;
            }

            return lastPumpEntryTime;

        } catch (Exception ex) {
            getAapsLogger().warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid.");
            return 0L;
        }

    }


    private void readPumpHistoryLogic() {

        boolean debugHistory = false;

        LocalDateTime targetDate = null;

        if (lastPumpHistoryEntry == null) {

            if (debugHistory)
                getAapsLogger().debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: null");

            Long lastPumpHistoryEntryTime = getLastPumpEntryTime();

            LocalDateTime timeMinus36h = new LocalDateTime();
            timeMinus36h = timeMinus36h.minusHours(36);
            medtronicHistoryData.setIsInInit(true);

            if (lastPumpHistoryEntryTime == 0L) {
                if (debugHistory)
                    getAapsLogger().debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: 0L - targetDate: "
                            + targetDate);
                targetDate = timeMinus36h;
            } else {
                // LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);

                if (debugHistory)
                    getAapsLogger().debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: " + lastPumpHistoryEntryTime + " - targetDate: " + targetDate);

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
                    getAapsLogger().debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): targetDate: " + targetDate);
            }
        } else {
            if (debugHistory)
                getAapsLogger().debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: not null - " + medtronicUtil.gsonInstance.toJson(lastPumpHistoryEntry));
            medtronicHistoryData.setIsInInit(false);
            // medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntry.atechDateTime);

            // targetDate = lastPumpHistoryEntry.atechDateTime;
        }

        //getAapsLogger().debug(LTag.PUMP, "HST: Target Date: " + targetDate);


        MedLinkPumpMessage msg = new MedLinkPumpMessage(MedLinkCommandType.GetState,
                new StatusCallback(aapsLogger, this,
                        medLinkPumpStatus),
                getBtSleepTime(),
                new BleCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));
        getMedLinkService().getMedtronicUIComm().executeCommandCP(msg);

        if (debugHistory)
            getAapsLogger().debug(LTag.PUMP, "HST: After task");

//        PumpHistoryResult historyResult = (PumpHistoryResult) responseTask2.returnData;

//        if (debugHistory)
//            getAapsLogger().debug(LTag.PUMP, "HST: History Result: " + historyResult.toString());

//        PumpHistoryEntry latestEntry = historyResult.getLatestEntry();

//        if (debugHistory)
//            getAapsLogger().debug(LTag.PUMP, getLogPrefix() + "Last entry: " + latestEntry);

//        if (latestEntry == null) // no new history to read
//            return;

//        this.lastPumpHistoryEntry = latestEntry;
//        sp.putLong(MedtronicConst.Statistics.LastPumpHistoryEntry, latestEntry.atechDateTime);

//        if (debugHistory)
//            getAapsLogger().debug(LTag.PUMP, "HST: History: valid=" + historyResult.validEntries.size() + ", unprocessed=" + historyResult.unprocessedEntries.size());
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
                System.currentTimeMillis() - lastBGHistoryRead > 610000 &&
                (System.currentTimeMillis() - firstMissedBGTimestamp > 1800000) ||
                missedBGs > 4 || force)) {


            this.lastBGHistoryRead = System.currentTimeMillis();
            BGHistoryCallback func =
                    new BGHistoryCallback(getInjector(), this, aapsLogger, false);

            IsigHistoryCallback isigFunc =
                    new IsigHistoryCallback(getInjector(), this, aapsLogger, false, func);


            MedLinkPumpMessage msg = new MedLinkPumpMessage(MedLinkCommandType.BGHistory,
                    MedLinkCommandType.IsigHistory,
//                        MedLinkCommandType.NoCommand,
                    func,
                    isigFunc,
//                        null,
                    getBtSleepTime(),
                    new BlePartialCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));
            medLinkService.getMedtronicUIComm().executeCommandCP(msg);

//            MedLinkPumpMessage msg = new MedLinkPumpMessage(MedLinkCommandType.BGHistory);
            medLinkService.getMedtronicUIComm().executeCommandCP(msg);
        }
    }

    protected void readPumpHistory() {
//        if (isLoggingEnabled())
//            LOG.error(getLogPrefix() + "readPumpHistory WIP.");

        scheduleNextReadState();
        getAapsLogger().info(LTag.CONFIGBUILDER, "read pump history");
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
            getAapsLogger().debug(LTag.PUMP, getLogPrefix() + "isPumpSuspended: true");
        } else {
            if (previousState == PumpDriverState.Suspended) {
                this.pumpState = PumpDriverState.Ready;
            }
            getAapsLogger().debug(LTag.PUMP, getLogPrefix() + "isPumpSuspended: false");
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
            if (aapsLogger != null) {
                getAapsLogger().info(LTag.CONFIGBUILDER, "Last Connection" + getPumpStatusData().getLastConnection());
                getAapsLogger().info(LTag.CONFIGBUILDER, "Next Delta " + minutesDelta);
                getAapsLogger().info(LTag.CONFIGBUILDER, "bgdelta  " + bgDelta);
                getAapsLogger().info(LTag.CONFIGBUILDER, "Last connection " + DateUtil.timeFullString(getPumpStatusData().getLastConnection()));
                getAapsLogger().info(LTag.CONFIGBUILDER, "Last bg " + DateUtil.timeFullString(getPumpStatusData().getLastBGTimestamp()));
            }
            while (bgDelta >= 5) {
                bgDelta -= 5;
            }
            int lostContactDelta = 0;
            if (getPumpStatusData().getLastConnection() - getPumpStatusData().getLastBGTimestamp() > 360000 || late1Min) {
                lostContactDelta = 2;
            }
            scheduleNextRefresh(PumpHistory, lostContactDelta + minutesDelta + 3 - bgDelta);
        } else {
            getAapsLogger().info(LTag.CONFIGBUILDER, "scheduling");
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
        double sum = 0;
        for (int value : lastBgs) {
            sum += value;
        }
        return sum / lastBgs.length;
    }

    protected void refreshAnyStatusThatNeedsToBeRefreshed() {

        Map<MedLinkMedtronicStatusRefreshType, Long> statusRefresh = workWithStatusRefresh(MedLinkMedtronicPumpPlugin.StatusRefreshAction.GetData, null,
                null);

        if (!doWeHaveAnyStatusNeededRefreshing(statusRefresh)) {
            return;
        }

        boolean resetTime = false;

        if (isPumpNotReachable()) {
            getAapsLogger().error("Pump unreachable.");
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

        if (!tempbasalMicrobolusOperations.getOperations().isEmpty() &&
                tempbasalMicrobolusOperations.getOperations().getFirst().getReleaseTime().
                        isBefore(LocalDateTime.now().plus(Seconds.seconds(30))) &&
                !tempbasalMicrobolusOperations.getOperations().getFirst().isCommandIssued()) {
            TempBasalMicroBolusPair oper = tempbasalMicrobolusOperations.getOperations().peek();
            getAapsLogger().info(LTag.PUMP, "Next Command " +
                    oper.getReleaseTime().toDateTime());
            getAapsLogger().info(LTag.PUMP, oper.toString());
            oper.setCommandIssued(true);
            switch (oper.getOperationType()) {
                case SUSPEND: {
                    if (!PumpStatusType.Suspended.equals(getPumpStatusData().pumpStatusType)) {
                        stopPump(new Callback() {
                            @Override public void run() {
                                if (medLinkPumpStatus.pumpStatusType.equals(PumpStatusType.Suspended)) {
                                    tempbasalMicrobolusOperations.getOperations().poll();
                                    oper.setCommandIssued(true);
                                }
                                tempbasalMicrobolusOperations.setShouldBeSuspended(true);
                            }
                        });
                    } else {
                        tempbasalMicrobolusOperations.getOperations().poll();
                    }
                }
                break;
                case REACTIVATE: {
                    Function1 callback = oper.getCallback();
                    reactivatePump(oper, f -> {
                        tempbasalMicrobolusOperations.setShouldBeSuspended(false);
                        return callback;
                    });
                }
                break;
                case BOLUS: {
                    if (PumpStatusType.Suspended.equals(getPumpStatusData().pumpStatusType)) {
                        reactivatePump(oper, new Function1() {
                            @Override public Object invoke(Object o) {
                                return o;
                            }
                        });
                    }
                    DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                    detailedBolusInfo.lastKnownBolusTime = getPumpStatusData().lastBolusTime.getTime();
                    detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS;
                    detailedBolusInfo.insulin = oper.getDose();
                    detailedBolusInfo.source = Source.PUMP;
                    detailedBolusInfo.isTBR = true;
                    detailedBolusInfo.deliverAt = System.currentTimeMillis();
                    getAapsLogger().debug(LTag.APS, "applyAPSRequest: bolus()");
                    Function1 callback = new Function1<PumpEnactResult, Unit>() {
                        @Override public Unit invoke(PumpEnactResult o) {
                            getAapsLogger().info(LTag.PUMPBTCOMM, "temp basal bolus " + o.toString());
                            if (o.success) {
                                tempbasalMicrobolusOperations.getOperations().poll();
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

            getAapsLogger().info(LTag.PUMP, "Next Command " + Math.round(
                    (refreshType.getValue() - System.currentTimeMillis())));
            getAapsLogger().info(LTag.PUMP, refreshType.getKey().name());

            if ((refreshType.getValue() -
                    System.currentTimeMillis()) <= 0) {
//                scheduleNextRefresh(refreshType.getKey());
                switch (refreshType.getKey()) {
                    case PumpHistory: {
                        getAapsLogger().info(LTag.PUMPBTCOMM, "refreshing");
                        readPumpHistory();
                    }
                    break;

                    case PumpTime: {
                        checkTimeAndOptionallySetTime();
                        refreshTypesNeededToReschedule.add(refreshType.getKey());
                        resetTime = true;
                    }
                    break;


//                    case Configuration: {
//                        medLinkService.getMedtronicUIComm().executeCommand(refreshType.getKey().getCommandType(medtronicUtil.getMedtronicPumpModel()));
//                        resetTime = true;
//                    }
//                    break;
                }
            }
        }


        if (statusRefreshMap.isEmpty() || System.currentTimeMillis() - lastTryToConnect >= 600000) {
            lastTryToConnect = System.currentTimeMillis();
            readPumpHistory();
            scheduleNextRefresh(PumpHistory);
            getAapsLogger().info(LTag.PUMPBTCOMM, "posthistory");
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
                    getAapsLogger().info(LTag.PUMPBTCOMM, "busy " + oper);
                    callback.invoke(new PumpEnactResult(getInjector()) //
                            .success(false) //
                            .enacted(false) //
                            .comment(getResourceHelper().gs(R.string.tempbasaldeliveryerror)));
                    break;
                case Connected:
                    getAapsLogger().info(LTag.PUMPBTCOMM, "connected " + oper);
                    callback.invoke(new PumpEnactResult(getInjector()) //
                            .success(isConnect) //
                            .enacted(isConnect) //
                            .comment(getResourceHelper().gs(R.string.careportal_tempbasalend)));
                    if (isConnect) {
                        lastStatus = PumpStatusType.Running.getStatus();
                        this.tempbasalMicrobolusOperations.getOperations().peek();
                    } else {
                        oper.setCommandIssued(false);
                    }
                    break;
                case Suspended:
                    getAapsLogger().info(LTag.PUMPBTCOMM, "suspended " + oper);
                    callback.invoke(new PumpEnactResult(getInjector()) //
                            .success(!isConnect) //
                            .enacted(!isConnect) //
                            .comment(getResourceHelper().gs(R.string.careportal_tempbasalstart)));
                    if (!isConnect) {
                        lastStatus = PumpStatusType.Suspended.getStatus();
                        this.tempbasalMicrobolusOperations.getOperations().peek();
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
        getAapsLogger().info(LTag.EVENTS, "CreateTemporaryData");
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
                getBtSleepTime(),
                new BleStopCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));
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

    public void sendChargingEvent() {
//        rxBus.send(new EventChargingState(false, medLinkPumpStatus.batteryVoltage));
    }

    public void sendPumpUpdateEvent() {
        rxBus.send(new EventMedtronicPumpValuesChanged());
    }

    private void initializePump(boolean b) {

        getAapsLogger().info(LTag.PUMP, getLogPrefix() + "initializePump - start");
        getAapsLogger().info(LTag.PUMP, getLogPrefix() + b);
        if (medLinkService != null && medLinkService.getDeviceCommunicationManager() != null) {
            if (!b) {
                medLinkService.getDeviceCommunicationManager().wakeUp(false);
            }
            medLinkService.getDeviceCommunicationManager().setDoWakeUpBeforeCommand(false);
        } else {
            getAapsLogger().info(LTag.PUMP, "nullmedlinkservice" + medLinkService);
        }

        setRefreshButtonEnabled(false);
        readPumpHistory();
    }

    public void postInit() {
        if (isRefresh) {
            if (isPumpNotReachable()) {
                getAapsLogger().error(getLogPrefix() + "initializePump::Pump unreachable.");
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
                    func,
                    getBtSleepTime(),
                    new BleCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));
            medLinkService.getMedtronicUIComm().executeCommandCP(message);
        } else {
            if (medLinkPumpStatus.medtronicDeviceType != medtronicUtil.getMedtronicPumpModel()) {
//                getAapsLogger().warn(LTag.PUMP, getLogPrefix() + "Configured pump is not the same as one detected.");
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
        getAapsLogger().info(LTag.PUMPBTCOMM, "postinit");
        scheduleNextRefresh(PumpHistory);


        // configuration (once and then if history shows config changes)
//        medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.getSettings(medtronicUtil.getMedtronicPumpModel()));

        // read profile (once, later its controlled by isThisProfileSet method)

//        if (initCommands.contains(resourceHelper.gs(R.string.key_medlink_init_commands_profile))) {
        readPumpProfile();
//        }


//        readPumpBGHistory(true);

//        if (initCommands.contains(resourceHelper.gs(R.string.key_medlink_init_commands_previous_bg_history))) {
            getPreviousBGHistory();
//        }
        if (initCommands.contains(resourceHelper.gs(R.string.key_medlink_init_commands_last_bolus_history))) {
            readBolusHistory();
        }


        int errorCount = medLinkService.getMedtronicUIComm().getInvalidResponsesCount();

        if (errorCount >= 5) {
            getAapsLogger().error("Number of error counts was 5 or more. Starting tunning.");
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
        getAapsLogger().info(LTag.EVENTS, "pump initialized");
    }

    private void readBolusHistory() {


        getAapsLogger().info(LTag.PUMPBTCOMM, "get full bolus history");

        lastBolusHistoryRead = System.currentTimeMillis();
        BolusHistoryCallback func =
                new BolusHistoryCallback(aapsLogger, this);

        MedLinkPumpMessage msg = new MedLinkPumpMessage(MedLinkCommandType.BolusHistory,
                func,
                getBtSleepTime()
                , new BleCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));
        medLinkService.getMedtronicUIComm().executeCommandCP(msg);

    }

    private void getPreviousBGHistory() {
        getAapsLogger().info(LTag.PUMPBTCOMM, "get bolus history");
        BGHistoryCallback func =
                new BGHistoryCallback(getInjector(), this, aapsLogger, true);
        IsigHistoryCallback isigFunc =
                new IsigHistoryCallback(getInjector(), this, aapsLogger, false, func);

        MedLinkPumpMessage msg = new MedLinkPumpMessage(
                MedLinkCommandType.PreviousBGHistory,
//                MedLinkCommandType.PreviousIsigHistory,
                MedLinkCommandType.NoCommand,
                func,
//                isigFunc,
                getBtSleepTime(),
                new BlePartialCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));
        medLinkService.getMedtronicUIComm().executeCommandCP(msg);
    }

    private void checkTimeAndOptionallySetTime() {

        getAapsLogger().info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Start");

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {
            getAapsLogger().debug(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Pump Unreachable.");
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

                getAapsLogger().info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Set time on pump.", timeDiff);

//                medLinkService.getMedtronicUIComm().executeCommand(MedLinkMedtronicCommandType.SetRealTimeClock);

                if (clock.timeDifference == 0) {
                    Notification notification = new Notification(Notification.INSIGHT_DATE_TIME_UPDATED, getResourceHelper().gs(R.string.pump_time_updated), Notification.INFO, 60);
                    rxBus.send(new EventNewNotification(notification));
                }
            } else {
                if ((clock.localDeviceTime.getYear() > 2015)) {
                    getAapsLogger().error("MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference over 24h requested [diff={} s]. Doing nothing.", timeDiff);
                    medtronicUtil.sendNotification(MedtronicNotificationType.TimeChangeOver24h, getResourceHelper(), rxBus);
                }
            }

        } else {
            getAapsLogger().info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Do nothing.", timeDiff);
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

    private void readPumpProfile() {
        lastProfileRead = System.currentTimeMillis();
        getAapsLogger().info(LTag.PUMPBTCOMM, "get basal profiles");
        BasalCallback func =
                new BasalCallback(aapsLogger, this);
        ProfileCallback profileCallback =
                new ProfileCallback(injector, aapsLogger, context, this);
        MedLinkPumpMessage msg = new BasalMedLinkMessage(MedLinkCommandType.ActiveBasalProfile,
                MedLinkCommandType.BaseProfile, func, profileCallback,
                getBtSleepTime()
                , new BleCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));
        medLinkService.getMedtronicUIComm().executeCommandCP(msg);

//        if (medtronicUITask.getResponseType() == MedtronicUIResponseType.Error) {
//            getAapsLogger().info(LTag.PUMP, "reprocessing due to error response type");
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

            case PumpHistory: {
                if (aapsLogger != null) {
                    getAapsLogger().info(LTag.PUMPBTCOMM, "Next refresh will be in " +
                            getTimeInFutureFromMinutes(refreshType.getRefreshTime() + additionalTimeInMinutes));
                }
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
                getAapsLogger().info(LTag.PUMPBTCOMM, new DateTime(time).toString());
//                if(!statusRefreshMap.containsKey(statusRefreshType)) {
                statusRefreshMap.put(statusRefreshType, time);
//                }
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
        getAapsLogger().debug(LTag.PUMP, "isThisProfileSet: basalInitalized=" + medLinkPumpStatus.basalProfileStatus);

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

        getAapsLogger().debug(LTag.PUMP, "Current Basals (h):   "
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

        getAapsLogger().debug(LTag.PUMP, stringBuilder.toString());

        if (!invalid) {
            getAapsLogger().debug(LTag.PUMP, "Basal profile is same as AAPS one.");
        } else {
            getAapsLogger().debug(LTag.PUMP, "Basal profile on Pump is different than the AAPS one.");
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
            getAapsLogger().debug(LTag.PUMP, "MedLink unreachable. MedLinkServiceState is null.");
            return false;
        }

        if (medLinkServiceState != MedLinkServiceState.PumpConnectorReady //
                && medLinkServiceState != MedLinkServiceState.MedLinkReady //
                && medLinkServiceState != MedLinkServiceState.TuneUpDevice) {
            getAapsLogger().debug(LTag.PUMP, "RileyLink unreachable.");
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
        getAapsLogger().info(LTag.PUMPBTCOMM, "absolute rate " + absoluteRate + " " + durationInMinutes);
        getAapsLogger().info(LTag.PUMPBTCOMM, "gettempBasal " + getTemporaryBasal());
        if (absoluteRate != 0d && (absoluteRate == getBaseBasalRate() ||
                Math.abs(absoluteRate - getBaseBasalRate()) < getPumpDescription().bolusStep)) {
            getAapsLogger().info(LTag.EVENTS, "cancelling temp basal");
            getAapsLogger().info(LTag.EVENTS, "" + getBaseBasalRate());
            getAapsLogger().info(LTag.EVENTS, "" + absoluteRate);
            getAapsLogger().info(LTag.EVENTS, "" + getPumpDescription().bolusStep);
            tempbasalMicrobolusOperations.getOperations().clear();
            clearTempBasal();
            return new PumpEnactResult(getInjector()).enacted(true).success(true);
        } else if (getTemporaryBasal() != null && getTemporaryBasal().getDesiredRate() == absoluteRate && absoluteRate == 0d) {
            getAapsLogger().info(LTag.EVENTS, "extendbasaltreatment");
            result = extendBasalTreatment(durationInMinutes, callback);
        } else {

            if (absoluteRate == medLinkPumpStatus.getCurrentBasal() ||
                    Math.abs(absoluteRate - medLinkPumpStatus.getCurrentBasal()) < pumpDescription.bolusStep) {
                getAapsLogger().info(LTag.EVENTS, "clearing temp basal");
                getAapsLogger().info(LTag.EVENTS, "" + getBaseBasalRate());
                getAapsLogger().info(LTag.EVENTS, "" + absoluteRate);
                getAapsLogger().info(LTag.EVENTS, "" + getPumpDescription().bolusStep);
                result = clearTempBasal();
            } else if (absoluteRate < medLinkPumpStatus.getCurrentBasal()) {
                getAapsLogger().info(LTag.EVENTS, "suspending");
                getAapsLogger().info(LTag.EVENTS, "" + getBaseBasalRate());
                getAapsLogger().info(LTag.EVENTS, "" + absoluteRate);
                getAapsLogger().info(LTag.EVENTS, "" + getPumpDescription().bolusStep);
                tempbasalMicrobolusOperations.clearOperations();
                result = scheduleSuspension(0, durationInMinutes, profile, callback,
                        absoluteRate,
                        PumpTempBasalType.Absolute);
            } else {
                getAapsLogger().info(LTag.EVENTS, "bolusingbasal");
                getAapsLogger().info(LTag.EVENTS, "" + getBaseBasalRate());
                getAapsLogger().info(LTag.EVENTS, "" + absoluteRate);
                getAapsLogger().info(LTag.EVENTS, "" + getPumpDescription().bolusStep);
                clearTempBasal();
                result = scheduleTempBasalBolus(0, durationInMinutes, profile, callback,
                        absoluteRate, PumpTempBasalType.Absolute);
            }
//        result.success = false;
//        result.comment = MainApp.gs(R.string.pumperror);

        }
        getAapsLogger().info(LTag.EVENTS, "Settings temp basal percent: " + result);
        return result;

    }

    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
                                                boolean enforceNew) {

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);
            getAapsLogger().info(LTag.PUMP, "pump unreachable");
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

        getAapsLogger().info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute: rate: " + absoluteRate + ", duration=" + durationInMinutes);

        // read current TBR
        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            getAapsLogger().warn(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - Could not read current TBR, canceling operation.");
            finishAction("TBR");
            return new PumpEnactResult(getInjector()).success(false).enacted(false)
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_cant_read_tbr));
        } else {
            getAapsLogger().info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute: Current Basal: duration: " + tbrCurrent.getDurationMinutes() + " min, rate=" + tbrCurrent.getInsulinRate());
        }

        if (!enforceNew) {

            if (MedLinkMedtronicUtil.isSame(tbrCurrent.getInsulinRate(), absoluteRate)) {

                boolean sameRate = true;
                if (MedLinkMedtronicUtil.isSame(0.0d, absoluteRate) && durationInMinutes > 0) {
                    // if rate is 0.0 and duration>0 then the rate is not the same
                    sameRate = false;
                }

                if (sameRate) {
                    getAapsLogger().info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                    finishAction("TBR");
                    return new PumpEnactResult(getInjector()).success(true).enacted(false);
                }
            }
            // if not the same rate, we cancel and start new
        }

        // if TBR is running we will cancel it.
        if (tbrCurrent.getInsulinRate() != 0.0f && tbrCurrent.getDurationMinutes() > 0) {
            getAapsLogger().info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - TBR running - so canceling it.");

            // CANCEL

            cancelTempBasal(true, new Callback() {
                @Override public void run() {
                    getAapsLogger().info(LTag.PUMPBTCOMM, "tbr cancelled");
                }
            });

            ToastUtils.showToastInUiThread(context, resourceHelper.gs(info.nightscout.androidaps.plugins.pump.common.R.string.tempbasaldeliveryerror));
//            Boolean response = (Boolean) responseTask2.returnData;

//            if (response) {
//                getAapsLogger().info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - Current TBR cancelled.");
//            } else {
//                getAapsLogger().error(getLogPrefix() + "setTempBasalAbsolute - Cancel TBR failed.");
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
//        getAapsLogger().info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - setTBR. Response: " + response);
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

    protected PumpEnactResult clearTempBasal() {
        getAapsLogger().info(LTag.EVENTS, " clearing temp basal");
        PumpEnactResult result = buildPumpEnactResult();
        this.tempbasalMicrobolusOperations.clearOperations();
        result.success = true;
        result.comment = resourceHelper.gs(R.string.canceltemp);
        if (getPumpStatusData().getPumpStatusType().equals(PumpStatusType.Suspended)) {
            startPump(new Callback() {
                @Override public void run() {
                    getAapsLogger().info(LTag.EVENTS, "temp basal cleared");
                }
            });
        }
//        createTemporaryBasalData(0, 0);
        return result;
    }

    private LinkedBlockingDeque<TempBasalMicroBolusPair> buildSuspensionScheduler(Integer totalSuspendedMinutes,
//                                                                                  Integer suspensions,
                                                                                  Integer durationInMinutes,
                                                                                  Function1 callback) {
        double mod = 0d;
        int mergeOperations = 0;
        Double operationInterval = 0d;
        int possibleSuspensions = 0;
        Double operationDuration = 0d;
        double durationDouble = Double.valueOf(durationInMinutes) -
                Double.valueOf(totalSuspendedMinutes);
        if (durationDouble == 0d) {
            operationDuration = Double.valueOf(totalSuspendedMinutes);
            possibleSuspensions = 1;
        } else {
            possibleSuspensions = (int) Math.round(durationDouble / Constants.INTERVAL_BETWEEN_OPERATIONS);
            int neededSuspensions = (int) Math.round(Double.valueOf(totalSuspendedMinutes) /
                    Constants.INTERVAL_BETWEEN_OPERATIONS);
            if (neededSuspensions < possibleSuspensions) {
                possibleSuspensions = neededSuspensions;
            }
            operationInterval = durationDouble / possibleSuspensions;
            mod = operationInterval % Constants.INTERVAL_BETWEEN_OPERATIONS;
            if ((mod / Constants.INTERVAL_BETWEEN_OPERATIONS) > 0.5) {
                operationInterval += Constants.INTERVAL_BETWEEN_OPERATIONS - mod;
            } else {
                operationInterval -= mod;
            }
            operationDuration = Double.valueOf(totalSuspendedMinutes) / possibleSuspensions;
            if (operationDuration < Constants.INTERVAL_BETWEEN_OPERATIONS) {
                operationDuration = (double) Constants.INTERVAL_BETWEEN_OPERATIONS;
            }
            mod = operationDuration % Constants.INTERVAL_BETWEEN_OPERATIONS;
            if ((mod / Constants.INTERVAL_BETWEEN_OPERATIONS) > 0.5) {
                operationDuration += Constants.INTERVAL_BETWEEN_OPERATIONS - mod;
            } else {
                operationDuration -= mod;
            }

            if (totalSuspendedMinutes > operationDuration * possibleSuspensions) {
                int diff = totalSuspendedMinutes - operationDuration.intValue() * possibleSuspensions;
                mergeOperations = diff / Constants.INTERVAL_BETWEEN_OPERATIONS;
            }
            if ((operationDuration + operationInterval) * (possibleSuspensions) > Double.valueOf(durationInMinutes)) {
                while (operationInterval >= 2 * Constants.INTERVAL_BETWEEN_OPERATIONS &&
                        (operationDuration + operationInterval) * (possibleSuspensions) >
                                Double.valueOf(durationInMinutes)) {
                    operationInterval -= Constants.INTERVAL_BETWEEN_OPERATIONS;
                }

                while (mergeOperations * operationDuration + (operationDuration + operationInterval) *
                        (possibleSuspensions - mergeOperations) > durationInMinutes && possibleSuspensions > 1) {
                    mergeOperations++;
                }
                possibleSuspensions -= mergeOperations;
            }

//        operationDuration = Double.valueOf(totalSuspendedMinutes) / Double.valueOf(suspensions);
//        if (operationDuration < Constants.INTERVAL_BETWEEN_OPERATIONS) {
//            operationDuration = (double) Constants.INTERVAL_BETWEEN_OPERATIONS;
//        }
//        mod = operationDuration % Constants.INTERVAL_BETWEEN_OPERATIONS;
//        if ((mod / Constants.INTERVAL_BETWEEN_OPERATIONS) > 0.5) {
//            operationDuration += Constants.INTERVAL_BETWEEN_OPERATIONS - mod;
//        } else {
//            operationDuration -= mod;
//        }
        }
        NextStartStop nextStartStop = new NextStartStop();
        StartStopDateTime startStopDateTime = nextStartStop.getNextStartStop(operationDuration.intValue(), operationInterval.intValue());
        int totalSuspended = 0;
        LinkedBlockingDeque<TempBasalMicroBolusPair> operations = new LinkedBlockingDeque<TempBasalMicroBolusPair>();
        for (int oper = 0; oper < possibleSuspensions; oper += 1) {
            operations.add(new TempBasalMicroBolusPair(totalSuspendedMinutes, 0d,
                    0d, startStopDateTime.getStartOperationTime(),
                    TempBasalMicroBolusPair.OperationType.SUSPEND, callback));
            operations.add(new TempBasalMicroBolusPair(totalSuspendedMinutes, 0d,
                    0d, startStopDateTime.getEndOperationTime(),
                    TempBasalMicroBolusPair.OperationType.REACTIVATE, callback));
            if (mergeOperations > 0) {
                startStopDateTime = nextStartStop.getNextStartStop(operationDuration.intValue() * 2,
                        operationInterval.intValue());
                mergeOperations--;
                totalSuspended += operationDuration.intValue() * 2;
            } else {
                startStopDateTime = nextStartStop.getNextStartStop(operationDuration.intValue(),
                        operationInterval.intValue());
                totalSuspended += operationDuration.intValue();
            }

        }
        return mergeExtraOperations(operations, mod, possibleSuspensions);
    }

    private LinkedBlockingDeque<TempBasalMicroBolusPair> mergeExtraOperations(
            LinkedBlockingDeque<TempBasalMicroBolusPair> operations, double mod
            , int suspensions
    ) {
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
//                break;
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
                calcPercent = 1 - (absoluteBasalValue / profile.getBasal());
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
//            int suspensions;
//            if (totalSuspendedMinutes < durationInMinutes / 2) {
//                suspensions = new BigDecimal(totalSuspendedMinutes)
//                        .divide(new BigDecimal(Constants.INTERVAL_BETWEEN_OPERATIONS), RoundingMode.HALF_UP)
//                        .setScale(0, RoundingMode.HALF_UP).intValue();
//            } else {
////                int delta = durationInMinutes - ;
//                suspensions = totalSuspendedMinutes / Constants.INTERVAL_BETWEEN_OPERATIONS;
//                if (suspensions == 0) {
//                    suspensions++;
//                }
//            }

            //TODO need to reavaluate some cases, used floor here to avoid two possible up rounds
            LinkedBlockingDeque<TempBasalMicroBolusPair> operations = this.buildSuspensionScheduler(
                    totalSuspendedMinutes, durationInMinutes, callback);
            this.tempbasalMicrobolusOperations.updateOperations(operations.size(), 0d,
                    operations,
                    totalSuspendedMinutes);
            this.tempbasalMicrobolusOperations.setAbsoluteRate(absoluteBasalValue);
        }
        refreshAnyStatusThatNeedsToBeRefreshed();
        //criar fila de comandos aqui, esta fila deverá ser consumida a cada execução de checagem de status
        return buildPumpEnactResult().success(true).comment(getResourceHelper().gs(R.string.medtronic_cmd_desc_set_tbr));
    }

    private int calculateTotalSuspended(double calcPercent) {
        double suspended = durationInMinutes * calcPercent;
        return new BigDecimal(Double.toString(suspended)).setScale(0, RoundingMode.HALF_UP).intValue();
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


        Double roundedTotalAmount = new BigDecimal(totalAmount).setScale(1,
                RoundingMode.HALF_UP).doubleValue();
        TempBasalMicrobolusOperations operations;
        if (roundedTotalAmount.doubleValue() == getPumpType().getBolusSize()) {
            LinkedBlockingDeque<TempBasalMicroBolusPair> tempBasalList = new LinkedBlockingDeque<>();
            tempBasalList.add(new TempBasalMicroBolusPair(0, totalAmount, totalAmount,
                    getCurrentTime().plusMinutes(durationInMinutes / 2),
                    TempBasalMicroBolusPair.OperationType.BOLUS, callback));
            operations = new TempBasalMicrobolusOperations(1, totalAmount,
                    durationInMinutes,
                    tempBasalList);
        } else if (roundedTotalAmount.doubleValue() < getPumpType().getBolusSize()) {
            cancelTempBasal(true);
            operations = new TempBasalMicrobolusOperations(0, 0, 0,
                    new LinkedBlockingDeque<>());
        } else {
            operations = buildTempBasalSMBOperations(roundedTotalAmount, insulinPeriod, callback,
                    durationInMinutes, absoluteBasalValue);
        }

        if (basalType.equals(PumpTempBasalType.Percent)) {
            operations.setAbsoluteRate(totalAmount);
        }

        return operations;
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
                delta = Constants.SECONDS_PER_DAY - startedTime;
            }
        } else {
            delta = basalProfilesFromTemp.peekFirst().timeAsSeconds - startedTime;
        }
        double profileDosage = 0d;
        if (basalType.equals(PumpTempBasalType.Percent)) {
            profileDosage = calcTotalAmountPct(percent, currentProfileValue.value, delta / 60);
        } else {
            profileDosage = calcTotalAmountAbs(abs, currentProfileValue.value, delta / 60);
        }
        return createTempBasalPair(profileDosage, delta, currentProfileValue.timeAsSeconds, basalType);

    }

    private TempBasalMicroBolusDTO createTempBasalPair(Double totalAmount, Integer durationInSeconds,
                                                       Integer startTime, PumpTempBasalType basalType) {
        return new TempBasalMicroBolusDTO(totalAmount,
                basalType.equals(PumpTempBasalType.Percent), durationInSeconds / 60,
                startTime, startTime + durationInSeconds);
    }

    private Double calcTotalAmountPct(int tempBasal, double currentBasalValue,
                                      int durationInMinutes) {
        if (tempBasal > 100) {
            tempBasal -= 100;
        }
        return ((tempBasal / 100d) * currentBasalValue) * (durationInMinutes / 60d);
    }

    private Double calcTotalAmountAbs(double tempBasal, double currentBasalValue,
                                      int durationInMinutes) {
        return (tempBasal - currentBasalValue) * (durationInMinutes / 60d);
    }

    private Double calcTotalAmount(Integer percent, double value, int durationInMinutes) {
        return (percent.doubleValue() / 100 - 1) * value * (durationInMinutes / 60d);
    }

    public LocalDateTime getCurrentTime() {
        return LocalDateTime.now();
    }

    @NotNull private TempBasalMicrobolusOperations buildTempBasalSMBOperations(Double totalAmount,
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
            getAapsLogger().info(LTag.PUMPBTCOMM, "" + time);
            getAapsLogger().info(LTag.PUMPBTCOMM, "" + periodAvailableOperations);
            double minBolusDose = getPumpType().getBolusSize();
            if (roundedPeriodDose >= minBolusDose) {
                int doses = new BigDecimal(
                        roundedPeriodDose / minBolusDose
                ).setScale(0, RoundingMode.HALF_DOWN).intValue();
                Double calculatedDose = new BigDecimal(periodDose).divide(new BigDecimal(doses), 2, RoundingMode.HALF_DOWN).doubleValue();
                minDosage = new BigDecimal(periodDose / doses).setScale(1, RoundingMode.HALF_DOWN).doubleValue();
                List<Double> list = buildOperations(doses, periodAvailableOperations, Collections.emptyList());
                //TODO convert build operations to return list of tempmicroboluspair
                for (double dose : list) {
                    if (dose > 0) {
                        TempBasalMicroBolusPair pair = new TempBasalMicroBolusPair(0,
                                dose, calculatedDose, operationTime,
                                TempBasalMicroBolusPair.OperationType.BOLUS, callback);
                        getAapsLogger().info(LTag.EVENTS, pair.toString());
                        result.getOperations().add(pair);
                    }
                    if (getCurrentTime().isAfter(operationTime)) {
                        Long refresh = statusRefreshMap.get(PumpHistory);
                        if (refresh > getCurrentTime().plusMinutes(5).toDateTime().getMillis()) {
                            operationTime = operationTime.plusMinutes(Constants.INTERVAL_BETWEEN_OPERATIONS);
                        } else {
                            Instant instant = Instant.ofEpochMilli(refresh);
                            operationTime = instant.toDateTime().withZone(DateTimeZone.getDefault()).toLocalDateTime();
                        }
                    } else {
                        operationTime = operationTime.plusMinutes(Constants.INTERVAL_BETWEEN_OPERATIONS);
                    }
                }
            }
        }
        Double totalDose = result.getOperations().stream().map(TempBasalMicroBolusPair::getDose).reduce(0d, Double::sum);
        double doseDiff = totalDose - totalAmount;
        if (totalDose.compareTo(totalAmount) > 0 && Math.abs(doseDiff - minDosage) < 1E-2) {
            result.setOperations(excludeExtraDose(totalDose, totalAmount, result));
            getAapsLogger().info(LTag.AUTOMATION, "Error in temp basal microbolus calculation, totalDose: " + totalDose + ", totalAmount " + totalAmount + ", profiles " + insulinPeriod + ", absoluteRate: " + absoluteRate + ", insulin period" + insulinPeriod);
        } else if (totalAmount.compareTo(totalDose) > 0 && Math.abs(doseDiff) >= minDosage) {
            //TODO need a test to verify if this is reacheable
            getAapsLogger().info(LTag.AUTOMATION, "Error in temp basal microbolus calculation, totalDose: " + totalDose + ", totalAmount " + totalAmount + ", profiles " + insulinPeriod + ", absoluteRate: " + absoluteRate + ", insulin period" + insulinPeriod);
//            throw new RuntimeException("Error in temp basal microbolus calculation, totalDose: " + totalDose + ", totalAmount " + totalAmount + ", profiles " + insulinPeriod);
        }
        return result;
    }

    private LinkedBlockingDeque<TempBasalMicroBolusPair> excludeExtraDose(Double totalDose,
                                                                          Double totalAmount,
                                                                          TempBasalMicrobolusOperations result) {
        int dosesToDecrease = (int) (Math.round((totalDose - totalAmount) / (getPumpType().getBolusSize())));
        final Double maxDosage = result.getOperations().stream().map(
                TempBasalMicroBolusPair::getDose).max(Double::compareTo).orElse(0d);
        final Double minDosage = result.getOperations().stream().map(
                TempBasalMicroBolusPair::getDose).min(Double::compareTo).orElse(0d);
        LinkedBlockingDeque<TempBasalMicroBolusPair> operations = new LinkedBlockingDeque<>();
        if (maxDosage.equals(minDosage)) {
            Stream<TempBasalMicroBolusPair> sortedOperations = result.getOperations().stream().sorted((prev, curr) -> prev.getDelta().compareTo(curr.getDelta()));
            operations = sortedOperations.skip(dosesToDecrease).sorted((prev, curr) ->
                    prev.getReleaseTime().compareTo(curr.getReleaseTime())).
                    collect(Collectors.toCollection(LinkedBlockingDeque::new));
        } else {

            while (!result.getOperations().isEmpty()) {
                TempBasalMicroBolusPair tmp = result.getOperations().pollFirst();
                if (tmp.getDose().equals(maxDosage) && dosesToDecrease > 0) {
                    dosesToDecrease -= 1;
                    if (tmp.getDose().compareTo(getPumpType().getBolusSize()) > 0) {
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
        if (doses == 0 || operations == 0) {
            return list;
        } else if (doses > operations) {
            return buildOperationsList(doses, operations, list.get(0) + getPumpType().getBolusSize());
        } else {
            double step = (double) operations / doses;
            if (doses == 1) {
                int position = Math.floorDiv(operations, 2) - 1;
                if (position < 0) {
                    position = 0;
                }
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
            aapsLogger.info(LTag.PUMPBTCOMM, "unrecheable?");
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
        getAapsLogger().debug(LTag.PUMP, "setTempBasalPercent [PumpPluginAbstract] - Not implemented.");
        return new PumpEnactResult(getInjector()).success(false).enacted(false).
                comment(getResourceHelper().gs(
                        info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver));
    }

    @NotNull @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes,
                                               Profile profile, boolean enforceNew, Function1 callback) {

        PumpEnactResult result;
        tempbasalMicrobolusOperations.getOperations().clear();
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
        getAapsLogger().info(LTag.DATATREATMENTS, "Settings temp basal percent: " + result);
        return result;
    }

    @NotNull @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = buildPumpEnactResult();
        result.success = false;
        medtronicUtil.sendNotification(MedtronicNotificationType.PumpExtendedBolusNotEnabled, getResourceHelper(), rxBus);
        getAapsLogger().debug("Setting extended bolus: " + result);
        return result;
    }


    @NotNull @Override
    public void cancelTempBasal(Boolean enforceNew, Callback callback) {
        getAapsLogger().info(LTag.EVENTS, "canceling temp basal");
        if (getPumpStatusData().pumpStatusType.equals(PumpStatusType.Suspended)) {
            startPump(callback);
        }
        this.tempbasalMicrobolusOperations.clearOperations();

        getAapsLogger().debug("Cancel temp basal: " + result);

    }

    public PumpEnactResult extendBasalTreatment(int duration, Function1 callback) {
//TODO implement
        PumpEnactResult result = new PumpEnactResult(getInjector()).success(true).enacted(true).
                comment(getResourceHelper().gs(R.string.let_temp_basal_run));
        Optional<TempBasalMicroBolusPair> reactivateOper =
                tempbasalMicrobolusOperations.getOperations().stream().filter(f ->
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
        getAapsLogger().debug("Canceling extended bolus: " + result);
        return result;
    }

    @NotNull @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.Medtronic;
    }

    @NotNull @Override
    public PumpType model() {
        return pumpDescription.pumpType;
    }

    @Override public PumpType getPumpType() {
        return pumpDescription.pumpType;
    }

    @NotNull @Override
    public String serialNumber() {
        return medLinkPumpStatus.serialNumber;
    }

    @NotNull @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @NonNull @Override
    public String shortStatus(boolean veryShort) {
        String ret = "";
        if (getPumpStatusData().lastConnection != 0) {
            long agoMsec = System.currentTimeMillis() - getPumpStatusData().lastConnection;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " min ago\n";
        }
        if (getPumpStatusData().lastBolusTime != null && getPumpStatusData().lastBolusTime.getTime() != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(getPumpStatusData().lastBolusAmount) + "U @" + //
                    android.text.format.DateFormat.format("HH:mm", getPumpStatusData().lastBolusTime) + "\n";
        }
        TemporaryBasal activeTemp = activePlugin.getActiveTreatments().getRealTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            ret += "Temp: " + activeTemp.toStringFull() + "\n";
        }
        ExtendedBolus activeExtendedBolus = activePlugin.getActiveTreatments().getExtendedBolusFromHistory(
                System.currentTimeMillis());
        if (activeExtendedBolus != null) {
            ret += "Extended: " + activeExtendedBolus.toString() + "\n";
        }
        // if (!veryShort) {
        // ret += "TDD: " + DecimalFormatter.to0Decimal(pumpStatus.dailyTotalUnits) + " / "
        // + pumpStatus.maxDailyTotalUnits + " U\n";
        // }
        ret += "IOB: " + getPumpStatusData().iob + "U\n";
        ret += "Reserv: " + DecimalFormatter.to0Decimal(getPumpStatusData().reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + getPumpStatusData().batteryVoltage + "\n";
        return ret;
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
                    getAapsLogger().debug("Medronic Pump plugin intent");
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
        getAapsLogger().info(LTag.PUMPBTCOMM, "valid profile " + validProfile);
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
//            getAapsLogger().info(LTag.PUMPBTCOMM,profile.toString());
            getAapsLogger().info(LTag.PUMPBTCOMM, this.profile.toString());
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
        getAapsLogger().info(LTag.PUMP, getLogPrefix() + "setNewBasalProfile");

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
            getAapsLogger().info(LTag.PUMP, "pump unreachable");
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
//        getAapsLogger().info(LTag.PUMP, getLogPrefix() + "Basal Profile was set: " + response);
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
            getAapsLogger().info(LTag.PUMP, "pump unreachable");
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
        getAapsLogger().info(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - " + BolusDeliveryType.DeliveryPrepared);

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
            getAapsLogger().debug(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - Pump Unreachable.");
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

            BolusCallback bolusCallback = new BolusCallback(aapsLogger, this);
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
                    getAapsLogger().info(LTag.PUMPBTCOMM, "pump is delivering");
                    response.set(true);
                    bolusInProgress(detailedBolusInfo, bolusDeliveryTime);
//                    processDeliveredBolus(answer, detailedBolusInfo);
                } else if (answer.getResponse().equals(PumpResponses.UnknownAnswer)) {
                    getAapsLogger().info(LTag.PUMPBTCOMM, "need to check bolus");
//                    processDeliveredBolus(answer, detailedBolusInfo);
                    checkBolusAtNextStatus = true;
                }
//                answer.get().forEach(x -> getAapsLogger().info(LTag.PUMPBTCOMM, x));
//                bolusDeliveryTime = answer.get().findFirst().map(bolTime -> Long.valueOf(bolTime)).
//                        orElse(0l);
//                getAapsLogger().info(LTag.PUMPBTCOMM, answer.get().collect(Collectors.joining()));
//                if (answer.get().anyMatch(ans -> ans.trim().equals("pump is delivering a bolus"))) {
//                    getAapsLogger().info(LTag.PUMPBTCOMM, "pump is deliverying");
//                    response.set(true);
//                    bolusInProgress(detailedBolusInfo, bolusDeliveryTime);
//                    processDeliveredBolus(answer, detailedBolusInfo);
//                } else if (answer.get().anyMatch(ans -> ans.trim().equals("pump is not delivering a bolus"))) {
//                    getAapsLogger().info(LTag.PUMPBTCOMM, detailedBolusInfo.toString());
//                    getAapsLogger().info(LTag.PUMPBTCOMM, f.getFunctionResult());
//                    if (detailedBolusInfo.insulin < 0.5 && f.getFunctionResult().equals(
//                            PumpResponses.BolusDelivered.getAnswer())) {
//                        processDeliveredBolus(answer, detailedBolusInfo);
//                    } else {
//                        getAapsLogger().info(LTag.PUMPBTCOMM, "pump is not deliverying");
//                        processDeliveredBolus(answer, detailedBolusInfo);
//                        Intent i = new Intent(context, ErrorHelperActivity.class);
//                        i.putExtra("soundid", R.raw.boluserror);
//                        i.putExtra("status", result.comment);
//                        i.putExtra("title", resourceHelper.gs(R.string.medtronic_cmd_bolus_could_not_be_delivered));
//                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                        context.startActivity(i);
//                    }
//                } else {
//                    getAapsLogger().info(LTag.PUMPBTCOMM, "and themmmm");
//                }

                return new MedLinkStandardReturn<String>(() -> f.getAnswer(), f.getFunctionResult().getAnswer());
            });
            MedLinkCommandType bolusCommand = detailedBolusInfo.isSMB ? MedLinkCommandType.SMBBolus : MedLinkCommandType.Bolus;
            BolusMedLinkMessage msg = new BolusMedLinkMessage(bolusCommand,
                    detailedBolusInfo.insulin,
                    andThem, new BolusProgressCallback(
                    medLinkPumpStatus,
                    resourceHelper,
                    rxBus,
                    null,
                    aapsLogger),
                    new BleBolusCommand(aapsLogger, getMedLinkService().getMedLinkServiceData())
            );


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
        getAapsLogger().info(LTag.EVENTS, "bolus in progress");
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
        long bolusEndTime = actionTime + (bolusTime * 1000L);
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
        getAapsLogger().info(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - " + BolusDeliveryType.DeliveryPrepared);

        setRefreshButtonEnabled(false);

        if (detailedBolusInfo.insulin > medLinkPumpStatus.reservoirRemainingUnits) {
            func.invoke(new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_bolus_could_not_be_delivered_no_insulin,
                            medLinkPumpStatus.reservoirRemainingUnits,
                            detailedBolusInfo.insulin)));
            return;
        }

        bolusDeliveryType = BolusDeliveryType.DeliveryPrepared;

        if (isPumpNotReachable()) {
            getAapsLogger().debug(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - Pump Unreachable.");
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


            if (lastDetailedBolusInfo != null) {
                readBolusData(detailedBolusInfo);
            }
            DetailedBolusInfo bolus = detailedBolusInfo.copy();
            BolusCallback bolusCallback = new BolusCallback(aapsLogger, this, detailedBolusInfo);
            Function<Supplier<Stream<String>>, MedLinkStandardReturn<String>> andThen = bolusCallback.andThen(f -> {
                Supplier<Stream<String>> answer = f::getAnswer;
//                answer.get().forEach(x -> getAapsLogger().info(LTag.PUMPBTCOMM, x));
                getAapsLogger().info(LTag.PUMPBTCOMM, f.getFunctionResult().getResponse().name());
                if (PumpResponses.BolusDelivered.equals(f.getFunctionResult().getResponse())) {

                    processDeliveredBolus(f.getFunctionResult(), bolus);
                    bolusInProgress(detailedBolusInfo, bolusDeliveryTime);
                    func.invoke(new PumpEnactResult(getInjector()).success(true) //
                            .enacted(true) //
                            .bolusDelivered(bolus.insulin) //
                            .carbsDelivered(bolus.carbs));
                } else if (PumpResponses.UnknownAnswer.equals(f.getFunctionResult().getResponse())) {
                    BolusAnswer bolusAnswer = f.getFunctionResult();
                    if (detailedBolusInfo.insulin == bolusAnswer.getBolusAmount() &&
                            bolusAnswer.getBolusDeliveryTime().toInstant().toEpochMilli() >
                                    lastBolusTime && bolusAnswer.getBolusDeliveryTime().toInstant().toEpochMilli() -
                            lastBolusTime <= 180000) {
                        detailedBolusInfo.deliverAt = bolusAnswer.getBolusDeliveryTime().toInstant().toEpochMilli();
                        handleNewTreatmentData(Stream.of(detailedBolusInfo));
                    } else {
                        //TODO postpone this message to later,  call status logic before to guarantee that the bolus has not been delivered
                        getAapsLogger().info(LTag.PUMPBTCOMM, "pump is not deliverying");
                        if (f.getFunctionResult().getBolusDeliveryTime() != null) {
                            processDeliveredBolus(f.getFunctionResult(), detailedBolusInfo);
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
                        } else {
                            readBolusData(detailedBolusInfo);
                        }
                    }
                } else if (PumpResponses.DeliveringBolus.equals(f.getFunctionResult().getResponse())) {
                    lastDetailedBolusInfo = detailedBolusInfo;
                    lastBolusTime = System.currentTimeMillis();
                    readBolusData(lastDetailedBolusInfo);
                    getAapsLogger().info(LTag.PUMPBTCOMM, "and themmmm");
                }
                Supplier<Stream<String>> recentBolus = () -> answer.get().filter(ans -> ans.contains("recent bolus"));
                if (recentBolus.get().findFirst().isPresent()) {
                    String result = recentBolus.get().findFirst().get();
                    getAapsLogger().info(LTag.PUMPBTCOMM, result);
                    Pattern pattern = Pattern.compile("\\d{1,2}\\.\\d{1,2}");
                    Matcher matcher = pattern.matcher(result);
                    if (matcher.find()) {
                        detailedBolusInfo.insulin = Double.parseDouble(matcher.group(0));
                    }
                }
                return new MedLinkStandardReturn<String>(f::getAnswer, f.getFunctionResult().getAnswer());
            });
            boolean stopAfter = false;
            if (getPumpStatusData().getPumpStatusType().equals(PumpStatusType.Suspended) &&
                    detailedBolusInfo.isSMB) {
                startPump(new Callback() {
                    @Override public void run() {
                        getAapsLogger().info(LTag.EVENTS, "starting pump for bolus");
                    }
                });
                stopAfter = true;
            }
            MedLinkCommandType bolusCommand = detailedBolusInfo.isSMB ? MedLinkCommandType.SMBBolus : MedLinkCommandType.Bolus;
            BolusMedLinkMessage msg = new BolusMedLinkMessage(bolusCommand, detailedBolusInfo.insulin,
                    andThen, new BolusProgressCallback(medLinkPumpStatus,
                    resourceHelper,
                    rxBus, null,
                    aapsLogger),
                    new BleBolusCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));


            medLinkService.getMedtronicUIComm().executeCommandCP(msg);

            if (stopAfter) {
                stopPump(new Callback() {
                    @Override public void run() {
                        getAapsLogger().info(LTag.EVENTS, "stopping pump after bolus");
                    }
                });
            }
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

    private void readBolusData(DetailedBolusInfo detailedBolusInfo) {
        getAapsLogger().info(LTag.PUMPBTCOMM, "get full bolus data");
        lastBolusHistoryRead = System.currentTimeMillis();
        BolusDeliverCallback func =
                new BolusDeliverCallback(getPumpStatusData(), this, aapsLogger,
                        detailedBolusInfo);
        MedLinkPumpMessage msg = new BolusStatusMedLinkMessage(MedLinkCommandType.BolusStatus,
                func,
                getBtSleepTime(),
                new BleCommand(aapsLogger, getMedLinkService().getMedLinkServiceData()));
        medLinkService.getMedtronicUIComm().executeCommandCP(msg);
    }

    @Override protected void triggerUIChange() {
        rxBus.send(new EventMedtronicPumpValuesChanged());
    }

    @Override
    public void timezoneOrDSTChanged(TimeChangeType changeType) {
        getAapsLogger().warn(LTag.PUMP, getLogPrefix() + "Time or TimeZone changed. ");
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
                getAapsLogger().info(LTag.BGSOURCE, "User bg source");
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
                    getAapsLogger().info(LTag.BGSOURCE, "User bg source");
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
            if (this.lastDetailedBolusInfo != null &&
                    Math.abs(bolusInfo.date - this.lastDetailedBolusInfo.date) < 220000l &&
                    bolusInfo.insulin == this.lastDetailedBolusInfo.insulin &&
                    this.lastDetailedBolusInfo.carbs != 0d) {
                bolusInfo.carbs = this.lastDetailedBolusInfo.carbs;
                this.lastDetailedBolusInfo = null;
            }
            activePlugin.getActiveTreatments().addToHistoryTreatment(bolusInfo, false);
//            if (bolusInfo.deliverAt > lastBolusTime) {
            lastBolusTime = bolusInfo.deliverAt;
//            }
        });
    }

    public void handleNewEvent() {
        getAapsLogger().info(LTag.EVENTS, " new event ");
        getAapsLogger().info(LTag.EVENTS, "" + isInitialized);
        getAapsLogger().info(LTag.EVENTS, "" + lastBolusTime);
        getAapsLogger().info(LTag.EVENTS, "" + medLinkPumpStatus.lastBolusTime.getTime());
        getAapsLogger().info(LTag.EVENTS, "" + pumpTimeDelta);
        if (isInitialized) {
            if (lastBolusTime != medLinkPumpStatus.lastBolusTime.getTime() &&
                    lastDeliveredBolus == medLinkPumpStatus.lastBolusAmount
                    &&
                    Math.abs(lastBolusTime - medLinkPumpStatus.lastBolusTime.getTime()) >
                            pumpTimeDelta + 60000
            ) {
                readBolusHistory();
            } else if (lastBolusTime > 0 && lastDetailedBolusInfo != null) {
                lastBolusTime = lastDetailedBolusInfo.deliverAt;
                if (sp.getBoolean(R.string.medlink_key_force_bolus_history_read, false) ||
                        getPumpStatusData().lastBolusAmount != lastDetailedBolusInfo.insulin) {
                    readBolusHistory();
                } else {
                    lastDetailedBolusInfo.deliverAt = getPumpStatusData().lastBolusTime.getTime();
                    lastDetailedBolusInfo.date = getPumpStatusData().lastBolusTime.getTime();
                    activePlugin.getActiveTreatments().addToHistoryTreatment(lastDetailedBolusInfo, true);
                    lastDetailedBolusInfo = null;
                }


            } else if (lastBolusTime < getPumpStatusData().lastBolusTime.getTime()) {
                readBolusHistory();
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
        if (sensorDataReadings.length == 1) {

            PumpHistoryEntry searchEntry = new PumpHistoryEntry();
            searchEntry.setEntryType(MedtronicDeviceType.Medtronic_515, PumpHistoryEntryType.BGReceived);
            searchEntry.setDisplayableValue(sensorDataReadings[0].toString());
            PumpHistoryResult historyResult = new PumpHistoryResult(aapsLogger, searchEntry, sensorDataReadings[0].date);
            medtronicHistoryData.addNewHistory(historyResult);
            if (medLinkPumpStatus.needToGetBGHistory() && isInitialized()) {
                missedBGs++;
                if (firstMissedBGTimestamp == 0l) {
                    firstMissedBGTimestamp = lastBGHistoryRead;
                }
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
        if (sensorDataReadings.length > 0 && sensorDataReadings[0].value != 0d) {
            getAapsLogger().info(LTag.PUMPBTCOMM, "pump bg history");
            readPumpBGHistory(false);
        }
//        } else {
//            bgFailedToRead = 0;
//        }
    }


    public void handleNewSensorData(SensorDataReading... sens) {
        if (sens.length == 1 && lastBolusTime == 0) {
            lastBolusTime = getPumpStatusData().lastBolusTime.getTime();
        }
        handleNewPumpData();
        if (sens.length == 1 &&
                medLinkPumpStatus.needToGetBGHistory() && isInitialized()) {
            missedBGs++;
            if (firstMissedBGTimestamp == 0l && isInitialized()) {
                firstMissedBGTimestamp = lastBGHistoryRead;
            }
        } else if (sens.length > 1) {
            getAapsLogger().info(LTag.PUMPBTCOMM, "" + isInitialized);
            getAapsLogger().info(LTag.PUMPBTCOMM, "" + isInitialized());
            if (isInitialized() && sens[0].bgReading.date != getPumpStatusData().lastDateTime && sens[0].date > firstMissedBGTimestamp && System.currentTimeMillis() - lastPreviousHistory > 500000) {
                getPreviousBGHistory();
                lastPreviousHistory = System.currentTimeMillis();
            }
            missedBGs = 0;
            firstMissedBGTimestamp = 0l;
        }
        Intent intent = buildIntentSensValues(sens);
        activePlugin.getActiveBgSource().handleNewData(intent);
        if (sens[0].bgValue != 0d) {
            readPumpBGHistory(false);
            late1Min = false;

        } else {
            late1Min = true;
        }
    }

    public void handleNewPumpData() {
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);
        handleBatteryData();
//        if(sens.length>0) {
        handleBolusDelivered(lastDetailedBolusInfo);
//        }
        handleProfile();
        handleBolusAlarm();
        if (getTemporaryBasal() != null && getTemporaryBasal().absoluteRate == 0 &&
                getTemporaryBasal().durationInMinutes > 0 &&
                getPumpStatusData().getPumpStatusType().equals(PumpStatusType.Running)) {
            stopPump(new Callback() {
                @Override public void run() {
                    getAapsLogger().info(LTag.PUMP, "Stopping unstopped pump");
                }
            });
        }
        handleNewEvent();
    }

    private void handleProfile() {
        if (lastProfileRead > 0 && System.currentTimeMillis() - lastProfileRead > 300000 &&
                getBasalProfile() != null && getBasalProfile().getEntryForTime(Instant.now()).rate != medLinkPumpStatus.getCurrentBasal()) {
            readPumpProfile();

        }
    }

    private void handleBolusAlarm() {
        if (!medLinkPumpStatus.bgAlarmOn) {
            isClosedLoopAllowed(new Constraint<>(false));
        } else {
            isClosedLoopAllowed(new Constraint<>(false));
        }


    }

    public void handleBolusDelivered(DetailedBolusInfo lastBolusInfo) {
        if (checkBolusAtNextStatus) {
            if (lastBolusInfo != null &&
                    lastBolusInfo.insulin == medLinkPumpStatus.lastBolusAmount &&
                    medLinkPumpStatus.lastBolusTime.getTime() > lastBolusTime) {
                lastBolusTime = medLinkPumpStatus.lastBolusTime.getTime();
                lastBolusInfo.deliverAt = lastBolusTime;
                lastBolusInfo.date = lastBolusTime;
                lastDeliveredBolus = lastBolusInfo.insulin;
                checkBolusAtNextStatus = false;
                handleNewTreatmentData(Stream.of(lastBolusInfo));
            } else {
                Intent i = new Intent(context, ErrorHelperActivity.class);
                i.putExtra("soundid", R.raw.boluserror);
                i.putExtra("status", result.comment);
                i.putExtra("title", resourceHelper.gs(R.string.medtronic_cmd_bolus_could_not_be_delivered));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
    }

    private void handleBatteryData() {
        long currentBatRead = medLinkPumpStatus.lastDateTime;
        int currentLevel = receiverStatusStore.getBatteryLevel();
        if (lastBatteryLevel == 0) {
            lastBatteryLevel = currentLevel;
        }
        if ((currentLevel - batteryDelta * 5 <= minimumBatteryLevel ||
                medLinkPumpStatus.batteryRemaining <= minimumBatteryLevel) &&
                medLinkPumpStatus.pumpStatusType == PumpStatusType.Suspended) {
            clearTempBasal();
        }
        if (batteryLastRead == 0l) {
            batteryLastRead = currentBatRead;
        } else {
            long batDelta = (currentBatRead - batteryLastRead) / 60000;
            double delta = 0d;
            if (batDelta > 0L) {
                delta = (currentLevel - lastBatteryLevel) / (batDelta);
            }
            if (delta > batteryDelta) {
                batteryDelta = delta;
            }
        }
    }


    @Override public MedLinkTemporaryBasal getTemporaryBasal() {
        if (!tempbasalMicrobolusOperations.getOperations().isEmpty()) {
            MedLinkTemporaryBasal tempBasal = new MedLinkTemporaryBasal(getInjector());
            tempBasal.date(tempbasalMicrobolusOperations.getOperations().getFirst().getReleaseTime().
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

    @org.jetbrains.annotations.Nullable @Override public String nextScheduledCommand() {
        Stream<Map.Entry<MedLinkMedtronicStatusRefreshType, Long>> nextCommandStream =
                statusRefreshMap.entrySet().stream().sorted((f1, f2) ->
                        f1.getValue().compareTo(f2.getValue()));
        Optional<Map.Entry<MedLinkMedtronicStatusRefreshType, Long>> nextCommand =
                nextCommandStream.findFirst();
        String result = "";
        if (nextCommand.isPresent()) {
            MedLinkMedtronicStatusRefreshType key = nextCommand.get().getKey();
            MedLinkMedtronicCommandType commandType = key.getCommandType(medtronicUtil.getMedtronicPumpModel());
            if (commandType != null) {
                result = commandType.commandDescription;
                result = result + " " + new LocalDateTime(nextCommand.get().getValue()).toString("HH:mm");
            } else if (key.name() != null) {
                result = key.name();
                result = result + " " + new LocalDateTime(nextCommand.get().getValue()).toString("HH:mm");
            }
        }
        return result;
    }

    @Override public String getBatteryInfoConfig() {
        return sp.getString(resourceHelper.gs(R.string
                .key_medlink_battery_info), "Age");
    }

    @NonNull @Override
    public JSONObject getJSONStatus(Profile profile, String profileName, String version) {
        JSONObject result = super.getJSONStatus(profile, profileName, version);
        JSONObject battery = new JSONObject();
        try {
            if (sp.getString(R.string
                    .key_medlink_battery_info, "Age") != "Age") {
                battery.put("voltage", getPumpStatusData().batteryVoltage);
            } else {
                BatteryStatusDTO dto = new BatteryStatusDTO();
                dto.voltage = getPumpStatusData().batteryVoltage;
                dto.batteryStatusType = BatteryStatusDTO.BatteryStatusType.Unknown;
                BatteryType type = BatteryType.valueOf(sp.getString(R.string.key_medtronic_battery_type, BatteryType.None.name()));
                getPumpStatusData().batteryRemaining = dto.getCalculatedPercent(type);
                battery.put("percent", getPumpStatusData().batteryRemaining);
            }
            result.put("battery", battery);
            result.put("clock", DateUtil.toISOString(getPumpStatusData().lastDateTime));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }
}
