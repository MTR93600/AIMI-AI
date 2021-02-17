package info.nightscout.androidaps.data;

import org.json.JSONArray;

import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.db.BGTarget;
import info.nightscout.androidaps.db.CarbRatioPair;
import info.nightscout.androidaps.db.InsulinSensitivity;
import info.nightscout.androidaps.db.WizardSettings;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;

/**
 * Created by Dirceu on 02/02/21.
 */
public class MedLinkProfile extends Profile {
    public MedLinkProfile(HasAndroidInjector injector, WizardSettings settings,
                          List<CarbRatioPair> carbRatioPair,
                          List<InsulinSensitivity> insulinSensitivities,
                          List<BGTarget> bgTargets, String units) {
        super(injector);
        init(settings, carbRatioPair, insulinSensitivities, bgTargets, units);
    }

    private void init(WizardSettings settings, List<CarbRatioPair> carbRatioPair,
                      List<InsulinSensitivity> insulinSensitivities, List<BGTarget> bgTargets, String units) {

        this.setUnits(units);
        this.dia = Constants.defaultDIA;
        this.timeZone = TimeZone.getDefault();

    }

    private enum ProfileDataType {
        ISF, IC, Basal, TargetLow, TargetHigh
    }

    private double getMultiplier(ProfileDataType dataType) {
        double multiplier = 1d;

        switch (dataType) {
            case ISF: {
                multiplier = 100d / percentage;
            }
            break;
            case IC:
                multiplier = 100d / percentage;
                break;
            case Basal:
                multiplier = percentage / 100d;
                break;

        }
        return multiplier;
    }

    @Override
    public synchronized boolean isValid(String from, boolean notify) {
        if (!isValid)
            return false;
//        if (!isValidated) {
//            if (basal_v == null)
//                basal_v = convertToSparseArray(basal);
//            validate(basal_v);
//            if (isf_v == null)
//                isf_v = convertToSparseArray(isf);
//            validate(isf_v);
//            if (ic_v == null)
//                ic_v = convertToSparseArray(ic);
//            validate(ic_v);
//            if (targetLow_v == null)
//                targetLow_v = convertToSparseArray(targetLow);
//            validate(targetLow_v);
//            if (targetHigh_v == null)
//                targetHigh_v = convertToSparseArray(targetHigh);
//            validate(targetHigh_v);
//
//            if (targetHigh_v.size() != targetLow_v.size()) isValid = false;
//            else for (int i = 0; i < targetHigh_v.size(); i++)
//                if (targetHigh_v.valueAt(i) < targetLow_v.valueAt(i))
//                    isValid = false;
//
//            isValidated = true;
//        }

        if (isValid) {
            // Check for hours alignment
            PumpInterface pump = activePlugin.getActivePump();
            if (!pump.getPumpDescription().is30minBasalRatesCapable) {
                for (int index = 0; index < basal_v.size(); index++) {
                    long secondsFromMidnight = basal_v.keyAt(index);
                    if (notify && secondsFromMidnight % 3600 != 0) {
                        if (configInterface.getAPS()) {
                            Notification notification = new Notification(Notification.BASAL_PROFILE_NOT_ALIGNED_TO_HOURS, resourceHelper.gs(R.string.basalprofilenotaligned, from), Notification.NORMAL);
                            rxBus.send(new EventNewNotification(notification));
                        }
                    }
                }
            }

            // Check for minimal basal value
            PumpDescription description = pump.getPumpDescription();
            for (int i = 0; i < basal_v.size(); i++) {
                if (basal_v.valueAt(i) < description.basalMinimumRate) {
                    basal_v.setValueAt(i, description.basalMinimumRate);
                    if (notify)
                        sendBelowMinimumNotification(from);
                } else if (basal_v.valueAt(i) > description.basalMaximumRate) {
                    basal_v.setValueAt(i, description.basalMaximumRate);
                    if (notify)
                        sendAboveMaximumNotification(from);
                }
            }

        }
        return isValid;
    }
}
