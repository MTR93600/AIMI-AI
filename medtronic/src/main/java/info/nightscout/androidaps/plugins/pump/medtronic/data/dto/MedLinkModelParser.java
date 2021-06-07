package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicDeviceType;

/**
 * Created by Dirceu on 03/01/21.
 */
public class MedLinkModelParser {


    public static MedLinkStandardReturn<MedLinkMedtronicDeviceType> parse(Supplier<Stream<String>> connection) {
        Optional<String> modelOpt = connection.get().filter(f -> f.startsWith("medtronic")).findFirst();
        if (modelOpt.isPresent()) {
            if (modelOpt.get().equals("medtronic veo")) {
                return new MedLinkStandardReturn<>(connection, MedLinkMedtronicDeviceType.MedLinkMedtronic_554_Veo);
            } else if (modelOpt.get().equals("medtronic 722")) {
                return new MedLinkStandardReturn<>(connection, MedLinkMedtronicDeviceType.MedLinkMedtronic_512_712);
            }
        } else {
            return new MedLinkStandardReturn<>(connection, MedLinkMedtronicDeviceType.Unknown_Device, MedLinkStandardReturn.ParsingError.ModelParsingError);
        }
        return null;
    }
}
