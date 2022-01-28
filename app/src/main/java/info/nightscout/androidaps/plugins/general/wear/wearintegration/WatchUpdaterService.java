package info.nightscout.androidaps.plugins.general.wear.wearintegration;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.database.AppRepository;
import info.nightscout.androidaps.database.entities.Bolus;
import info.nightscout.androidaps.database.entities.GlucoseValue;
import info.nightscout.androidaps.database.entities.TemporaryBasal;
import info.nightscout.androidaps.extensions.GlucoseValueExtensionKt;
import info.nightscout.androidaps.extensions.TemporaryBasalExtensionKt;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.interfaces.Config;
import info.nightscout.androidaps.interfaces.GlucoseUnit;
import info.nightscout.androidaps.interfaces.IobCobCalculator;
import info.nightscout.androidaps.interfaces.Loop;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.Profile;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.GlucoseValueDataPoint;
import info.nightscout.androidaps.plugins.general.wear.WearPlugin;
import info.nightscout.androidaps.plugins.general.wear.events.EventWearConfirmAction;
import info.nightscout.androidaps.plugins.general.wear.events.EventWearInitiateAction;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider;
import info.nightscout.androidaps.receivers.ReceiverStatusStore;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.TrendCalculator;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.shared.sharedPreferences.SP;

