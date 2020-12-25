package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseResultActivity;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType;

/**
 * Created by Dirceu on 25/09/20.
 */
public class MedLinkPumpMessage //implements RLMessage
{
    public MedLinkCommandType commandType;
    private MedLinkCommandType argument;
    protected BaseResultActivity baseResultActivity;


    private AAPSLogger aapsLogger;

    public MedLinkPumpMessage(AAPSLogger aapsLogger, MedLinkCommandType commandType) {
        this.aapsLogger = aapsLogger;
        this.commandType = commandType;
    }

    public MedLinkPumpMessage(AAPSLogger aapsLogger, MedLinkCommandType commandType, MedLinkCommandType argument, BaseResultActivity baseResultActivity) {
        this.aapsLogger = aapsLogger;
        this.argument = argument;
        this.commandType = commandType;
        this.baseResultActivity = baseResultActivity;
    }


    public byte[] getCommandData() {
        return commandType.getRaw();
    }


    public byte[] getArgumentData() {
        if (argument != null) {
            return argument.getRaw();
        } else {
            return new byte[0];
        }

    }

    public BaseResultActivity getBaseResultActivity() {
        return baseResultActivity;
    }

//    @Override public String toString() {
//        return "MedLinkMessage{" +
//                "commandType=" + commandType +
//                ", messageType=" + messageType +
//                ", messageBody=" + messageBody +
//                '}';
//    }
}
