package info.nightscout.androidaps.plugins.pump.medtronic.driver;

import org.jetbrains.annotations.NotNull;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.data.MLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.interfaces.pump.defs.PumpType;
import info.nightscout.pump.common.data.MedLinkPumpStatus;
import info.nightscout.pump.core.defs.PumpDeviceState;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.shared.interfaces.ResourceHelper;
import info.nightscout.shared.sharedPreferences.SP;


/**
 * Created by dirceu on 25/09/20.
 * copied from {@link MedtronicPumpStatus}
 */

@Singleton
public class MedLinkMedtronicPumpStatus extends MedLinkPumpStatus {


    private final ResourceHelper resourceHelper;
    private final SP sp;
    private final MedLinkUtil medLinkUtil;
    private final RxBus rxBus;

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
                                      RxBus rxBus,
                                      MedLinkUtil medLinkUtil
    ) {
        super(PumpType.MEDLINK_MEDTRONIC_523_723_REVEL);
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.rxBus = rxBus;
        this.medLinkUtil = medLinkUtil;
        initSettings();
    }


    public void initSettings() {

        this.setActiveProfileName("STD");
        this.setReservoirRemainingUnits(75d);
        this.setReservoirFullUnits(300);
        this.setBatteryRemaining(75);

        if (this.medtronicPumpMap == null)
            createMedtronicPumpMap();

        if (this.medtronicDeviceTypeMap == null)
            createMedtronicDeviceTypeMap();

        this.setLastConnection(sp.getLong(MedtronicConst.Statistics.LastGoodPumpCommunicationTime
                , 0L));
        this.setLastDataTime(this.getLastConnection());
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
        medtronicPumpMap.put("523", PumpType.MEDLINK_MEDTRONIC_523_723_REVEL);
        medtronicPumpMap.put("723", PumpType.MEDLINK_MEDTRONIC_523_723_REVEL);
        medtronicPumpMap.put("554", PumpType.MEDLINK_MEDTRONIC_554_754_VEO);
        medtronicPumpMap.put("754", PumpType.MEDLINK_MEDTRONIC_554_754_VEO);

    }

    public Map<String, PumpType> getMedtronicPumpMap() {
        return medtronicPumpMap;
    }

    public Map<String, MedLinkMedtronicDeviceType> getMedtronicDeviceTypeMap() {
        return medtronicDeviceTypeMap;
    }

    public double getBasalProfileForHour() {
        if (getBasalsByHour() != null) {
            GregorianCalendar c = new GregorianCalendar();
            int hour = c.get(Calendar.HOUR_OF_DAY);

            return getBasalsByHour()[hour];
        }

        return 0;
    }

    public double getCurrentBasal() {
        return super.currentBasal;
    }
    // Battery type
    private Map<String, BatteryType> mapByDescription;

    public BatteryType getBatteryTypeByDescription(String batteryTypeStr) {
        if (mapByDescription == null) {
            mapByDescription = new HashMap<>();
            for (BatteryType value : BatteryType.values()) {
                mapByDescription.put(resourceHelper.gs(value.getDescription()), value);
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

    public boolean needToGetBGHistory(){
        return getLastDataTime() - lastBGTimestamp > 360000;
    }

}
