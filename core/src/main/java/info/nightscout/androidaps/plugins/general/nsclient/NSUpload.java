package info.nightscout.androidaps.plugins.general.nsclient;

import android.os.Build;

import androidx.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.database.entities.GlucoseValue;
import info.nightscout.androidaps.database.entities.TemporaryTarget;
import info.nightscout.androidaps.database.entities.TherapyEvent;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.IobCobCalculatorInterface;
import info.nightscout.androidaps.interfaces.LoopInterface;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.ProfileStore;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.UploadQueueInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.plugins.aps.loop.DeviceStatus;
import info.nightscout.androidaps.plugins.configBuilder.RunningConfiguration;
import info.nightscout.androidaps.receivers.ReceiverStatusStore;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 26.05.2017.
 */
@Singleton
public class NSUpload {

    private final AAPSLogger aapsLogger;
    private final ResourceHelper resourceHelper;
    private final SP sp;
    private final UploadQueueInterface uploadQueue;
    private final RunningConfiguration runningConfiguration;
    private final ProfileFunction profileFunction;

    public static String ISVALID = "isValid";

    @Inject
    public NSUpload(
            AAPSLogger aapsLogger,
            ResourceHelper resourceHelper,
            SP sp,
            UploadQueueInterface uploadQueue,
            RunningConfiguration runningConfiguration,
            ProfileFunction profileFunction
    ) {
        this.aapsLogger = aapsLogger;
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.uploadQueue = uploadQueue;
        this.runningConfiguration = runningConfiguration;
        this.profileFunction = profileFunction;
    }

