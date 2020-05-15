package info.nightscout.androidaps.plugins.general.wear.tizenintegration;

// imports for Samsung communication
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.accessory.*;

import java.io.IOException;

// All imports below are necessary for sending or receive data
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.HandlerThread;
import android.app.Service;
import android.os.Binder;
import android.os.IBinder;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.NonNull;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.general.wear.ActionStringHandler;
import info.nightscout.androidaps.plugins.general.wear.WearPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.receivers.ReceiverStatusStore;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.ToastUtils;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class TizenUpdaterService extends SAAgent {
    @Inject public HasAndroidInjector injector;
    @Inject public AAPSLogger aapsLogger;
    @Inject public WearPlugin wearPlugin;
    @Inject public ResourceHelper resourceHelper;
    @Inject public SP sp;
    @Inject public ProfileFunction profileFunction;
    @Inject public DefaultValueHelper defaultValueHelper;
    @Inject public NSDeviceStatus nsDeviceStatus;
    @Inject public ActivePluginProvider activePlugin;
    @Inject public LoopPlugin loopPlugin;
    @Inject public IobCobCalculatorPlugin iobCobCalculatorPlugin;
    @Inject public TreatmentsPlugin treatmentsPlugin;
    @Inject public ActionStringHandler actionStringHandler;
    @Inject ReceiverStatusStore receiverStatusStore;
    @Inject Config config;

    private static final String TAG = "Tizen Service";
    private static final Class<ServiceConnection> SASOCKET_CLASS = ServiceConnection.class;
    private ServiceConnection mConnectionHandler = null;
    private Handler mHandler = new Handler();
    private Context mContext = null;
    private Handler handler;
    private final IBinder mBinder = new LocalBinder();


    public static final String ACTION_RESEND = TizenUpdaterService.class.getName().concat(".Resend");
    public static final String ACTION_OPEN_SETTINGS = TizenUpdaterService.class.getName().concat(".OpenSettings");
    public static final String ACTION_SEND_STATUS = TizenUpdaterService.class.getName().concat(".SendStatus");
    public static final String ACTION_SEND_BASALS = TizenUpdaterService.class.getName().concat(".SendBasals");
    public static final String ACTION_SEND_BOLUSPROGRESS = TizenUpdaterService.class.getName().concat(".BolusProgress");
    public static final String ACTION_SEND_ACTIONCONFIRMATIONREQUEST = TizenUpdaterService.class.getName().concat(".ActionConfirmationRequest");
    public static final String ACTION_SEND_CHANGECONFIRMATIONREQUEST = TizenUpdaterService.class.getName().concat(".ChangeConfirmationRequest");
    public static final String ACTION_CANCEL_NOTIFICATION = TizenUpdaterService.class.getName().concat(".CancelNotification");


    // channel list for Tizen
    private final int TIZEN_SENDSTATUS_CH = 105;
    private final int TIZEN_SENDDATA_CH = 110;
    private final int TIZEN_SENDBASALS_CH = 125;
    private final int TIZEN_PREFERENCES_CH = 115;
    private final int TIZEN_RESEND_CH = 120;
    private final int TIZEN_INITIATE_ACTIONSTRING_CH = 200;
    private final int TIZEN_CONFIRM_ACTIONSTRING_CH = 205;
    private final int TIZEN_CANCEL_ACTIONSTRING_CH = 210;
    private final int TIZEN_CANCELBOLUS_CH = 225;
    private final int TIZEN_OPENSETTINGS_CH = 230;
    private final int TIZEN_BOLUSPROGRESS_CH = 220;

    public static final String TIZEN_ENABLE = "tizenenable";
    public static final String logPrefix = "Tizen::";
    private static boolean lastLoopStatus;


    public TizenUpdaterService() {
        super(TAG, SASOCKET_CLASS);
    }


    @Override
    public void onCreate() {
        super.onCreate();
        AndroidInjection.inject(this);
        if (tizenIntegration()) {
            tizenApiConnect();
        }

        SA mAccessory = new SA();
        try {
            mAccessory.initialize(this);
        } catch (SsdkUnsupportedException e) {
            if (processUnsupportedException(e) == true) {
                return;
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (mConnectionHandler != null && mConnectionHandler.isConnected()) {
            mConnectionHandler=null; //todo: I'm not sure if it's enough or if it's closed cleanly
        }
        super.onDestroy();
    }

    @Override
    protected void onFindPeerAgentsResponse(SAPeerAgent[] peerAgents, int result) {
        if ((result == SAAgent.PEER_AGENT_FOUND) && (peerAgents != null)) {
            for(SAPeerAgent peerAgent:peerAgents)
                requestServiceConnection(peerAgent);
        } else if (result == SAAgent.FINDPEER_DEVICE_NOT_CONNECTED) {
            Toast.makeText(getApplicationContext(), "FINDPEER_DEVICE_NOT_CONNECTED", Toast.LENGTH_LONG).show();
        } else if (result == SAAgent.FINDPEER_SERVICE_NOT_FOUND) {
            Toast.makeText(getApplicationContext(), "FINDPEER_SERVICE_NOT_FOUND", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "No peers have been found!!!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onServiceConnectionRequested(SAPeerAgent peerAgent) {
        if (peerAgent != null && sp.getBoolean(TIZEN_ENABLE, false)) {
            acceptServiceConnectionRequest(peerAgent);
        }
    }

    @Override
    protected void onServiceConnectionResponse(SAPeerAgent peerAgent, SASocket socket, int result) {
        if (result == SAAgent.CONNECTION_SUCCESS) {
            this.mConnectionHandler = (ServiceConnection) socket;
            Log.e(TAG, "Connected");
            Toast.makeText(getBaseContext(), "Connected", Toast.LENGTH_LONG).show();
        } else if (result == SAAgent.CONNECTION_ALREADY_EXIST) {
            Toast.makeText(getBaseContext(), "CONNECTION_ALREADY_EXIST", Toast.LENGTH_LONG).show();
        } else if (result == SAAgent.CONNECTION_DUPLICATE_REQUEST) {
            Toast.makeText(getBaseContext(), "CONNECTION_DUPLICATE_REQUEST", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getBaseContext(), "Service Connection failure", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onError(SAPeerAgent peerAgent, String errorMessage, int errorCode) {
        super.onError(peerAgent, errorMessage, errorCode);
    }

    @Override
    protected void onPeerAgentsUpdated(SAPeerAgent[] peerAgents, int result) {
        final SAPeerAgent[] peers = peerAgents;
        final int status = result;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (peers != null) {
                    if (status == SAAgent.PEER_AGENT_AVAILABLE) {
                        Log.e(TAG, "TIZEN PEER_AGENT_AVAILABLE");
                        Toast.makeText(getApplicationContext(), "PEER_AGENT_AVAILABLE", Toast.LENGTH_LONG).show();
                        findPeers();
                    } else {
                        Log.e(TAG, "TIZEN PEER_AGENT_UNAVAILABLE");
                        Toast.makeText(getApplicationContext(), "PEER_AGENT_UNAVAILABLE", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    public class ServiceConnection extends SASocket {
        public ServiceConnection() {
            super(ServiceConnection.class.getName());
        }

        @Override
        public void onError(int channelId, String errorMessage, int errorCode) {
        }

        @Override
        public void onReceive(int channelId, byte[] data) {
// lines below for testing communication **********************************************************************************
            final String message = new String(data);
            Log.e(TAG, "Received: " + message);
            if (channelId == 105)
                Toast.makeText(getApplicationContext(), "105: " + message, Toast.LENGTH_LONG).show();
            else if (channelId==110)
                Toast.makeText(getApplicationContext(), "110: " + message, Toast.LENGTH_LONG).show();
            else if (channelId==115)
                Toast.makeText(getApplicationContext(), "115: " + message, Toast.LENGTH_LONG).show();
            else
                Toast.makeText(getApplicationContext(), "Other: " + message, Toast.LENGTH_LONG).show();
// End of bloc for testing communication **********************************************************************************

            if (mConnectionHandler.isConnected()) {
                if (channelId==TIZEN_RESEND_CH) {
                    resendData();
                }
                if (channelId==TIZEN_CANCELBOLUS_CH) {
                    cancelBolus();
                }
                if (channelId==TIZEN_INITIATE_ACTIONSTRING_CH) {
                    String actionstring = new String(data);
                    aapsLogger.debug(LTag.TIZEN, "Tizen: " + actionstring);
                    actionStringHandler.handleInitiate(actionstring);
                }
                if (channelId==TIZEN_CONFIRM_ACTIONSTRING_CH) {
                    String actionstring = new String(data);
                    aapsLogger.debug(LTag.TIZEN, "Tizen Confirm: " + actionstring);
                    actionStringHandler.handleConfirmation(actionstring);
                }
            } else { // todo: check if it a findpeers here
                findPeers();
            }



        }

        @Override
        protected void onServiceConnectionLost(int reason) {
            Log.e(TAG, "Disconnected");
            Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_LONG).show();
            closeConnection();
        }
    }

    public class LocalBinder extends Binder {
        public TizenUpdaterService getService() {
            return TizenUpdaterService.this;
        }
    }

    public void findPeers() { findPeerAgents(); }

    private boolean sendTizen(int channel, final String data) {
        boolean retvalue = false;
        if (mConnectionHandler != null) {
            try {
                mConnectionHandler.send(channel, data.getBytes());
                retvalue = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return retvalue;
    }

    public boolean closeConnection() {
        if (mConnectionHandler != null) {
            mConnectionHandler.close();
            mConnectionHandler = null;
            return true;
        } else {
            return false;
        }
    }

    private boolean processUnsupportedException(SsdkUnsupportedException e) {
        e.printStackTrace();
        int errType = e.getType();
        if (errType == SsdkUnsupportedException.VENDOR_NOT_SUPPORTED
                || errType == SsdkUnsupportedException.DEVICE_NOT_SUPPORTED) {
            /*
             * Your application can not use Samsung Accessory SDK. You application should work smoothly
             * without using this SDK, or you may want to notify user and close your app gracefully (release
             * resources, stop Service threads, close UI thread, etc.)
             */
            stopSelf();
        } else if (errType == SsdkUnsupportedException.LIBRARY_NOT_INSTALLED) {
            Log.e(TAG, "You need to install Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED) {
            Log.e(TAG, "You need to update Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED) {
            Log.e(TAG, "We recommend that you update your Samsung Accessory SDK before using this application.");
            return false;
        }
        return true;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (mConnectionHandler != null) {
            if (mConnectionHandler.isConnected()) {
                sendTizen(TIZEN_SENDSTATUS_CH,"Hello from AAPS");
            } else {
                findPeers();
            }
        }
/*
        if (tizenIntegration()) {
            handler.post(() -> {
                if (mConnectionHandler.isConnected()) {
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
                    //googleApiClient.connect();
                }
            });
        }

 */
        return Service.START_STICKY;
    }

    private void tizenApiConnect() {
        findPeers();
    }


    private boolean tizenIntegration() {
        return wearPlugin.isEnabled(PluginType.GENERAL) && sp.getBoolean(TIZEN_ENABLE, true) ;
    }


    // code below is for data exchange and integration as close as possible than Wearintegration plugin

    private void cancelBolus() {
        activePlugin.getActivePump().stopBolusDelivering();
    }

    public void sendData() {

        BgReading lastBG = iobCobCalculatorPlugin.lastBg();
        // Log.d(TAG, logPrefix + "LastBg=" + lastBG);
        if (lastBG != null) {
            GlucoseStatus glucoseStatus = new GlucoseStatus(injector).getGlucoseStatusData();

            if (mConnectionHandler != null && !mConnectionHandler.isConnected() ) {
                tizenApiConnect();
            }
            if (tizenIntegration()) {

                final JSONObject dataMap = dataMapSingleBG(lastBG, glucoseStatus);
                if (dataMap == null) { // todo: correct line below
                    //ToastUtils.showToastInUiThread(this, resourceHelper.gs(R.string.noprofile));
                    return;
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        sendTizen(TIZEN_SENDDATA_CH, dataMap.toString());
                    }
                });
            }
        }
    }

    private JSONObject dataMapSingleBG(BgReading lastBG, GlucoseStatus glucoseStatus) {
        String units = profileFunction.getUnits();

        double lowLine = defaultValueHelper.determineLowLine();
        double highLine = defaultValueHelper.determineHighLine();

        long sgvLevel = 0L;
        if (lastBG.value > highLine) {
            sgvLevel = 1;
        } else if (lastBG.value < lowLine) {
            sgvLevel = -1;
        }
        try {
            JSONObject dataMap = new JSONObject();
            dataMap.put("sgvString", lastBG.valueToUnitsToString(units));
            dataMap.put("glucoseUnits", units);
            dataMap.put("timestamp", lastBG.date);
            if (glucoseStatus == null) {
                dataMap.put("slopeArrow", "");
                dataMap.put("delta", "--");
                dataMap.put("avgDelta", "--");
            } else {
                dataMap.put("slopeArrow", slopeArrow(glucoseStatus.delta));
                dataMap.put("delta", deltastring(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units));
                dataMap.put("avgDelta", deltastring(glucoseStatus.avgdelta, glucoseStatus.avgdelta * Constants.MGDL_TO_MMOLL, units));
            }
            dataMap.put("sgvLevel", sgvLevel);
            dataMap.put("sgvDouble", lastBG.value);
            dataMap.put("high", highLine);
            dataMap.put("low", lowLine);
            return dataMap;
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
        return new JSONObject();
    }

    private String deltastring(double deltaMGDL, double deltaMMOL, String units) {
        String deltastring = "";
        if (deltaMGDL >= 0) {
            deltastring += "+";
        } else {
            deltastring += "-";
        }
        // todo change wear_detailed_delta with tizen dedicated pref
        boolean detailed = sp.getBoolean(R.string.key_wear_detailed_delta, false);
        if (units.equals(Constants.MGDL)) {
            if (detailed) {
                deltastring += DecimalFormatter.to1Decimal(Math.abs(deltaMGDL));
            } else {
                deltastring += DecimalFormatter.to0Decimal(Math.abs(deltaMGDL));
            }
        } else {
            if (detailed) {
                deltastring += DecimalFormatter.to2Decimal(Math.abs(deltaMMOL));
            } else {
                deltastring += DecimalFormatter.to1Decimal(Math.abs(deltaMMOL));
            }
        }
        return deltastring;
    }

    private String slopeArrow(double delta) {
        if (delta <= (-3.5 * 5)) {
            return "\u21ca";
        } else if (delta <= (-2 * 5)) {
            return "\u2193";
        } else if (delta <= (-1 * 5)) {
            return "\u2198";
        } else if (delta <= (1 * 5)) {
            return "\u2192";
        } else if (delta <= (2 * 5)) {
            return "\u2197";
        } else if (delta <= (3.5 * 5)) {
            return "\u2191";
        } else {
            return "\u21c8";
        }
    }

    public void resendData() {
        long startTime = System.currentTimeMillis() - (long) (60000 * 60 * 5.5);
        BgReading last_bg = iobCobCalculatorPlugin.lastBg();

        if (last_bg == null) return;

        List<BgReading> graph_bgs = MainApp.getDbHelper().getBgreadingsDataFromTime(startTime, true);
        GlucoseStatus glucoseStatus = new GlucoseStatus(injector).getGlucoseStatusData(true);

        if (!graph_bgs.isEmpty()) {
            try {
                JSONObject entries = dataMapSingleBG(last_bg, glucoseStatus);
                if (entries == null) {
                    // ToastUtils.showToastInUiThread(this, resourceHelper.gs(R.string.noprofile));
                    return;
                }
                final JSONArray dataMaps = new JSONArray();
                for (BgReading bg : graph_bgs) {
                    JSONObject dataMap = dataMapSingleBG(bg, glucoseStatus);
                    if (dataMap != null) {
                        dataMaps.put(dataMap);
                    }
                }
                entries.put("entries", dataMaps);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        sendTizen(TIZEN_RESEND_CH, entries.toString());
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "Unhandled exception");
            }
        }
        sendPreferences();
        sendBasals();
        sendStatus();
    }

    public void sendBasals() {
        if (mConnectionHandler != null && !mConnectionHandler.isConnected() ) {
            tizenApiConnect();
        }

        long now = System.currentTimeMillis();
        final long startTimeWindow = now - (long) (60000 * 60 * 5.5);

        try {
            JSONArray basals = new JSONArray();
            JSONArray temps = new JSONArray();
            JSONArray boluses = new JSONArray();
            JSONArray predictions = new JSONArray();

            Profile profile = profileFunction.getProfile();

            if (profile == null) {
                return;
            }

            long beginBasalSegmentTime = startTimeWindow;
            long runningTime = startTimeWindow;

            double beginBasalValue = profile.getBasal(beginBasalSegmentTime);
            double endBasalValue = beginBasalValue;

            TemporaryBasal tb1 = treatmentsPlugin.getTempBasalFromHistory(runningTime);
            TemporaryBasal tb2 = treatmentsPlugin.getTempBasalFromHistory(runningTime); //TODO for Adrian ... what's the meaning?
            double tb_before = beginBasalValue;
            double tb_amount = beginBasalValue;
            long tb_start = runningTime;

            if (tb1 != null) {
                tb_before = beginBasalValue;
                Profile profileTB = profileFunction.getProfile(runningTime);
                if (profileTB != null) {
                    tb_amount = tb1.tempBasalConvertedToAbsolute(runningTime, profileTB);
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
                    basals.put(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue));

                    //begin new Basal segment
                    beginBasalSegmentTime = runningTime;
                    beginBasalValue = endBasalValue;
                }

                //temps
                tb2 = treatmentsPlugin.getTempBasalFromHistory(runningTime);

                if (tb1 == null && tb2 == null) {
                    //no temp stays no temp

                } else if (tb1 != null && tb2 == null) {
                    //temp is over -> push it
                    temps.put(tempDatamap(tb_start, tb_before, runningTime, endBasalValue, tb_amount));
                    tb1 = null;

                } else if (tb1 == null && tb2 != null) {
                    //temp begins
                    tb1 = tb2;
                    tb_start = runningTime;
                    tb_before = endBasalValue;
                    tb_amount = tb1.tempBasalConvertedToAbsolute(runningTime, profileTB);

                } else if (tb1 != null && tb2 != null) {
                    double currentAmount = tb2.tempBasalConvertedToAbsolute(runningTime, profileTB);
                    if (currentAmount != tb_amount) {
                        temps.put(tempDatamap(tb_start, tb_before, runningTime, currentAmount, tb_amount));
                        tb_start = runningTime;
                        tb_before = tb_amount;
                        tb_amount = currentAmount;
                        tb1 = tb2;
                    }
                }
            }
            if (beginBasalSegmentTime != runningTime) {
                //push the remaining segment
                basals.put(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue));
            }
            if (tb1 != null) {
                tb2 = treatmentsPlugin.getTempBasalFromHistory(now); //use "now" to express current situation
                if (tb2 == null) {
                    //express the cancelled temp by painting it down one minute early
                    temps.put(tempDatamap(tb_start, tb_before, now - 1 * 60 * 1000, endBasalValue, tb_amount));
                } else {
                    //express currently running temp by painting it a bit into the future
                    Profile profileNow = profileFunction.getProfile(now);
                    double currentAmount = tb2.tempBasalConvertedToAbsolute(now, profileNow);
                    if (currentAmount != tb_amount) {
                        temps.put(tempDatamap(tb_start, tb_before, now, tb_amount, tb_amount));
                        temps.put(tempDatamap(now, tb_amount, runningTime + 5 * 60 * 1000, currentAmount, currentAmount));
                    } else {
                        temps.put(tempDatamap(tb_start, tb_before, runningTime + 5 * 60 * 1000, tb_amount, tb_amount));
                    }
                }
            } else {
                tb2 = treatmentsPlugin.getTempBasalFromHistory(now); //use "now" to express current situation
                if (tb2 != null) {
                    //onset at the end
                    Profile profileTB = profileFunction.getProfile(runningTime);
                    double currentAmount = tb2.tempBasalConvertedToAbsolute(runningTime, profileTB);
                    temps.put(tempDatamap(now - 1 * 60 * 1000, endBasalValue, runningTime + 5 * 60 * 1000, currentAmount, currentAmount));
                }
            }

            List<Treatment> treatments = treatmentsPlugin.getTreatmentsFromHistory();
            for (Treatment treatment : treatments) {
                if (treatment.date > startTimeWindow) {
                    boluses.put(treatmentMap(treatment.date, treatment.insulin, treatment.carbs, treatment.isSMB, treatment.isValid));
                }

            }

            final LoopPlugin.LastRun finalLastRun = loopPlugin.getLastRun();
            if (sp.getBoolean("wear_predictions", true) && finalLastRun != null && finalLastRun.getRequest().hasPredictions && finalLastRun.getConstraintsProcessed() != null) {
                List<BgReading> predArray = finalLastRun.getConstraintsProcessed().getPredictions();

                if (!predArray.isEmpty()) {
                    for (BgReading bg : predArray) {
                        if (bg.value < 40) continue;
                        predictions.put(predictionMap(bg.date, bg.value, bg.getPredectionColor()));
                    }
                }
            }

            JSONObject dm = new JSONObject();
            dm.put("basals", basals);
            dm.put("temps", temps);
            dm.put("boluses", boluses);
            dm.put("predictions", predictions);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendTizen(TIZEN_SENDBASALS_CH, dm.toString());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
    }

    private JSONObject tempDatamap(long startTime, double startBasal, long to, double toBasal, double amount) {
        JSONObject dm = new JSONObject();
        try {
            dm.put("starttime", startTime);
            dm.put("startBasal", startBasal);
            dm.put("endtime", to);
            dm.put("endbasal", toBasal);
            dm.put("amount", amount);
            return dm;
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
        return new JSONObject();
    }

    private JSONObject basalMap(long startTime, long endTime, double amount) {
        JSONObject dm = new JSONObject();
        try {
            dm.put("starttime", startTime);
            dm.put("endtime", endTime);
            dm.put("amount", amount);
            return dm;
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
        return new JSONObject();
    }

    private JSONObject treatmentMap(long date, double bolus, double carbs, boolean isSMB, boolean isValid) {
        JSONObject dm = new JSONObject();
        try {
            dm.put("date", date);
            dm.put("bolus", bolus);
            dm.put("carbs", carbs);
            dm.put("isSMB", isSMB);
            dm.put("isValid", isValid);
            return dm;
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
        return new JSONObject();
    }

    private JSONObject predictionMap(long timestamp, double sgv, int color) {
        JSONObject dm = new JSONObject();
        try {
            dm.put("timestamp", timestamp);
            dm.put("sgv", sgv);
            dm.put("color", color);
            return dm;
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
        return new JSONObject();
    }

    public void sendNotification() {
        if (mConnectionHandler != null && !mConnectionHandler.isConnected() ) {
            tizenApiConnect();
        }

        try {
            JSONObject dataMap = new JSONObject();
            dataMap.put("timestamp", System.currentTimeMillis());
            dataMap.put("openSettings", "openSettings");
            debugData("sendNotification", dataMap);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendTizen(TIZEN_OPENSETTINGS_CH, dataMap.toString());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
    }

    public void sendBolusProgress(int progresspercent, String status) {
        if (mConnectionHandler != null && !mConnectionHandler.isConnected() ) {
            tizenApiConnect();
        }

        try {
            JSONObject dataMap = new JSONObject();
            dataMap.put("timestamp", System.currentTimeMillis());
            dataMap.put("bolusProgress", status);
            dataMap.put("bolusProgress", "bolusProgress");
            dataMap.put("progresspercent", progresspercent);
            debugData("sendBolusProgress", dataMap);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendTizen(TIZEN_BOLUSPROGRESS_CH, dataMap.toString());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
    }

    public void sendActionConfirmationRequest(String title, String message, String actionstring) {
        if (mConnectionHandler != null && !mConnectionHandler.isConnected()) {
            tizenApiConnect();
        }

        try {
            JSONObject dataMap = new JSONObject();
            dataMap.put("timestamp", System.currentTimeMillis());
            dataMap.put("actionConfirmationRequest", "actionConfirmationRequest");
            dataMap.put("title", title);
            dataMap.put("message", message);
            dataMap.put("actionstring", actionstring);

            aapsLogger.debug(LTag.TIZEN, "Requesting confirmation from tizen: " + actionstring);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendTizen(TIZEN_CONFIRM_ACTIONSTRING_CH, dataMap.toString());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
    }

    public void sendChangeConfirmationRequest(String title, String message, String actionstring) {
        if (mConnectionHandler != null && !mConnectionHandler.isConnected()) {
            tizenApiConnect();
        }

        try {
            JSONObject dataMap = new JSONObject();
            dataMap.put("timestamp", System.currentTimeMillis());
            dataMap.put("changeConfirmationRequest", "changeConfirmationRequest");
            dataMap.put("title", title);
            dataMap.put("message", message);
            dataMap.put("actionstring", actionstring);

            aapsLogger.debug(LTag.TIZEN, "Requesting confirmation from tizen: " + actionstring);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendTizen(TIZEN_CONFIRM_ACTIONSTRING_CH, dataMap.toString());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
    }

    public void sendCancelNotificationRequest(String actionstring) {
        if (mConnectionHandler != null && !mConnectionHandler.isConnected()) {
            tizenApiConnect();
        }

        try {
            JSONObject dataMap = new JSONObject();
            dataMap.put("timestamp", System.currentTimeMillis());
            dataMap.put("cancelNotificationRequest", "cancelNotificationRequest");
            dataMap.put("actionstring", actionstring);

            aapsLogger.debug(LTag.TIZEN, "Canceling notification on tizen: " + actionstring);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendTizen(TIZEN_CANCEL_ACTIONSTRING_CH, dataMap.toString());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
    }

    public void sendStatus() {
        if (mConnectionHandler != null && !mConnectionHandler.isConnected()) {
            tizenApiConnect();
        }

        try {
            JSONObject dataMap = new JSONObject();
            Profile profile = profileFunction.getProfile();
            String status = resourceHelper.gs(R.string.noprofile);
            String iobSum, iobDetail, cobString, currentBasal, bgiString;
            iobSum = iobDetail = cobString = currentBasal = bgiString = "";
            if (profile != null) {
                treatmentsPlugin.updateTotalIOBTreatments();
                IobTotal bolusIob = treatmentsPlugin.getLastCalculationTreatments().round();
                treatmentsPlugin.updateTotalIOBTempBasals();
                IobTotal basalIob = treatmentsPlugin.getLastCalculationTempBasals().round();

                iobSum = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob);
                iobDetail = "(" + DecimalFormatter.to2Decimal(bolusIob.iob) + "|" + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
                cobString = iobCobCalculatorPlugin.getCobInfo(false, "WatcherUpdaterService").generateCOBString();
                currentBasal = generateBasalString();

                //bgi


                double bgi = -(bolusIob.activity + basalIob.activity) * 5 * Profile.fromMgdlToUnits(profile.getIsfMgdl(), profileFunction.getUnits());
                bgiString = "" + ((bgi >= 0) ? "+" : "") + DecimalFormatter.to1Decimal(bgi);

                status = generateStatusString(profile, currentBasal, iobSum, iobDetail, bgiString);
            }

            //batteries
            int phoneBattery = receiverStatusStore.getBatteryLevel();
            String rigBattery = nsDeviceStatus.getUploaderStatus().trim();

            long openApsStatus;
            //OpenAPS status
            if (config.getAPS()) {
                //we are AndroidAPS
                openApsStatus = loopPlugin.getLastRun() != null && loopPlugin.getLastRun().getLastTBREnact() != 0 ? loopPlugin.getLastRun().getLastTBREnact() : -1;
            } else {
                //NSClient or remote
                openApsStatus = NSDeviceStatus.getOpenApsTimestamp();
            }

            long pref= generatePreference();

            dataMap.put("externalStatusString", status);
            dataMap.put("wearsettings", pref);
            dataMap.put("iobSum", iobSum);
            dataMap.put("iobDetail", iobDetail);
            //dataMap.put("detailedIob", sp.getBoolean(R.string.key_wear_detailediob, false));
            dataMap.put("cob", cobString);
            dataMap.put("currentBasal", currentBasal);
            dataMap.put("battery", "" + phoneBattery);
            dataMap.put("rigBattery", rigBattery);
            dataMap.put("openApsStatus", openApsStatus);
            dataMap.put("bgi", bgiString);
            //dataMap.put("showBgi", sp.getBoolean(R.string.key_wear_showbgi, false));
            dataMap.put("batteryLevel", (phoneBattery >= 30) ? 1 : 0);
            debugData("sendStatus", dataMap);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendTizen(TIZEN_SENDSTATUS_CH, dataMap.toString());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
    }
    private void sendPreferences() {
        if (mConnectionHandler != null && !mConnectionHandler.isConnected() ) {
            tizenApiConnect();
        }

        try {
            JSONObject dataMap = new JSONObject();
            //boolean wearcontrol = sp.getBoolean("wearcontrol", false);
            long pref= generatePreference();

            dataMap.put("timestamp", System.currentTimeMillis());
            dataMap.put("wearsettings", pref);
            debugData("sendPreferences", dataMap);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendTizen(TIZEN_PREFERENCES_CH, dataMap.toString());
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Unhandled exception");
        }
    }

    private void debugData(String source, Object data) {
        // Log.d(TAG, "WR: " + source + " " + data);
    }

    @NonNull
    private String generateStatusString(Profile profile, String currentBasal, String iobSum, String iobDetail, String bgiString) {

        String status = "";

        if (profile == null) {
            status = resourceHelper.gs(R.string.noprofile);
            return status;
        }

        if (!loopPlugin.isEnabled(PluginType.LOOP)) {
            status += resourceHelper.gs(R.string.disabledloop) + "\n";
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

        TemporaryBasal activeTemp = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            basalStringResult = activeTemp.toStringShort();
        } else {
            if (sp.getBoolean(R.string.key_danar_visualizeextendedaspercentage, false)) {
                basalStringResult = "100%";
            } else {
                basalStringResult = DecimalFormatter.to2Decimal(profile.getBasal()) + "U/h";
            }
        }
        return basalStringResult;
    }

    public static boolean shouldReportLoopStatus(boolean enabled) {
        return (lastLoopStatus != enabled);
    }

    private long generatePreference() {

        return 0;
    }

}
