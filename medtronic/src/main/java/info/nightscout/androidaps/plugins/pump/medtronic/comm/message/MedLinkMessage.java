package info.nightscout.androidaps.plugins.pump.medtronic.comm.message;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicCommandType;

/**
 * Created by Dirceu on 25/09/20.
 */
public class MedLinkMessage {
    public MedLinkMedtronicCommandType commandType;

    public MedLinkMessage(AAPSLogger aapsLogger, String s) {

    }

    public MedLinkMessage(AAPSLogger aapsLogger) {

    }

    public RLMessage toRileyMessage() {
        return null;
    }

    public boolean isValid() {
        return false;
    }

    public byte[] getRawContent() {
        return new byte[0];
    }

    public byte[] getRawContentOfFrame() {
        return new byte[0];
    }

    public MessageBody getMessageBody() {
        return null;
    }

    public void init(PacketType carelink, byte[] pumpIDBytes, MedLinkMedtronicCommandType messageType, MessageBody messageBody) {

    }

    public Object getResponseContent() {
        return null;
    }
}
