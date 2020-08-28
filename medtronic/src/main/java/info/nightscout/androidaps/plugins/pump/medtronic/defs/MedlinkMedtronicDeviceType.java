package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import java.util.HashMap;
import java.util.Map;

/**
 * Taken from MedtronicDeviceType
 * <p>
 * Author: Dirceu Semighini Filho
 */

public enum MedlinkMedtronicDeviceType {
    Unknown_Device, //

    // Pump
    Medtronic_511("511"), //

    Medtronic_512("512"), //
    Medtronic_712("712"), //
    Medtronic_512_712(Medtronic_512, Medtronic_712), //

    Medtronic_515("515"), //
    Medtronic_715("715"), //
    Medtronic_515_715(Medtronic_515, Medtronic_715), //

    Medtronic_522("522"), //
    Medtronic_722("722"), //
    Medtronic_522_722(Medtronic_522, Medtronic_722), //

    Medtronic_523_Revel("523"), //
    Medtronic_723_Revel("723"), //

    Medtronic_554_Veo("554"), //
    Medtronic_754_Veo("754"), //

    Medtronic_512andHigher(Medtronic_512, Medtronic_712, Medtronic_515, Medtronic_715, Medtronic_522, Medtronic_722, //
            Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, Medtronic_754_Veo), //

    Medtronic_515andHigher(Medtronic_515, Medtronic_715, Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, //
            Medtronic_554_Veo, Medtronic_754_Veo), //
    Medtronic_522andHigher(Medtronic_522, Medtronic_722, Medtronic_523_Revel, Medtronic_723_Revel, //
            Medtronic_554_Veo, Medtronic_754_Veo), //
    Medtronic_523andHigher(Medtronic_523_Revel, Medtronic_723_Revel, Medtronic_554_Veo, //
            Medtronic_754_Veo), //

    Medtronic_554andHigher(Medtronic_554_Veo, Medtronic_754_Veo), //


    //
    All;

    static Map<String, MedlinkMedtronicDeviceType> mapByDescription;

    static {

        mapByDescription = new HashMap<>();

        for (MedlinkMedtronicDeviceType minimedDeviceType : values()) {

            if (!minimedDeviceType.isFamily) {
                mapByDescription.put(minimedDeviceType.pumpModel, minimedDeviceType);
            }
        }

    }

    private String pumpModel;

    private boolean isFamily;
    private MedlinkMedtronicDeviceType[] familyMembers = null;


    MedlinkMedtronicDeviceType(String pumpModel) {
        this.isFamily = false;
        this.pumpModel = pumpModel;
    }


    MedlinkMedtronicDeviceType(MedlinkMedtronicDeviceType... familyMembers) {
        this.familyMembers = familyMembers;
        this.isFamily = true;
    }


    public static boolean isSameDevice(MedlinkMedtronicDeviceType deviceWeCheck, MedlinkMedtronicDeviceType deviceSources) {
        if (deviceSources.isFamily) {
            for (MedlinkMedtronicDeviceType mdt : deviceSources.familyMembers) {
                if (mdt == deviceWeCheck)
                    return true;
            }
        } else {
            return (deviceWeCheck == deviceSources);
        }

        return false;
    }


    public static MedlinkMedtronicDeviceType getByDescription(String desc) {
        if (mapByDescription.containsKey(desc)) {
            return mapByDescription.get(desc);
        } else {
            return MedlinkMedtronicDeviceType.Unknown_Device;
        }
    }


//    public static boolean isLargerFormat(MedtronicDeviceType model) {
//        return isSameDevice(model, Medtronic_523andHigher);
//    }


    public boolean isFamily() {
        return isFamily;
    }


    public MedlinkMedtronicDeviceType[] getFamilyMembers() {
        return familyMembers;
    }


//    public boolean isLargerFormat() {
//        return isSameDevice(this, Medtronic_523andHigher);
//    }

    public boolean isMedtronic_523orHigher() {
        return isSameDevice(this, Medtronic_523andHigher);
    }


    public int getBolusStrokes() {
        return (isMedtronic_523orHigher()) ? 40 : 10;
    }


    public String getPumpModel() {
        return pumpModel;
    }
}
