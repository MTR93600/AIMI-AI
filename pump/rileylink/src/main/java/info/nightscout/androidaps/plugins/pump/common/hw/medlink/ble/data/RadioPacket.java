package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.CRC;
import info.nightscout.pump.core.utils.ByteUtil;

/**
 * Created by dirceu on 10/12/20.
 * copied from rileylink RadioPacket
 */

public class RadioPacket {

    @Inject MedLinkUtil medLinkUtil;

    private byte[] pkt;


    public RadioPacket(HasAndroidInjector injector, byte[] pkt) {
        injector.androidInjector().inject(this);
        this.pkt = pkt;
    }


    public byte[] getRaw() {
        return pkt;
    }


    private byte[] getWithCRC() {
        byte[] withCRC = ByteUtil.concat(pkt, CRC.crc8(pkt));
        return withCRC;
    }


    public byte[] getEncoded() {
//        return null;
//        switch ((MedLinkEncodingType)medLinkUtil.getEncoding()) {
//            case Manchester: { // We have this encoding in RL firmware
//                return pkt;
//            }

//            case FourByteSixByteLocal: {
//                byte[] withCRC = getWithCRC();
//
//                byte[] encoded = medLinkUtil.getEncoding4b6b().encode4b6b(withCRC);
//                return ByteUtil.concat(encoded, (byte) 0);
//            }
        return pkt;

//            default:
//                throw new NotImplementedException(("Encoding not supported: " + medLinkUtil.getEncoding().toString()));

    }
}
