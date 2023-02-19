package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import java.util.HashMap;
import java.util.Map;

/**
 * Taken from MedtronicDeviceType
 * <p>
 * Author: Dirceu Semighini Filho

 * used by medlink
 */

public enum MedLinkMedtronicDeviceType {
    Unknown_Device, //

    // Pump
    MedLinkMedtronic_511("511"), //

    MedLinkMedtronic_512("512"), //
    MedLinkMedtronic_712("712"), //
    MedLinkMedtronic_512_712(MedLinkMedtronic_512, MedLinkMedtronic_712), //

    MedLinkMedtronic_515("515"), //
    MedLinkMedtronic_715("715"), //
    MedLinkMedtronic_515_715(MedLinkMedtronic_515, MedLinkMedtronic_715), //

    MedLinkMedtronic_522("522"), //
    MedLinkMedtronic_722("722"), //
    MedLinkMedtronic_522_722(MedLinkMedtronic_522, MedLinkMedtronic_722), //

    MedLinkMedtronic_523_Revel("523"), //
    MedLinkMedtronic_723_Revel("723"), //

    MedLinkMedtronic_554_Veo("554"), //
    MedLinkMedtronic_754_Veo("754"), //

    MedLinkMedtronic_512andHigher(MedLinkMedtronic_512, MedLinkMedtronic_712, MedLinkMedtronic_515,
            MedLinkMedtronic_715, MedLinkMedtronic_522, MedLinkMedtronic_722, //
            MedLinkMedtronic_523_Revel, MedLinkMedtronic_723_Revel,
            MedLinkMedtronic_554_Veo, MedLinkMedtronic_754_Veo), //

    MedLinkMedtronic_515andHigher(MedLinkMedtronic_515, MedLinkMedtronic_715, MedLinkMedtronic_522,
            MedLinkMedtronic_722, MedLinkMedtronic_523_Revel, MedLinkMedtronic_723_Revel, //
            MedLinkMedtronic_554_Veo, MedLinkMedtronic_754_Veo), //
    MedLinkMedtronic_522andHigher(MedLinkMedtronic_522, MedLinkMedtronic_722,
            MedLinkMedtronic_523_Revel, MedLinkMedtronic_723_Revel, //
            MedLinkMedtronic_554_Veo, MedLinkMedtronic_754_Veo), //
    MedLinkMedtronic_523andHigher(MedLinkMedtronic_523_Revel, MedLinkMedtronic_723_Revel,
            MedLinkMedtronic_554_Veo, //
            MedLinkMedtronic_754_Veo), //

    MedLinkMedtronic_554andHigher(MedLinkMedtronic_554_Veo, MedLinkMedtronic_754_Veo), //


    //
    All;

    static Map<String, MedLinkMedtronicDeviceType> mapByDescription;

    static {

        mapByDescription = new HashMap<>();

        for (MedLinkMedtronicDeviceType minimedDeviceType : values()) {

            if (!minimedDeviceType.isFamily) {
                mapByDescription.put(minimedDeviceType.pumpModel, minimedDeviceType);
            }
        }

    }

    private String pumpModel;

    private boolean isFamily;
    private MedLinkMedtronicDeviceType[] familyMembers = null;


    MedLinkMedtronicDeviceType(String pumpModel) {
        this.isFamily = false;
        this.pumpModel = pumpModel;
    }


    MedLinkMedtronicDeviceType(MedLinkMedtronicDeviceType... familyMembers) {
        this.familyMembers = familyMembers;
        this.isFamily = true;
    }


    public static boolean isSameDevice(MedLinkMedtronicDeviceType deviceWeCheck, MedLinkMedtronicDeviceType deviceSources) {
        if (deviceSources.isFamily) {
            for (MedLinkMedtronicDeviceType mdt : deviceSources.familyMembers) {
                if (mdt == deviceWeCheck)
                    return true;
            }
        } else {
            return (deviceWeCheck == deviceSources);
        }

        return false;
    }


    public static MedLinkMedtronicDeviceType getByDescription(String desc) {
        if (mapByDescription.containsKey(desc)) {
            return mapByDescription.get(desc);
        } else {
            return MedLinkMedtronicDeviceType.Unknown_Device;
        }
    }


//    public static boolean isLargerFormat(MedtronicDeviceType model) {
//        return isSameDevice(model, Medtronic_523andHigher);
//    }


    public boolean isFamily() {
        return isFamily;
    }


    public MedLinkMedtronicDeviceType[] getFamilyMembers() {
        return familyMembers;
    }


//    public boolean isLargerFormat() {
//        return isSameDevice(this, Medtronic_523andHigher);
//    }

    public boolean isMedtronic_523orHigher() {
        return isSameDevice(this, MedLinkMedtronic_523andHigher);
    }


    public int getBolusStrokes() {
        return (isMedtronic_523orHigher()) ? 40 : 10;
    }


    public String getPumpModel() {
        return pumpModel;
    }
}
