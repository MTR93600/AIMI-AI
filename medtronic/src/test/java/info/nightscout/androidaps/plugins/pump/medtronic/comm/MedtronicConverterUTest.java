package info.nightscout.androidaps.plugins.pump.medtronic.comm;

import org.junit.Ignore;

/**
 * Created by andy on 1/30/19.
 */
@Ignore
public class MedtronicConverterUTest {
/*
    MedtronicConverter converter = new MedtronicConverter();


    // 00 03 00 05 01 00 C8 00 A0 01 01 00 01 00 00 64 01 05 00 14 00 64 01 00 00
    //@Test
    public void testDecoding554() {
        byte[] data = ByteUtil
            .createByteArrayFromString("00 03 00 05 01 00 C8 00 A0 01 01 00 01 00 00 64 01 05 00 14 00 64 01 00 00");

        MedtronicUtil.setMedtronicPumpModel(MedtronicDeviceType.Medtronic_554_Veo);

        Map<String, PumpSettingDTO> settings = (Map<String, PumpSettingDTO>)converter.convertResponse(
            MedtronicCommandType.Settings, data);

        for (PumpSettingDTO pumpSettingDTO : settings.values()) {
            System.out.println("" + pumpSettingDTO.key + " = " + pumpSettingDTO.value);
        }

        // byte[] data = new byte[] { 00 03 00 05 01 00 C8 00 A0 01 01 00 01 00 00 64 01 05 00 14 00 64 01 00 00 };
    }
*/
}
