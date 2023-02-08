package info.nightscout.androidaps.plugins.pump.common.hw.medlink;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.data.MLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6b;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;

/**
 * Created by dirceu on 9/17/20.
 * copied from RileyLinkUtil
 */
@Singleton
public class MedLinkUtil  {

    private List<MLHistoryItem> historyMedLink = new ArrayList<>();
    private ServiceTask currentTask;

    private MedLinkEncodingType encoding = MedLinkEncodingType.FourByteSixByteLocal;
    private Encoding4b6b encoding4b6b;

    // TODO maybe not needed
    private RileyLinkTargetFrequency rileyLinkTargetFrequency;

    @Inject
    public MedLinkUtil() {
    }

    public MedLinkEncodingType getEncoding() {
        return encoding;
    }

    public void setEncoding(MedLinkEncodingType encoding) {
        this.setEncoding(encoding);
    }

    public void sendBroadcastMessage(Intent message, Context context) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(message);
    }

    public void sendBroadcastMessage(String message, Context context) {
        Intent intent = new Intent(message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    // FIXME remove ?
    public void setCurrentTask(ServiceTask task) {
        if (currentTask == null) {
            currentTask = task;
        } else {
            //LOG.error("setCurrentTask: Cannot replace current task");
        }
    }

//    @Override public void finishCurrentTask(ServiceTask task) {
//        if (task != currentTask) {
//            //LOG.error("finishCurrentTask: task does not match");
//        }
//        // hack to force deep copy of transport contents
//        ServiceTransport transport = task.getServiceTransport().clone();
//
//        if (transport.hasServiceResult()) {
//            sendServiceTransportResponse(transport, transport.getServiceResult());
//        }
//        currentTask = null;
//    }

//    private static void sendServiceTransportResponse(ServiceTransport transport, ServiceResult serviceResult) {
//        // get the key (hashcode) of the client who requested this
//        Integer clientHashcode = transport.getSenderHashcode();
//        // make a new bundle to send as the message data
//        transport.setServiceResult(serviceResult);
//        // FIXME
//        // transport.setTransportType(RT2Const.IPC.MSG_ServiceResult);
//        // rileyLinkIPCConnection.sendTransport(transport, clientHashcode);
//    }

    public List<MLHistoryItem> getRileyLinkHistory() {
        return historyMedLink;
    }

    public List<MLHistoryItem> getMedLinkHistory() {
        return historyMedLink;
    }

    public Encoding4b6b getEncoding4b6b() {
        return encoding4b6b;
    }

    public void setRileyLinkTargetFrequency(RileyLinkTargetFrequency rileyLinkTargetFrequency_) {
        this.rileyLinkTargetFrequency = rileyLinkTargetFrequency_;
    }
}