    public void uploadTempBasalStartAbsolute(TemporaryBasal temporaryBasal, Double originalExtendedAmount) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", TherapyEvent.Type.TEMPORARY_BASAL.getText());
            data.put("duration", temporaryBasal.durationInMinutes);
            data.put("absolute", temporaryBasal.absoluteRate);
            data.put("rate", temporaryBasal.absoluteRate);
            if (temporaryBasal.pumpId != 0)
                data.put("pumpId", temporaryBasal.pumpId);
            data.put("created_at", DateUtil.toISOString(temporaryBasal.date));
            data.put("enteredBy", "openaps://" + "AndroidAPS");
            if (originalExtendedAmount != null)
                data.put("originalExtendedAmount", originalExtendedAmount); // for back synchronization
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data, temporaryBasal.date));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadTempBasalStartPercent(TemporaryBasal temporaryBasal, Profile profile) {
        try {
            boolean useAbsolute = sp.getBoolean(R.string.key_ns_sync_use_absolute, false);
            double absoluteRate = 0;
            if (profile != null) {
                absoluteRate = profile.getBasal(temporaryBasal.date) * temporaryBasal.percentRate / 100d;
            }
            if (useAbsolute) {
                TemporaryBasal t = temporaryBasal.clone();
                t.isAbsolute = true;
                if (profile != null) {
                    t.absoluteRate = absoluteRate;
                    uploadTempBasalStartAbsolute(t, null);
                }
            } else {
                JSONObject data = new JSONObject();
                data.put("eventType", TherapyEvent.Type.TEMPORARY_BASAL.getText());
                data.put("duration", temporaryBasal.durationInMinutes);
                data.put("percent", temporaryBasal.percentRate - 100);
                if (profile != null)
                    data.put("rate", absoluteRate);
                if (temporaryBasal.pumpId != 0)
                    data.put("pumpId", temporaryBasal.pumpId);
                data.put("created_at", DateUtil.toISOString(temporaryBasal.date));
                data.put("enteredBy", "openaps://" + "AndroidAPS");
                uploadQueue.add(new DbRequest("dbAdd", "treatments", data, temporaryBasal.date));
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadTempBasalEnd(long time, boolean isFakedTempBasal, long pumpId) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", TherapyEvent.Type.TEMPORARY_BASAL.getText());
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", "openaps://" + "AndroidAPS");
            if (isFakedTempBasal)
                data.put("isFakedTempBasal", isFakedTempBasal);
            if (pumpId != 0)
                data.put("pumpId", pumpId);
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data, time));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadExtendedBolus(ExtendedBolus extendedBolus) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", TherapyEvent.Type.COMBO_BOLUS.getText());
            data.put("duration", extendedBolus.durationInMinutes);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", extendedBolus.insulin);
            data.put("relative", extendedBolus.insulin / extendedBolus.durationInMinutes * 60); // U/h
            if (extendedBolus.pumpId != 0)
                data.put("pumpId", extendedBolus.pumpId);
            data.put("created_at", DateUtil.toISOString(extendedBolus.date));
            data.put("enteredBy", "openaps://" + "AndroidAPS");
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data, extendedBolus.date));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadExtendedBolusEnd(long time, long pumpId) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", TherapyEvent.Type.COMBO_BOLUS.getText());
            data.put("duration", 0);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", 0);
            data.put("relative", 0);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", "openaps://" + "AndroidAPS");
            if (pumpId != 0)
                data.put("pumpId", pumpId);
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data, time));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadDeviceStatus(LoopInterface loopPlugin, IobCobCalculatorInterface iobCobCalculatorPlugin, ProfileFunction profileFunction, PumpInterface pumpInterface, ReceiverStatusStore receiverStatusStore, String version) {
        Profile profile = profileFunction.getProfile();
        String profileName = profileFunction.getProfileName();

        if (profile == null) {
            aapsLogger.error("Profile is null. Skipping upload");
            return;
        }

        DeviceStatus deviceStatus = new DeviceStatus(aapsLogger);
        try {
            LoopInterface.LastRun lastRun = loopPlugin.getLastRun();
            if (lastRun != null && lastRun.getLastAPSRun() > System.currentTimeMillis() - 300 * 1000L) {
                // do not send if result is older than 1 min
                APSResult apsResult = lastRun.getRequest();
                apsResult.json().put("timestamp", DateUtil.toISOString(lastRun.getLastAPSRun()));
                deviceStatus.suggested = apsResult.json();

                deviceStatus.iob = lastRun.getRequest().getIob().json();
                deviceStatus.iob.put("time", DateUtil.toISOString(lastRun.getLastAPSRun()));

                JSONObject requested = new JSONObject();

                if (lastRun.getTbrSetByPump() != null && lastRun.getTbrSetByPump().getEnacted()) { // enacted
                    deviceStatus.enacted = lastRun.getRequest().json();
                    deviceStatus.enacted.put("rate", lastRun.getTbrSetByPump().json(profile).get("rate"));
                    deviceStatus.enacted.put("duration", lastRun.getTbrSetByPump().json(profile).get("duration"));
                    deviceStatus.enacted.put("recieved", true);
                    requested.put("duration", lastRun.getRequest().getDuration());
                    requested.put("rate", lastRun.getRequest().getRate());
                    requested.put("temp", "absolute");
                    deviceStatus.enacted.put("requested", requested);
                }
                if (lastRun.getTbrSetByPump() != null && lastRun.getTbrSetByPump().getEnacted()) { // enacted
                    if (deviceStatus.enacted == null) {
                        deviceStatus.enacted = lastRun.getRequest().json();
                    }
                    deviceStatus.enacted.put("smb", lastRun.getTbrSetByPump().getBolusDelivered());
                    requested.put("smb", lastRun.getRequest().getSmb());
                    deviceStatus.enacted.put("requested", requested);
                }
            } else {
                aapsLogger.debug(LTag.NSCLIENT, "OpenAPS data too old to upload, sending iob only");
                IobTotal[] iob = iobCobCalculatorPlugin.calculateIobArrayInDia(profile);
                if (iob.length > 0) {
                    deviceStatus.iob = iob[0].json();
                    deviceStatus.iob.put("time", DateUtil.toISOString(DateUtil.now()));
                }
            }
            deviceStatus.device = "openaps://" + Build.MANUFACTURER + " " + Build.MODEL;
            JSONObject pumpstatus = pumpInterface.getJSONStatus(profile, profileName, version);
            deviceStatus.pump = pumpstatus;

            deviceStatus.uploaderBattery = receiverStatusStore.getBatteryLevel();

            deviceStatus.created_at = DateUtil.toISOString(new Date());

            deviceStatus.configuration = runningConfiguration.configuration();

            uploadQueue.add(new DbRequest("dbAdd", "devicestatus", deviceStatus.mongoRecord(), System.currentTimeMillis()));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadTreatmentRecord(DetailedBolusInfo detailedBolusInfo) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", detailedBolusInfo.eventType);
            if (detailedBolusInfo.insulin != 0d) data.put("insulin", detailedBolusInfo.insulin);
            if (detailedBolusInfo.carbs != 0d) data.put("carbs", (int) detailedBolusInfo.carbs);
            data.put("created_at", DateUtil.toISOString(detailedBolusInfo.date));
            data.put("date", detailedBolusInfo.date);
            data.put("isSMB", detailedBolusInfo.isSMB);
            if (detailedBolusInfo.pumpId != 0)
                data.put("pumpId", detailedBolusInfo.pumpId);
            if (detailedBolusInfo.glucose != 0d)
                data.put("glucose", detailedBolusInfo.glucose);
            if (!detailedBolusInfo.glucoseType.equals(""))
                data.put("glucoseType", detailedBolusInfo.glucoseType);
            if (detailedBolusInfo.boluscalc != null)
                data.put("boluscalc", detailedBolusInfo.boluscalc);
            if (detailedBolusInfo.carbTime != 0)
                data.put("preBolus", detailedBolusInfo.carbTime);
            if (!StringUtils.isEmpty(detailedBolusInfo.notes)) {
                data.put("notes", detailedBolusInfo.notes);
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        uploadCareportalEntryToNS(data, detailedBolusInfo.date);
    }

    public void uploadProfileSwitch(ProfileSwitch profileSwitch, long nsClientId) {
        try {
            JSONObject data = getJson(profileSwitch);
            uploadCareportalEntryToNS(data, nsClientId);
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadTempTarget(TemporaryTarget tempTarget) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", TherapyEvent.Type.TEMPORARY_TARGET.getText());
            data.put("duration", T.msecs(tempTarget.getDuration()).mins());
            data.put(ISVALID, tempTarget.isValid());
            if (tempTarget.getLowTarget() > 0) {
                data.put("reason", tempTarget.getReason().getText());
                data.put("targetBottom", Profile.fromMgdlToUnits(tempTarget.getLowTarget(), profileFunction.getUnits()));
                data.put("targetTop", Profile.fromMgdlToUnits(tempTarget.getHighTarget(), profileFunction.getUnits()));
                data.put("units", profileFunction.getUnits());
            }
            data.put("created_at", DateUtil.toISOString(tempTarget.getTimestamp()));
            data.put("enteredBy", "AndroidAPS");
            uploadCareportalEntryToNS(data, tempTarget.getTimestamp());
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void updateTempTarget(TemporaryTarget tempTarget) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", TherapyEvent.Type.TEMPORARY_TARGET.getText());
            data.put("duration", T.msecs(tempTarget.getDuration()).mins());
            data.put(ISVALID, tempTarget.isValid());
            if (tempTarget.getLowTarget() > 0) {
                data.put("reason", tempTarget.getReason().getText());
                data.put("targetBottom", Profile.fromMgdlToUnits(tempTarget.getLowTarget(), profileFunction.getUnits()));
                data.put("targetTop", Profile.fromMgdlToUnits(tempTarget.getHighTarget(), profileFunction.getUnits()));
                data.put("units", profileFunction.getUnits());
            }
            data.put("created_at", DateUtil.toISOString(tempTarget.getTimestamp()));
            data.put("enteredBy", "AndroidAPS");
            uploadQueue.add(new DbRequest("dbUpdate", "treatments", tempTarget.getInterfaceIDs().getNightscoutId(), data, tempTarget.getTimestamp()));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void updateProfileSwitch(ProfileSwitch profileSwitch) {
        try {
            JSONObject data = getJson(profileSwitch);
            if (profileSwitch._id != null) {
                uploadQueue.add(new DbRequest("dbUpdate", "treatments", profileSwitch._id, data, profileSwitch.date));
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    private static JSONObject getJson(ProfileSwitch profileSwitch) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("eventType", TherapyEvent.Type.PROFILE_SWITCH.getText());
        data.put("duration", profileSwitch.durationInMinutes);
        data.put("profile", profileSwitch.getCustomizedName());
        data.put("profileJson", profileSwitch.profileJson);
        data.put("profilePlugin", profileSwitch.profilePlugin);
        if (profileSwitch.isCPP) {
            data.put("CircadianPercentageProfile", true);
            data.put("timeshift", profileSwitch.timeshift);
            data.put("percentage", profileSwitch.percentage);
        }
        data.put("created_at", DateUtil.toISOString(profileSwitch.date));
        data.put("enteredBy", "AndroidAPS");

        return data;
    }

    public void uploadCareportalEntryToNS(JSONObject data, long nsClientId) {
        try {
            if (data.has("preBolus") && data.has("carbs")) {
                JSONObject prebolus = new JSONObject();
                prebolus.put("carbs", data.get("carbs"));
                data.remove("carbs");
                prebolus.put("eventType", data.get("eventType"));
                if (data.has("enteredBy")) prebolus.put("enteredBy", data.get("enteredBy"));
                if (data.has("notes")) prebolus.put("notes", data.get("notes"));
                long mills = DateUtil.fromISODateString(data.getString("created_at")).getTime();
                Date preBolusDate = new Date(mills + data.getInt("preBolus") * 60000L + 1000L);
                prebolus.put("created_at", DateUtil.toISOString(preBolusDate));
                uploadCareportalEntryToNS(prebolus, preBolusDate.getTime());
            }
            DbRequest dbr = new DbRequest("dbAdd", "treatments", data, nsClientId);
            aapsLogger.debug("Prepared: " + dbr.log());
            uploadQueue.add(dbr);
        } catch (Exception e) {
            aapsLogger.error("Unhandled exception", e);
        }

    }

    // TODO replace with seting isValid = false
    public void removeCareportalEntryFromNS(String _id) {
        uploadQueue.add(new DbRequest("dbRemove", "treatments", _id, System.currentTimeMillis()));
    }

    public void uploadError(String error) {
        uploadError(error, new Date());
    }

    public void uploadError(String error, Date date) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Announcement");
            data.put("created_at", DateUtil.toISOString(date));
            data.put("enteredBy", sp.getString("careportal_enteredby", "AndroidAPS"));
            data.put("notes", error);
            data.put("isAnnouncement", true);
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        uploadQueue.add(new DbRequest("dbAdd", "treatments", data, date.getTime()));
    }

    public void uploadBg(GlucoseValue reading, String source) {
        JSONObject data = new JSONObject();
        try {
            data.put("device", source);
            data.put("date", reading.getTimestamp());
            data.put("dateString", DateUtil.toISOString(reading.getTimestamp()));
            data.put("sgv", reading.getValue());
            data.put("direction", reading.getTrendArrow().getText());
            data.put("type", "sgv");
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        uploadQueue.add(new DbRequest("dbAdd", "entries", data, reading.getTimestamp()));
    }

    public void updateBg(GlucoseValue reading, String source) {
        JSONObject data = new JSONObject();
        try {
            data.put("device", source);
            data.put("date", reading.getTimestamp());
            data.put("dateString", DateUtil.toISOString(reading.getTimestamp()));
            data.put("sgv", reading.getValue());
            data.put("direction", reading.getTrendArrow().getText());
            data.put("type", "sgv");
            if (reading.getInterfaceIDs().getNightscoutId() != null) {
                uploadQueue.add(new DbRequest("dbUpdate", "entries", reading.getInterfaceIDs().getNightscoutId(), data, System.currentTimeMillis()));
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
    }

    public void uploadAppStart() {
        if (sp.getBoolean(R.string.key_ns_logappstartedevent, true)) {
            JSONObject data = new JSONObject();
            try {
                data.put("eventType", "Note");
                data.put("created_at", DateUtil.toISOString(new Date()));
                data.put("notes", resourceHelper.gs(R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL);
            } catch (JSONException e) {
                aapsLogger.error("Unhandled exception", e);
            }
            uploadQueue.add(new DbRequest("dbAdd", "treatments", data, System.currentTimeMillis()));
        }
    }

    public void uploadProfileStore(JSONObject profileStore) {
        if (sp.getBoolean(R.string.key_ns_uploadlocalprofile, false)) {
            uploadQueue.add(new DbRequest("dbAdd", "profile", profileStore, System.currentTimeMillis()));
        }
    }

    public void uploadEvent(String careportalEvent, long time, @Nullable String notes) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", careportalEvent);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", sp.getString("careportal_enteredby", "AndroidAPS"));
            data.put("units", profileFunction.getUnits());
            if (notes != null) {
                data.put("notes", notes);
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        uploadQueue.add(new DbRequest("dbAdd", "treatments", data, time));
    }

    public void uploadEvent(TherapyEvent event) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", event.getType().getText());
            data.put("created_at", event.getTimestamp());
            data.put("enteredBy", event.getEnteredBy());
            if (event.getGlucoseUnit() == TherapyEvent.GlucoseUnit.MGDL)
                data.put("units", Constants.MGDL);
            else data.put("units", Constants.MMOL);
            if (event.getDuration() != 0) data.put("duration", T.msecs(event.getDuration()).mins());
            if (event.getNote() != null) data.put("notes", event.getNote());
            if (event.getGlucose() != null) data.put("glucose", event.getGlucose());
            if (event.getGlucoseType() != null)
                data.put("glucoseType", event.getGlucoseType().getText());
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        uploadQueue.add(new DbRequest("dbAdd", "treatments", data, event.getTimestamp()));
    }

    public void removeFoodFromNS(String _id) {
        try {
            uploadQueue.add(new DbRequest("dbRemove", "food", _id, System.currentTimeMillis()));
        } catch (Exception e) {
            aapsLogger.error("Unhandled exception", e);
        }

    }

    public void createNSTreatment(JSONObject data, ProfileStore profileStore, ProfileFunction profileFunction, long eventTime) {
        if (JsonHelper.safeGetString(data, "eventType", "").equals(TherapyEvent.Type.PROFILE_SWITCH.getText())) {
            ProfileSwitch profileSwitch = profileFunction.prepareProfileSwitch(
                    profileStore,
                    JsonHelper.safeGetString(data, "profile"),
                    JsonHelper.safeGetInt(data, "duration"),
                    JsonHelper.safeGetInt(data, "percentage"),
                    JsonHelper.safeGetInt(data, "timeshift"),
                    eventTime
            );
            uploadProfileSwitch(profileSwitch, eventTime);
        } else {
            uploadCareportalEntryToNS(data, eventTime);
        }
    }

    public static boolean isIdValid(String _id) {
        if (_id == null)
            return false;
        return _id.length() == 24;
    }

}
