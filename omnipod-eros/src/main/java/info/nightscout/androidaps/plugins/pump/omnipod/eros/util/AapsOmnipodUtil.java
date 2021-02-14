package info.nightscout.androidaps.plugins.pump.omnipod.eros.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.R;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.PodStateManager;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by andy on 4/8/19.
 */
@Singleton
public class AapsOmnipodUtil {

    private final ResourceHelper resourceHelper;

    private final Gson gsonInstance = createGson();

    @Inject
    public AapsOmnipodUtil(ResourceHelper resourceHelper) {
        this.resourceHelper = resourceHelper;
    }

    private Gson createGson() {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(DateTime.class, (JsonSerializer<DateTime>) (dateTime, typeOfSrc, context) ->
                        new JsonPrimitive(ISODateTimeFormat.dateTime().print(dateTime)))
                .registerTypeAdapter(DateTime.class, (JsonDeserializer<DateTime>) (json, typeOfT, context) ->
                        ISODateTimeFormat.dateTime().parseDateTime(json.getAsString()))
                .registerTypeAdapter(DateTimeZone.class, (JsonSerializer<DateTimeZone>) (timeZone, typeOfSrc, context) ->
                        new JsonPrimitive(timeZone.getID()))
                .registerTypeAdapter(DateTimeZone.class, (JsonDeserializer<DateTimeZone>) (json, typeOfT, context) ->
                        DateTimeZone.forID(json.getAsString()));

        return gsonBuilder.create();
    }

    public Gson getGsonInstance() {
        return this.gsonInstance;
    }

    public List<String> getTranslatedActiveAlerts(PodStateManager podStateManager) {
        List<String> translatedAlerts = new ArrayList<>();
        AlertSet activeAlerts = podStateManager.getActiveAlerts();

        for (AlertSlot alertSlot : activeAlerts.getAlertSlots()) {
            translatedAlerts.add(translateAlertType(podStateManager.getConfiguredAlertType(alertSlot)));
        }
        return translatedAlerts;
    }

    private String translateAlertType(AlertType alertType) {
        if (alertType == null) {
            return resourceHelper.gs(R.string.omnipod_alert_unknown_alert);
        }
        switch (alertType) {
            case FINISH_PAIRING_REMINDER:
                return resourceHelper.gs(R.string.omnipod_alert_finish_pairing_reminder);
            case FINISH_SETUP_REMINDER:
                return resourceHelper.gs(R.string.omnipod_alert_finish_setup_reminder_reminder);
            case EXPIRATION_ALERT:
                return resourceHelper.gs(R.string.omnipod_alert_expiration);
            case EXPIRATION_ADVISORY_ALERT:
                return resourceHelper.gs(R.string.omnipod_alert_expiration_advisory);
            case SHUTDOWN_IMMINENT_ALARM:
                return resourceHelper.gs(R.string.omnipod_alert_shutdown_imminent);
            case LOW_RESERVOIR_ALERT:
                return resourceHelper.gs(R.string.omnipod_alert_low_reservoir);
            default:
                return alertType.name();
        }
    }
}
