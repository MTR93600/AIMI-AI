package info.nightscout.androidaps.plugins.pump.common;

import android.content.Context;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Created by Dirceu on 06/04/21.
 */
public abstract class MedLinkPumpPluginAbstract extends PumpPluginAbstract {

    public abstract TemporaryBasal getTemporaryBasal();

    protected MedLinkPumpPluginAbstract(PluginDescription pluginDescription, PumpType pumpType, HasAndroidInjector injector, ResourceHelper resourceHelper, AAPSLogger aapsLogger, CommandQueueProvider commandQueue, RxBusWrapper rxBus, ActivePluginProvider activePlugin, SP sp, Context context, FabricPrivacy fabricPrivacy, DateUtil dateUtil) {
        super(pluginDescription, pumpType, injector, resourceHelper, aapsLogger, commandQueue, rxBus, activePlugin, sp, context, fabricPrivacy, dateUtil);
    }

    protected void deliverTreatment(@NotNull DetailedBolusInfo detailedBolusInfo,
                                    @NotNull Function1<? super PumpEnactResult, Unit> func) {
        try {
            if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
                // neither carbs nor bolus requested
                aapsLogger.error("deliverTreatment: Invalid input");

                func.invoke(new PumpEnactResult(getInjector()).success(false).enacted(false).bolusDelivered(0d).carbsDelivered(0d)
                        .comment(getResourceHelper().gs(R.string.invalidinput)));
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                deliverBolus(detailedBolusInfo, func);
            } else {
                //if (MedtronicHistoryData.doubleBolusDebug)
                //    aapsLogger.debug("DoubleBolusDebug: deliverTreatment::(carb only entry)");

                // no bolus required, carb only treatment
                activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);

                EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
                bolusingEvent.setT(new Treatment());
                bolusingEvent.getT().isSMB = detailedBolusInfo.isSMB;
                bolusingEvent.getT().isTBR = detailedBolusInfo.isTBR;
                bolusingEvent.setPercent(100);
                rxBus.send(bolusingEvent);

                aapsLogger.debug(LTag.PUMP, "deliverTreatment: Carb only treatment.");

                func.invoke(new PumpEnactResult(getInjector()).success(true).enacted(true).
                        bolusDelivered(0d).carbsDelivered(detailedBolusInfo.carbs)
                        .comment(getResourceHelper().gs(R.string.common_resultok)));
            }
        } finally {
            triggerUIChange();
        }
    }

    protected abstract void deliverBolus(DetailedBolusInfo detailedBolusInfo,
                                         @NotNull Function1<? super PumpEnactResult, Unit> func);

}