public class WatchUpdaterService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    @Inject public GlucoseStatusProvider glucoseStatusProvider;
    @Inject public AAPSLogger aapsLogger;
    @Inject public WearPlugin wearPlugin;
    @Inject public ResourceHelper rh;
    @Inject public SP sp;
    @Inject public RxBus rxBus;
    @Inject public ProfileFunction profileFunction;
    @Inject public DefaultValueHelper defaultValueHelper;
    @Inject public NSDeviceStatus nsDeviceStatus;
    @Inject public ActivePlugin activePlugin;
    @Inject public Loop loop;
    @Inject public IobCobCalculator iobCobCalculator;
    @Inject public AppRepository repository;
    @Inject ReceiverStatusStore receiverStatusStore;
    @Inject Config config;
    @Inject public TrendCalculator trendCalculator;

    public static final String ACTION_RESEND = WatchUpdaterService.class.getName().concat(".Resend");
    public static final String ACTION_OPEN_SETTINGS = WatchUpdaterService.class.getName().concat(".OpenSettings");
    public static final String ACTION_SEND_STATUS = WatchUpdaterService.class.getName().concat(".SendStatus");
    public static final String ACTION_SEND_BASALS = WatchUpdaterService.class.getName().concat(".SendBasals");
    public static final String ACTION_SEND_BOLUSPROGRESS = WatchUpdaterService.class.getName().concat(".BolusProgress");
    public static final String ACTION_SEND_ACTIONCONFIRMATIONREQUEST = WatchUpdaterService.class.getName().concat(".ActionConfirmationRequest");
    public static final String ACTION_SEND_CHANGECONFIRMATIONREQUEST = WatchUpdaterService.class.getName().concat(".ChangeConfirmationRequest");
    public static final String ACTION_CANCEL_NOTIFICATION = WatchUpdaterService.class.getName().concat(".CancelNotification");

    private GoogleApiClient googleApiClient;
    public static final String WEARABLE_DATA_PATH = "/nightscout_watch_data";
    public static final String WEARABLE_RESEND_PATH = "/nightscout_watch_data_resend";
    private static final String WEARABLE_CANCELBOLUS_PATH = "/nightscout_watch_cancel_bolus";
    public static final String WEARABLE_CONFIRM_ACTIONSTRING_PATH = "/nightscout_watch_confirmactionstring";
    public static final String WEARABLE_INITIATE_ACTIONSTRING_PATH = "/nightscout_watch_initiateactionstring";

    private static final String OPEN_SETTINGS_PATH = "/openwearsettings";
    private static final String NEW_STATUS_PATH = "/sendstatustowear";
    private static final String NEW_PREFERENCES_PATH = "/sendpreferencestowear";
    public static final String BASAL_DATA_PATH = "/nightscout_watch_basal";
    public static final String BOLUS_PROGRESS_PATH = "/nightscout_watch_bolusprogress";
    public static final String ACTION_CONFIRMATION_REQUEST_PATH = "/nightscout_watch_actionconfirmationrequest";
    public static final String ACTION_CHANGECONFIRMATION_REQUEST_PATH = "/nightscout_watch_changeconfirmationrequest";
    public static final String ACTION_CANCELNOTIFICATION_REQUEST_PATH = "/nightscout_watch_cancelnotificationrequest";


    private static boolean lastLoopStatus;

    private Handler handler;

    // Phone
    private static final String CAPABILITY_PHONE_APP = "phone_app_sync_bgs";
    private static final String MESSAGE_PATH_PHONE = "/phone_message_path";
    // Wear
    private static final String CAPABILITY_WEAR_APP = "wear_app_sync_bgs";
    private static final String MESSAGE_PATH_WEAR = "/wear_message_path";


    @Override
    public void onCreate() {
        AndroidInjection.inject(this);
        if (wearIntegration()) {
            googleApiConnect();
        }
        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread(this.getClass().getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }
    }

    private boolean wearIntegration() {
        return wearPlugin.isEnabled();
    }

    private void googleApiConnect() {
        if (googleApiClient != null && (googleApiClient.isConnected() || googleApiClient.isConnecting())) {
            googleApiClient.disconnect();
        }
        googleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
        Wearable.MessageApi.addListener(googleApiClient, this);
        if (googleApiClient.isConnected()) {
            aapsLogger.debug(LTag.WEAR, "API client is connected");
        } else {
            // Log.d("WatchUpdater", logPrefix + "API client is not connected and is trying to connect");
            googleApiClient.connect();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        // Log.d(TAG, logPrefix + "onStartCommand: " + action);

        if (wearIntegration()) {
            handler.post(() -> {
                if (googleApiClient != null && googleApiClient.isConnected()) {
                    if (ACTION_RESEND.equals(action)) {
                        resendData();
                    } else if (ACTION_OPEN_SETTINGS.equals(action)) {
                        sendNotification();
                    } else if (ACTION_SEND_STATUS.equals(action)) {
                        sendStatus();
                    } else if (ACTION_SEND_BASALS.equals(action)) {
                        sendBasals();
                    } else if (ACTION_SEND_BOLUSPROGRESS.equals(action)) {
                        sendBolusProgress(intent.getIntExtra("progresspercent", 0), intent.hasExtra("progressstatus") ? intent.getStringExtra("progressstatus") : "");
                    } else if (ACTION_SEND_ACTIONCONFIRMATIONREQUEST.equals(action)) {
                        String title = intent.getStringExtra("title");
                        String message = intent.getStringExtra("message");
                        String actionstring = intent.getStringExtra("actionstring");
                        sendActionConfirmationRequest(title, message, actionstring);
                    } else if (ACTION_SEND_CHANGECONFIRMATIONREQUEST.equals(action)) {
                        String title = intent.getStringExtra("title");
                        String message = intent.getStringExtra("message");
                        String actionstring = intent.getStringExtra("actionstring");
                        sendChangeConfirmationRequest(title, message, actionstring);
                    } else if (ACTION_CANCEL_NOTIFICATION.equals(action)) {
                        String actionstring = intent.getStringExtra("actionstring");
                        sendCancelNotificationRequest(actionstring);
                    } else {
                        sendData();
                    }
                } else {
                    if (googleApiClient != null) googleApiClient.connect();
                }
            });
        }

        return START_STICKY;
    }


    private void updateWearSyncBgsCapability(CapabilityInfo capabilityInfo) {
        Log.d("WatchUpdaterService", "CabilityInfo: " + capabilityInfo);
        Set<Node> connectedNodes = capabilityInfo.getNodes();
        String mWearNodeId = pickBestNodeId(connectedNodes);
    }


    private String pickBestNodeId(Set<Node> nodes) {
        String bestNodeId = null;
        // Find a nearby node or pick one arbitrarily
        for (Node node : nodes) {
            if (node.isNearby()) {
                return node.getId();
            }
            bestNodeId = node.getId();
        }
        return bestNodeId;
    }


    @Override
    public void onConnected(Bundle connectionHint) {
        CapabilityApi.CapabilityListener capabilityListener = capabilityInfo -> {
            updateWearSyncBgsCapability(capabilityInfo);
            // Log.d(TAG, logPrefix + "onConnected onCapabilityChanged mWearNodeID:" + mWearNodeId);
            // new CheckWearableConnected().execute();
        };

        Wearable.CapabilityApi.addCapabilityListener(googleApiClient, capabilityListener, CAPABILITY_WEAR_APP);
        sendData();
    }


    @Override
    public void onPeerConnected(com.google.android.gms.wearable.Node peer) {// KS
        super.onPeerConnected(peer);
        String id = peer.getId();
        String name = peer.getDisplayName();
        // Log.d(TAG, logPrefix + "onPeerConnected peer name & ID: " + name + "|" + id);
    }


    @Override
    public void onPeerDisconnected(com.google.android.gms.wearable.Node peer) {// KS
        super.onPeerDisconnected(peer);
        String id = peer.getId();
        String name = peer.getDisplayName();
        // Log.d(TAG, logPrefix + "onPeerDisconnected peer name & ID: " + name + "|" + id);
    }


    @Override
    public void onMessageReceived(MessageEvent event) {

        // Log.d(TAG, logPrefix + "onMessageRecieved: " + event);

        if (wearIntegration()) {
            if (event != null && event.getPath().equals(WEARABLE_RESEND_PATH)) {
                resendData();
            }

            if (event != null && event.getPath().equals(WEARABLE_CANCELBOLUS_PATH)) {
                cancelBolus();
            }

            if (event != null && event.getPath().equals(WEARABLE_INITIATE_ACTIONSTRING_PATH)) {
                String actionstring = new String(event.getData());
                aapsLogger.debug(LTag.WEAR, "Wear: " + actionstring);
                rxBus.send(new EventWearInitiateAction(actionstring));
            }

            if (event != null && event.getPath().equals(WEARABLE_CONFIRM_ACTIONSTRING_PATH)) {
                String actionstring = new String(event.getData());
                aapsLogger.debug(LTag.WEAR, "Wear Confirm: " + actionstring);
                rxBus.send(new EventWearConfirmAction(actionstring));
            }
        }
    }

    private void cancelBolus() {
        activePlugin.getActivePump().stopBolusDelivering();
    }

    private void sendData() {

        GlucoseValue lastBG = iobCobCalculator.getAds().lastBg();
        // Log.d(TAG, logPrefix + "LastBg=" + lastBG);
        if (lastBG != null) {
            GlucoseStatus glucoseStatus = glucoseStatusProvider.getGlucoseStatusData();

            if (googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) {
                googleApiConnect();
            }
            if (wearIntegration()) {

                final DataMap dataMap = dataMapSingleBG(lastBG, glucoseStatus);

                (new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, dataMap);
            }
        }
    }


    private DataMap dataMapSingleBG(GlucoseValue lastBG, GlucoseStatus glucoseStatus) {
        GlucoseUnit units = profileFunction.getUnits();
        double convert2MGDL = 1.0;
        if (units.equals(GlucoseUnit.MMOL))
            convert2MGDL = Constants.MMOLL_TO_MGDL;
        double lowLine = defaultValueHelper.determineLowLine() * convert2MGDL;
        double highLine = defaultValueHelper.determineHighLine() * convert2MGDL;

        long sgvLevel = 0L;
        if (lastBG.getValue() > highLine) {
            sgvLevel = 1;
        } else if (lastBG.getValue() < lowLine) {
            sgvLevel = -1;
        }

        DataMap dataMap = new DataMap();
        dataMap.putString("sgvString", GlucoseValueExtensionKt.valueToUnitsString(lastBG, units));
        dataMap.putString("glucoseUnits", units.getAsText());
        dataMap.putLong("timestamp", lastBG.getTimestamp());
        if (glucoseStatus == null) {
            dataMap.putString("slopeArrow", "");
            dataMap.putString("delta", "--");
            dataMap.putString("avgDelta", "--");
        } else {
            dataMap.putString("slopeArrow", trendCalculator.getTrendArrow(lastBG).getSymbol());
            dataMap.putString("delta", deltastring(glucoseStatus.getDelta(), glucoseStatus.getDelta() * Constants.MGDL_TO_MMOLL, units));
            dataMap.putString("avgDelta", deltastring(glucoseStatus.getShortAvgDelta(), glucoseStatus.getShortAvgDelta() * Constants.MGDL_TO_MMOLL, units));
        }
        dataMap.putLong("sgvLevel", sgvLevel);
        dataMap.putDouble("sgvDouble", lastBG.getValue());
        dataMap.putDouble("high", highLine);
        dataMap.putDouble("low", lowLine);
        return dataMap;
    }

    private String deltastring(double deltaMGDL, double deltaMMOL, GlucoseUnit units) {
        String deltastring = "";
        if (deltaMGDL >= 0) {
            deltastring += "+";
        } else {
            deltastring += "-";
        }

        boolean detailed = sp.getBoolean(R.string.key_wear_detailed_delta, false);
        if (units.equals(GlucoseUnit.MGDL)) {
            if (detailed) {
                deltastring += DecimalFormatter.INSTANCE.to1Decimal(Math.abs(deltaMGDL));
            } else {
                deltastring += DecimalFormatter.INSTANCE.to0Decimal(Math.abs(deltaMGDL));
            }
        } else {
            if (detailed) {
                deltastring += DecimalFormatter.INSTANCE.to2Decimal(Math.abs(deltaMMOL));
            } else {
                deltastring += DecimalFormatter.INSTANCE.to1Decimal(Math.abs(deltaMMOL));
            }
        }
        return deltastring;
    }

    private void resendData() {
        if (googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) {
            googleApiConnect();
        }
        long startTime = System.currentTimeMillis() - (long) (60000 * 60 * 5.5);
        GlucoseValue last_bg = iobCobCalculator.getAds().lastBg();

        if (last_bg == null) return;

        List<GlucoseValue> graph_bgs = repository.compatGetBgReadingsDataFromTime(startTime, true).blockingGet();
        GlucoseStatus glucoseStatus = glucoseStatusProvider.getGlucoseStatusData(true);

        if (!graph_bgs.isEmpty()) {
            DataMap entries = dataMapSingleBG(last_bg, glucoseStatus);
            final ArrayList<DataMap> dataMaps = new ArrayList<>(graph_bgs.size());
            for (GlucoseValue bg : graph_bgs) {
                DataMap dataMap = dataMapSingleBG(bg, glucoseStatus);
                dataMaps.add(dataMap);
            }
            entries.putDataMapArrayList("entries", dataMaps);
            (new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, entries);
        }
        sendPreferences();
        sendBasals();
        sendStatus();
    }

    private void sendBasals() {
        if (googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) {
            googleApiConnect();
        }

        long now = System.currentTimeMillis();
        final long startTimeWindow = now - (long) (60000 * 60 * 5.5);


        ArrayList<DataMap> basals = new ArrayList<>();
        ArrayList<DataMap> temps = new ArrayList<>();
        ArrayList<DataMap> boluses = new ArrayList<>();
        ArrayList<DataMap> predictions = new ArrayList<>();


        Profile profile = profileFunction.getProfile();

        if (profile == null) {
            return;
        }

        long beginBasalSegmentTime = startTimeWindow;
        long runningTime = startTimeWindow;

        double beginBasalValue = profile.getBasal(beginBasalSegmentTime);
        double endBasalValue = beginBasalValue;

        TemporaryBasal tb1 = iobCobCalculator.getTempBasalIncludingConvertedExtended(runningTime);
        TemporaryBasal tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(runningTime); //TODO for Adrian ... what's the meaning?
        double tb_before = beginBasalValue;
        double tb_amount = beginBasalValue;
        long tb_start = runningTime;

        if (tb1 != null) {
            tb_before = beginBasalValue;
            Profile profileTB = profileFunction.getProfile(runningTime);
            if (profileTB != null) {
                tb_amount = TemporaryBasalExtensionKt.convertedToAbsolute(tb1, runningTime, profileTB);
                tb_start = runningTime;
            }
        }


        for (; runningTime < now; runningTime += 5 * 60 * 1000) {
            Profile profileTB = profileFunction.getProfile(runningTime);
            if (profileTB == null)
                return;
            //basal rate
            endBasalValue = profile.getBasal(runningTime);
            if (endBasalValue != beginBasalValue) {
                //push the segment we recently left
                basals.add(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue));

                //begin new Basal segment
                beginBasalSegmentTime = runningTime;
                beginBasalValue = endBasalValue;
            }

            //temps
            tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(runningTime);

            if (tb1 == null && tb2 == null) {
                //no temp stays no temp

            } else if (tb1 != null && tb2 == null) {
                //temp is over -> push it
                temps.add(tempDatamap(tb_start, tb_before, runningTime, endBasalValue, tb_amount));
                tb1 = null;

            } else if (tb1 == null && tb2 != null) {
                //temp begins
                tb1 = tb2;
                tb_start = runningTime;
                tb_before = endBasalValue;
                tb_amount = TemporaryBasalExtensionKt.convertedToAbsolute(tb1, runningTime, profileTB);

            } else if (tb1 != null && tb2 != null) {
                double currentAmount = TemporaryBasalExtensionKt.convertedToAbsolute(tb2, runningTime, profileTB);
                if (currentAmount != tb_amount) {
                    temps.add(tempDatamap(tb_start, tb_before, runningTime, currentAmount, tb_amount));
                    tb_start = runningTime;
                    tb_before = tb_amount;
                    tb_amount = currentAmount;
                    tb1 = tb2;
                }
            }
        }
        if (beginBasalSegmentTime != runningTime) {
            //push the remaining segment
            basals.add(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue));
        }
        if (tb1 != null) {
            tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(now); //use "now" to express current situation
            if (tb2 == null) {
                //express the cancelled temp by painting it down one minute early
                temps.add(tempDatamap(tb_start, tb_before, now - 60 * 1000, endBasalValue, tb_amount));
            } else {
                //express currently running temp by painting it a bit into the future
                Profile profileNow = profileFunction.getProfile(now);
                double currentAmount = TemporaryBasalExtensionKt.convertedToAbsolute(tb2, now, profileNow);
                if (currentAmount != tb_amount) {
                    temps.add(tempDatamap(tb_start, tb_before, now, tb_amount, tb_amount));
                    temps.add(tempDatamap(now, tb_amount, runningTime + 5 * 60 * 1000, currentAmount, currentAmount));
                } else {
                    temps.add(tempDatamap(tb_start, tb_before, runningTime + 5 * 60 * 1000, tb_amount, tb_amount));
                }
            }
        } else {
            tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(now); //use "now" to express current situation
            if (tb2 != null) {
                //onset at the end
                Profile profileTB = profileFunction.getProfile(runningTime);
                double currentAmount = TemporaryBasalExtensionKt.convertedToAbsolute(tb2, runningTime, profileTB);
                temps.add(tempDatamap(now - 60 * 1000, endBasalValue, runningTime + 5 * 60 * 1000, currentAmount, currentAmount));
            }
        }

        repository.getBolusesIncludingInvalidFromTime(startTimeWindow, true).blockingGet()
                .stream()
                .filter(bolus -> bolus.getType() != Bolus.Type.PRIMING)
                .forEach(bolus -> boluses.add(treatmentMap(bolus.getTimestamp(), bolus.getAmount(), 0, bolus.getType() == Bolus.Type.SMB, bolus.isValid())));
        repository.getCarbsDataFromTimeExpanded(startTimeWindow, true).blockingGet()
                .forEach(carb -> boluses.add(treatmentMap(carb.getTimestamp(), 0, carb.getAmount(), false, carb.isValid())));

        final LoopPlugin.LastRun finalLastRun = loop.getLastRun();
        if (sp.getBoolean("wear_predictions", true) && finalLastRun != null && finalLastRun.getRequest().getHasPredictions() && finalLastRun.getConstraintsProcessed() != null) {
            List<GlucoseValueDataPoint> predArray =
                    finalLastRun.getConstraintsProcessed().getPredictions()
                            .stream().map(bg -> new GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, rh))
                            .collect(Collectors.toList());

            if (!predArray.isEmpty()) {
                for (GlucoseValueDataPoint bg : predArray) {
                    if (bg.getData().getValue() < 40) continue;
                    predictions.add(predictionMap(bg.getData().getTimestamp(), bg.getData().getValue(), bg.getPredictionColor()));
                }
            }
        }


        DataMap dm = new DataMap();
        dm.putDataMapArrayList("basals", basals);
        dm.putDataMapArrayList("temps", temps);
        dm.putDataMapArrayList("boluses", boluses);
        dm.putDataMapArrayList("predictions", predictions);
        (new SendToDataLayerThread(BASAL_DATA_PATH, googleApiClient)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, dm);
    }

    private DataMap tempDatamap(long startTime, double startBasal, long to, double toBasal, double amount) {
        DataMap dm = new DataMap();
        dm.putLong("starttime", startTime);
        dm.putDouble("startBasal", startBasal);
        dm.putLong("endtime", to);
        dm.putDouble("endbasal", toBasal);
        dm.putDouble("amount", amount);
        return dm;
    }

    private DataMap basalMap(long startTime, long endTime, double amount) {
        DataMap dm = new DataMap();
        dm.putLong("starttime", startTime);
        dm.putLong("endtime", endTime);
        dm.putDouble("amount", amount);
        return dm;
    }

    private DataMap treatmentMap(long date, double bolus, double carbs, boolean isSMB, boolean isValid) {
        DataMap dm = new DataMap();
        dm.putLong("date", date);
        dm.putDouble("bolus", bolus);
        dm.putDouble("carbs", carbs);
        dm.putBoolean("isSMB", isSMB);
        dm.putBoolean("isValid", isValid);
        return dm;
    }

    private DataMap predictionMap(long timestamp, double sgv, int color) {
        DataMap dm = new DataMap();
        dm.putLong("timestamp", timestamp);
        dm.putDouble("sgv", sgv);
        dm.putInt("color", color);
        return dm;
    }


    private void sendNotification() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(OPEN_SETTINGS_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("openSettings", "openSettings");
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("OpenSettings", "No connection to wearable available!");
        }
    }

    private void sendBolusProgress(int progresspercent, String status) {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(BOLUS_PROGRESS_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("bolusProgress", "bolusProgress");
            dataMapRequest.getDataMap().putString("progressstatus", status);
            dataMapRequest.getDataMap().putInt("progresspercent", progresspercent);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("BolusProgress", "No connection to wearable available!");
        }
    }

    private void sendActionConfirmationRequest(String title, String message, String actionstring) {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(ACTION_CONFIRMATION_REQUEST_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("actionConfirmationRequest", "actionConfirmationRequest");
            dataMapRequest.getDataMap().putString("title", title);
            dataMapRequest.getDataMap().putString("message", message);
            dataMapRequest.getDataMap().putString("actionstring", actionstring);

            aapsLogger.debug(LTag.WEAR, "Requesting confirmation from wear: " + actionstring);

            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("confirmationRequest", "No connection to wearable available!");
        }
    }

    private void sendChangeConfirmationRequest(String title, String message, String actionstring) {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(ACTION_CHANGECONFIRMATION_REQUEST_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("changeConfirmationRequest", "changeConfirmationRequest");
            dataMapRequest.getDataMap().putString("title", title);
            dataMapRequest.getDataMap().putString("message", message);
            dataMapRequest.getDataMap().putString("actionstring", actionstring);

            aapsLogger.debug(LTag.WEAR, "Requesting confirmation from wear: " + actionstring);

            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("changeConfirmRequest", "No connection to wearable available!");
        }
    }

    private void sendCancelNotificationRequest(String actionstring) {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(ACTION_CANCELNOTIFICATION_REQUEST_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("cancelNotificationRequest", "cancelNotificationRequest");
            dataMapRequest.getDataMap().putString("actionstring", actionstring);

            aapsLogger.debug(LTag.WEAR, "Canceling notification on wear: " + actionstring);

            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("cancelNotificationReq", "No connection to wearable available!");
        }
    }

    private void sendStatus() {

        if (googleApiClient != null && googleApiClient.isConnected()) {
            Profile profile = profileFunction.getProfile();
            String status = rh.gs(R.string.noprofile);
            String iobSum, iobDetail, cobString, currentBasal, bgiString;
            iobSum = iobDetail = cobString = currentBasal = bgiString = "";
            if (profile != null) {
                IobTotal bolusIob = iobCobCalculator.calculateIobFromBolus().round();
                IobTotal basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round();

                iobSum = DecimalFormatter.INSTANCE.to2Decimal(bolusIob.getIob() + basalIob.getBasaliob());
                iobDetail =
                        "(" + DecimalFormatter.INSTANCE.to2Decimal(bolusIob.getIob()) + "|" + DecimalFormatter.INSTANCE.to2Decimal(basalIob.getBasaliob()) + ")";
                cobString = iobCobCalculator.getCobInfo(false, "WatcherUpdaterService").generateCOBString();
                currentBasal = generateBasalString();

                //bgi
                double bgi =
                        -(bolusIob.getActivity() + basalIob.getActivity()) * 5 * Profile.Companion.fromMgdlToUnits(profile.getIsfMgdl(), profileFunction.getUnits());
                bgiString = "" + ((bgi >= 0) ? "+" : "") + DecimalFormatter.INSTANCE.to1Decimal(bgi);

                status = generateStatusString(profile, currentBasal, iobSum, iobDetail, bgiString);
            }


            //batteries
            int phoneBattery = receiverStatusStore.getBatteryLevel();
            String rigBattery = nsDeviceStatus.getUploaderStatus().trim();


            long openApsStatus;
            //OpenAPS status
            if (config.getAPS()) {
                //we are AndroidAPS
                openApsStatus = loop.getLastRun() != null && loop.getLastRun().getLastTBREnact() != 0 ? loop.getLastRun().getLastTBREnact() : -1;
            } else {
                //NSClient or remote
                openApsStatus = nsDeviceStatus.getOpenApsTimestamp();
            }

            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(NEW_STATUS_PATH);
            //unique content
            dataMapRequest.getDataMap().putString("externalStatusString", status);
            dataMapRequest.getDataMap().putString("iobSum", iobSum);
            dataMapRequest.getDataMap().putString("iobDetail", iobDetail);
            dataMapRequest.getDataMap().putBoolean("detailedIob", sp.getBoolean(R.string.key_wear_detailediob, false));
            dataMapRequest.getDataMap().putString("cob", cobString);
            dataMapRequest.getDataMap().putString("currentBasal", currentBasal);
            dataMapRequest.getDataMap().putString("battery", "" + phoneBattery);
            dataMapRequest.getDataMap().putString("rigBattery", rigBattery);
            dataMapRequest.getDataMap().putLong("openApsStatus", openApsStatus);
            dataMapRequest.getDataMap().putString("bgi", bgiString);
            dataMapRequest.getDataMap().putBoolean("showBgi", sp.getBoolean(R.string.key_wear_showbgi, false));
            dataMapRequest.getDataMap().putInt("batteryLevel", (phoneBattery >= 30) ? 1 : 0);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("SendStatus", "No connection to wearable available!");
        }
    }

    private void sendPreferences() {
        if (googleApiClient != null && googleApiClient.isConnected()) {

            boolean wearcontrol = sp.getBoolean(R.string.key_wear_control, false);

            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(NEW_PREFERENCES_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putBoolean(rh.gs(R.string.key_wear_control), wearcontrol);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        } else {
            Log.e("SendStatus", "No connection to wearable available!");
        }
    }

    @NonNull
    private String generateStatusString(Profile profile, String currentBasal, String iobSum, String iobDetail, String bgiString) {

        String status = "";

        if (profile == null) {
            status = rh.gs(R.string.noprofile);
            return status;
        }

        if (!((PluginBase)loop).isEnabled()) {
            status += rh.gs(R.string.disabledloop) + "\n";
            lastLoopStatus = false;
        } else {
            lastLoopStatus = true;
        }

        String iobString;
        if (sp.getBoolean(R.string.key_wear_detailediob, false)) {
            iobString = iobSum + " " + iobDetail;
        } else {
            iobString = iobSum + "U";
        }

        status += currentBasal + " " + iobString;

        //add BGI if shown, otherwise return
        if (sp.getBoolean(R.string.key_wear_showbgi, false)) {
            status += " " + bgiString;
        }

        return status;
    }

    @NonNull
    private String generateBasalString() {

        String basalStringResult;

        Profile profile = profileFunction.getProfile();
        if (profile == null)
            return "";

        TemporaryBasal activeTemp = iobCobCalculator.getTempBasalIncludingConvertedExtended(System.currentTimeMillis());
        if (activeTemp != null) {
            basalStringResult = TemporaryBasalExtensionKt.toStringShort(activeTemp);
        } else {
            basalStringResult = DecimalFormatter.INSTANCE.to2Decimal(profile.getBasal()) + "U/h";
        }
        return basalStringResult;
    }

    @Override
    public void onDestroy() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    public static boolean shouldReportLoopStatus(boolean enabled) {
        return (lastLoopStatus != enabled);
    }
}
