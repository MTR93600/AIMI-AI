package info.nightscout.androidaps.plugins.pump.medtronic.driver;

import org.jetbrains.annotations.NotNull;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.data.MLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


/**
 * Created by dirceu on 25/09/20.
 * copied from {@link MedtronicPumpStatus}
 */

@Singleton
public class MedLinkMedtronicPumpStatus extends info.nightscout.androidaps.plugins.pump.common.data.MedLinkPumpStatus  {


    private final ResourceHelper resourceHelper;
    private final SP sp;
    private final MedLinkUtil medLinkUtil;
    private final RxBusWrapper rxBus;

    public String errorDescription = null;
    public String serialNumber;
    public String pumpFrequency = null;
    public Double maxBolus;
    public Double maxBasal;



    // statuses
    private PumpDeviceState pumpDeviceState = PumpDeviceState.NeverContacted;
    public MedLinkMedtronicDeviceType medtronicDeviceType = null;
    public Date tempBasalStart;
    public Double tempBasalAmount = 0.0d;

    // fixme
    public Integer tempBasalLength = 0;

    private Map<String, PumpType> medtronicPumpMap = null;
    private Map<String, MedLinkMedtronicDeviceType> medtronicDeviceTypeMap = null;
    public BasalProfileStatus basalProfileStatus = BasalProfileStatus.NotInitialized;
    public BatteryType batteryType = BatteryType.None;


    @Inject
    public MedLinkMedtronicPumpStatus(ResourceHelper resourceHelper,
                                      SP sp,
                                      RxBusWrapper rxBus,
                                      MedLinkUtil medLinkUtil
    ) {
        super(PumpType.MedLink_Medtronic_523_723_Revel);
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.rxBus = rxBus;
        this.medLinkUtil = medLinkUtil;
        initSettings();
    }


    public void initSettings() {

        this.activeProfileName = "STD";
        this.reservoirRemainingUnits = 75d;
        this.reservoirFullUnits = 300;
        this.batteryRemaining = 75;

        if (this.medtronicPumpMap == null)
            createMedtronicPumpMap();

        if (this.medtronicDeviceTypeMap == null)
            createMedtronicDeviceTypeMap();

        this.lastConnection = sp.getLong(MedtronicConst.Statistics.LastGoodPumpCommunicationTime, 0L);
        this.lastDateTime = this.lastConnection;
    }


    private void createMedtronicDeviceTypeMap() {
        medtronicDeviceTypeMap = new HashMap<>();
        medtronicDeviceTypeMap.put("512", MedLinkMedtronicDeviceType.MedLinkMedtronic_512);
        medtronicDeviceTypeMap.put("712", MedLinkMedtronicDeviceType.MedLinkMedtronic_712);
        medtronicDeviceTypeMap.put("515", MedLinkMedtronicDeviceType.MedLinkMedtronic_515);
        medtronicDeviceTypeMap.put("715", MedLinkMedtronicDeviceType.MedLinkMedtronic_715);

        medtronicDeviceTypeMap.put("522", MedLinkMedtronicDeviceType.MedLinkMedtronic_522);
        medtronicDeviceTypeMap.put("722", MedLinkMedtronicDeviceType.MedLinkMedtronic_722);
        medtronicDeviceTypeMap.put("523", MedLinkMedtronicDeviceType.MedLinkMedtronic_523_Revel);
        medtronicDeviceTypeMap.put("723", MedLinkMedtronicDeviceType.MedLinkMedtronic_723_Revel);
        medtronicDeviceTypeMap.put("554", MedLinkMedtronicDeviceType.MedLinkMedtronic_554_Veo);
        medtronicDeviceTypeMap.put("754", MedLinkMedtronicDeviceType.MedLinkMedtronic_754_Veo);
    }


    private void createMedtronicPumpMap() {

        medtronicPumpMap = new HashMap<>();
        medtronicPumpMap.put("523", PumpType.MedLink_Medtronic_523_723_Revel);
        medtronicPumpMap.put("723", PumpType.MedLink_Medtronic_523_723_Revel);
        medtronicPumpMap.put("554", PumpType.MedLink_Medtronic_554_754_Veo);
        medtronicPumpMap.put("754", PumpType.MedLink_Medtronic_554_754_Veo);

    }

    public Map<String, PumpType> getMedtronicPumpMap() {
        return medtronicPumpMap;
    }

    public Map<String, MedLinkMedtronicDeviceType> getMedtronicDeviceTypeMap() {
        return medtronicDeviceTypeMap;
    }

    public double getBasalProfileForHour() {
        if (basalsByHour != null) {
            GregorianCalendar c = new GregorianCalendar();
            int hour = c.get(Calendar.HOUR_OF_DAY);

            return basalsByHour[hour];
        }

        return 0;
    }

    // Battery type
    private Map<String, BatteryType> mapByDescription;

    public BatteryType getBatteryTypeByDescription(String batteryTypeStr) {
        if (mapByDescription == null) {
            mapByDescription = new HashMap<>();
            for (BatteryType value : BatteryType.values()) {
                mapByDescription.put(resourceHelper.gs(value.description), value);
            }
        }
        if (mapByDescription.containsKey(batteryTypeStr)) {
            return mapByDescription.get(batteryTypeStr);
        }
        return BatteryType.None;
    }

    @NotNull
    public String getErrorInfo() {
        return (errorDescription == null) ? "-" : errorDescription;
    }


    public <E> E getCustomData(String key, Class<E> clazz) {
        switch(key) {
            case "SERIAL_NUMBER":
                return (E)serialNumber;

            case "PUMP_FREQUENCY":
                return (E)pumpFrequency;

            case "PUMP_MODEL": {
                if (medtronicDeviceType==null)
                    return null;
                else
                    return (E)medtronicDeviceType.getPumpModel();
            }


            default:
                return null;
        }
    }

    public PumpDeviceState getPumpDeviceState() {
        return pumpDeviceState;
    }


    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.pumpDeviceState = pumpDeviceState;

        medLinkUtil.getRileyLinkHistory().add(new MLHistoryItem(pumpDeviceState, RileyLinkTargetDevice.MedtronicPump));

        rxBus.send(new EventRileyLinkDeviceStatusChange(pumpDeviceState));
    }

    public boolean needToGetBGHdistory(){
        long validBG = lastBGTimestamp + 5390000l;
        return lastDateTime > (validBG) || System.currentTimeMillis() > validBG;
    }
}
