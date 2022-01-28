package info.nightscout.androidaps.danar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.dana.DanaPump;
import info.nightscout.androidaps.danar.services.DanaRExecutionService;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.interfaces.CommandQueue;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.Profile;
import info.nightscout.androidaps.interfaces.PumpSync;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.rx.AapsSchedulers;
import info.nightscout.shared.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;

@Singleton
public class DanaRPlugin extends AbstractDanaRPlugin {
    private final CompositeDisposable disposable = new CompositeDisposable();

    private final AAPSLogger aapsLogger;
    private final Context context;
    private final ResourceHelper rh;
    private final ConstraintChecker constraintChecker;
    private final FabricPrivacy fabricPrivacy;

    @Inject
    public DanaRPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            AapsSchedulers aapsSchedulers,
            RxBus rxBus,
            Context context,
            ResourceHelper rh,
            ConstraintChecker constraintChecker,
            ActivePlugin activePlugin,
            SP sp,
            CommandQueue commandQueue,
            DanaPump danaPump,
            DateUtil dateUtil,
            FabricPrivacy fabricPrivacy,
            PumpSync pumpSync
    ) {
        super(injector, danaPump, rh, constraintChecker, aapsLogger, aapsSchedulers, commandQueue, rxBus, activePlugin, sp, dateUtil, pumpSync);
        this.aapsLogger = aapsLogger;
        this.context = context;
        this.rh = rh;
        this.constraintChecker = constraintChecker;
        this.fabricPrivacy = fabricPrivacy;

        useExtendedBoluses = sp.getBoolean(R.string.key_danar_useextended, false);
        pumpDescription.fillFor(PumpType.DANA_R);
    }

    @Override
    protected void onStart() {
        Intent intent = new Intent(context, DanaRExecutionService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        disposable.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> {
                    if (isEnabled(PluginType.PUMP)) {
                        boolean previousValue = useExtendedBoluses;
                        useExtendedBoluses = sp.getBoolean(R.string.key_danar_useextended, false);

                        if (useExtendedBoluses != previousValue && pumpSync.expectedPumpState().getExtendedBolus() != null) {
                            sExecutionService.extendedBolusStop();
                        }
                    }
                }, fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> context.unbindService(mConnection), fabricPrivacy::logException)
        );
        super.onStart();
    }

    @Override
    protected void onStop() {
        context.unbindService(mConnection);

        disposable.clear();
        super.onStop();
    }

    private final ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected");
            sExecutionService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            aapsLogger.debug(LTag.PUMP, "Service is connected");
            DanaRExecutionService.LocalBinder mLocalBinder = (DanaRExecutionService.LocalBinder) service;
            sExecutionService = mLocalBinder.getServiceInstance();
        }
    };

    // Plugin base interface
    @NonNull
    @Override
    public String getName() {
        return rh.gs(R.string.danarpump);
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_danar;
    }

    // Pump interface
    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return useExtendedBoluses;
    }

    @Override
    public boolean isInitialized() {
        return danaPump.getLastConnection() > 0 && danaPump.isExtendedBolusEnabled() && danaPump.getMaxBasal() > 0 && danaPump.isPasswordOK();
    }

    @Override
    public boolean isHandshakeInProgress() {
        return sExecutionService != null && sExecutionService.isHandshakeInProgress();
    }

    @Override
    public void finishHandshaking() {
        sExecutionService.finishHandshaking();
    }

    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(new Constraint<>(detailedBolusInfo.insulin)).value();
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            EventOverviewBolusProgress.Treatment t = new EventOverviewBolusProgress.Treatment(0, 0, detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB);
            boolean connectionOK = false;
            if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0)
                connectionOK = sExecutionService.bolus(detailedBolusInfo.insulin, (int) detailedBolusInfo.carbs, detailedBolusInfo.getCarbsTimestamp() != null ? detailedBolusInfo.getCarbsTimestamp() : detailedBolusInfo.timestamp, t);
            PumpEnactResult result = new PumpEnactResult(getInjector());
            result.success(connectionOK && Math.abs(detailedBolusInfo.insulin - t.insulin) < pumpDescription.getBolusStep())
                    .bolusDelivered(t.insulin)
                    .carbsDelivered(detailedBolusInfo.carbs);
            if (!result.getSuccess())
                result.comment(rh.gs(R.string.boluserrorcode, detailedBolusInfo.insulin, t.insulin, danaPump.getBolusStartErrorCode()));
            else
                result.comment(R.string.ok);
            aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.getBolusDelivered());
            detailedBolusInfo.insulin = t.insulin;
            detailedBolusInfo.timestamp = System.currentTimeMillis();
            if (detailedBolusInfo.insulin > 0)
                pumpSync.syncBolusWithPumpId(
                        detailedBolusInfo.timestamp,
                        detailedBolusInfo.insulin,
                        detailedBolusInfo.getBolusType(),
                        dateUtil.now(),
                        PumpType.DANA_R,
                        serialNumber());
            if (detailedBolusInfo.carbs > 0)
                pumpSync.syncCarbsWithTimestamp(
                        detailedBolusInfo.getCarbsTimestamp() != null ? detailedBolusInfo.getCarbsTimestamp() : detailedBolusInfo.timestamp,
                        detailedBolusInfo.carbs,
                        null,
                        PumpType.DANA_R,
                        serialNumber());
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult(getInjector());
            result.success(false).bolusDelivered(0d).carbsDelivered(0d).comment(R.string.invalidinput);
            aapsLogger.error("deliverTreatment: Invalid input");
            return result;
        }
    }

    // This is called from APS
    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(double absoluteRate, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {
        // Recheck pump status if older than 30 min
        //This should not be needed while using queue because connection should be done before calling this
        PumpEnactResult result = new PumpEnactResult(getInjector());

        absoluteRate = constraintChecker.applyBasalConstraints(new Constraint<>(absoluteRate), profile).value();

        boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d && absoluteRate >= 0.10d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate() || absoluteRate < 0.10d;
        final boolean doHighTemp = absoluteRate > getBaseBasalRate() && !useExtendedBoluses;
        final boolean doExtendedTemp = absoluteRate > getBaseBasalRate() && useExtendedBoluses;

        int percentRate = Double.valueOf(absoluteRate / getBaseBasalRate() * 100).intValue();
        // Any basal less than 0.10u/h will be dumped once per hour, not every 4 minutes. So if it's less than .10u/h, set a zero temp.
        if (absoluteRate < 0.10d) percentRate = 0;
        if (percentRate < 100) percentRate = (int) Round.ceilTo((double) percentRate, 10d);
        else percentRate = (int) Round.floorTo((double) percentRate, 10d);
        if (percentRate > getPumpDescription().getMaxTempPercent()) {
            percentRate = getPumpDescription().getMaxTempPercent();
        }
        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Calculated percent rate: " + percentRate);

        if (percentRate == 100) doTempOff = true;

        if (doTempOff) {
            // If extended in progress
            if (danaPump.isExtendedInProgress() && useExtendedBoluses) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doTempOff)");
                return cancelExtendedBolus();
            }
            // If temp in progress
            if (danaPump.isTempBasalInProgress()) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelRealTempBasal();
            }
            result.success(true).enacted(false).percent(100).isPercent(true).isTempCancel(true);
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK");
            return result;
        }

        if (doLowTemp || doHighTemp) {
            // If extended in progress
            if (danaPump.isExtendedInProgress() && useExtendedBoluses) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)");
                result = cancelExtendedBolus();
                if (!result.getSuccess()) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous extended bolus (doLowTemp || doHighTemp)");
                    return result;
                }
            }
            // Check if some temp is already in progress
            if (danaPump.isTempBasalInProgress()) {
                // Correct basal already set ?
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: currently running: " + danaPump.temporaryBasalToString());
                if (danaPump.getTempBasalPercent() == percentRate && danaPump.getTempBasalRemainingMin() > 4) {
                    if (enforceNew) {
                        cancelTempBasal(true);
                    } else {
                        result.success(true).percent(percentRate).enacted(false).duration(danaPump.getTempBasalRemainingMin()).isPercent(true).isTempCancel(false);
                        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)");
                        return result;
                    }
                }
            }
            // Convert duration from minutes to hours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal " + percentRate + "% for " + durationInMinutes + " minutes (doLowTemp || doHighTemp)");
            return setTempBasalPercent(percentRate, durationInMinutes, profile, false, tbrType);
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (danaPump.isTempBasalInProgress()) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doExtendedTemp)");
                result = cancelRealTempBasal();
                // Check for proper result
                if (!result.getSuccess()) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous temp basal (doExtendedTemp)");
                    return result;
                }
            }

            // Calculate # of halfHours from minutes
            int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
            // We keep current basal running so need to sub current basal
            double extendedRateToSet = absoluteRate - getBaseBasalRate();
            extendedRateToSet = constraintChecker.applyBasalConstraints(new Constraint<>(extendedRateToSet), profile).value();
            // needs to be rounded to 0.1
            extendedRateToSet = Round.roundTo(extendedRateToSet, pumpDescription.getExtendedBolusStep() * 2); // *2 because of half hours

            // What is current rate of extended bolusing in u/h?
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Extended bolus in progress: " + (danaPump.isExtendedInProgress()) + " rate: " + danaPump.getExtendedBolusAbsoluteRate() + "U/h duration remaining: " + danaPump.getExtendedBolusRemainingMinutes() + "min");
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h");

            // Compare with extended rate in progress
            if (danaPump.isExtendedInProgress() && Math.abs(danaPump.getExtendedBolusAbsoluteRate() - extendedRateToSet) < getPumpDescription().getExtendedBolusStep()) {
                // correct extended already set
                result.success(true).absolute(danaPump.getExtendedBolusAbsoluteRate()).enacted(false).duration(danaPump.getExtendedBolusRemainingMinutes()).isPercent(false).isTempCancel(false);
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct extended already set");
                return result;
            }

            // Now set new extended, no need to to stop previous (if running) because it's replaced
            double extendedAmount = extendedRateToSet / 2 * durationInHalfHours;
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting extended: " + extendedAmount + "U  half hours: " + durationInHalfHours);
            result = setExtendedBolus(extendedAmount, durationInMinutes);
            if (!result.getSuccess()) {
                aapsLogger.error("setTempBasalAbsolute: Failed to set extended bolus");
                return result;
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Extended bolus set ok");
            result.absolute(result.getAbsolute() + getBaseBasalRate());
            return result;
        }
        // We should never end here
        aapsLogger.error("setTempBasalAbsolute: Internal error");
        result.success(false).comment("Internal error");
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        if (danaPump.isTempBasalInProgress())
            return cancelRealTempBasal();
        if (danaPump.isExtendedInProgress() && useExtendedBoluses) {
            return cancelExtendedBolus();
        }
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success(true).enacted(false).comment(R.string.ok).isTempCancel(true);
        return result;
    }

    @NonNull @Override
    public PumpType model() {
        return PumpType.DANA_R;
    }

    private PumpEnactResult cancelRealTempBasal() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        if (danaPump.isTempBasalInProgress()) {
            sExecutionService.tempBasalStop();
            if (!danaPump.isTempBasalInProgress()) {
                pumpSync.syncStopTemporaryBasalWithPumpId(
                        dateUtil.now(),
                        dateUtil.now(),
                        getPumpDescription().getPumpType(),
                        serialNumber()
                );
                result.success(true).enacted(true).isTempCancel(true);
            } else
                result.success(false).enacted(false).isTempCancel(true);
        } else {
            result.success(true).isTempCancel(true).comment(R.string.ok);
            aapsLogger.debug(LTag.PUMP, "cancelRealTempBasal: OK");
        }
        return result;
    }

    @NonNull @Override
    public PumpEnactResult loadEvents() {
        return new PumpEnactResult(getInjector()); // no history, not needed
    }

    @NonNull @Override
    public PumpEnactResult setUserOptions() {
        return sExecutionService.setUserOptions();
    }
}
