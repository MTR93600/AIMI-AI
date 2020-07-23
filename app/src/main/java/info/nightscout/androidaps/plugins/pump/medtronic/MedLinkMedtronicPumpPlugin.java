package info.nightscout.androidaps.plugins.pump.medtronic;



import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
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
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalMicrobolusOperations;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.InstanceId;


/**
 * Created by dirceu on 10.07.2020.
 */
public class MedLinkMedtronicPumpPlugin  extends PluginBase implements PumpInterface, MicrobolusPumpInterface {

    private static Logger log = LoggerFactory.getLogger(info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin.class);

    private static info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin plugin = null;

    private final TempBasalMicrobolusOperations tempbasalMicrobolusOperations;

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
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override
    public PumpEnactResult loadTDDs() {
        //no result, could read DB in the future?
        PumpEnactResult result = new PumpEnactResult();
        return result;
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
    public double getReservoirLevel() { return -1; }

    @Override
    public int getBatteryLevel() { return -1; }

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
        return result;
    }

    private PumpEnactResult scheduleSuspension(Integer percent, Integer durationInMinutes,
                                               Profile profile, PumpEnactResult result) {
        Integer totalSuspendedMinutes = (int)Math.round(durationInMinutes*(1-(percent/100d)));
        if(totalSuspendedMinutes< 5){
            Integer suspensionsQuantity = 1;
            Integer interval = durationInMinutes/suspensionsQuantity;
            List<Integer> intervals = new ArrayList<Integer>(Collections.nCopies(suspensionsQuantity, interval));
            this.tempbasalMicrobolusOperations.updateOperations(suspensionsQuantity,0d,intervals, totalSuspendedMinutes);
        } else if (totalSuspendedMinutes % 5 ==0){
            Integer suspensionsQuantity = new Double(totalSuspendedMinutes / 5).intValue();
            Integer interval = durationInMinutes/suspensionsQuantity;
            List<Integer> intervals = new ArrayList<Integer>(Collections.nCopies(suspensionsQuantity, interval));
            this.tempbasalMicrobolusOperations.updateOperations(suspensionsQuantity,0d,intervals, totalSuspendedMinutes);
        } else {
            Integer suspensions = new Double(totalSuspendedMinutes / 5).intValue();
            Integer interval = durationInMinutes/suspensions;
            //TODO need to reavaluate some cases, used floor here to avoid two possible up rounds
            Integer suspensionsQuantity = (int)Math.round(totalSuspendedMinutes/durationInMinutes);
            List<Integer> intervals = new ArrayList<Integer>(Collections.nCopies(suspensionsQuantity, interval));
            this.tempbasalMicrobolusOperations.updateOperations(suspensionsQuantity,0d,intervals,totalSuspendedMinutes);

        }
        //criar fila de comandos aqui, esta fila deverá ser consumida a cada execução de checagem de status
        return null;
    }

    private PumpEnactResult scheduleTempBasalBolus(Integer percent, Integer durationInMinutes,
                                                   Profile profile, PumpEnactResult result) {
        return null;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {

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

    @Override
    public boolean canHandleDST() {
        return true;
    }

    @Override
    public void timeDateOrTimeZoneChanged() {

    }


}
