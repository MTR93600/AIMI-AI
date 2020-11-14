package info.nightscout.androidaps.plugins.pump.common.hw.medlink;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.common.hw.connector.ConnectorUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.connector.data.HistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.connector.defs.CommunicatorEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.data.MLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6b;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceResult;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;

/**
 * Created by dirceu on 9/17/20.
 * copied from RileyLinkUtil
 */
@Singleton
public class MedLinkUtil implements ConnectorUtil {

    private List<MLHistoryItem> historyMedLink = new ArrayList<>();
    private ServiceTask currentTask;

    private CommunicatorEncodingType encoding = MedLinkEncodingType.FourByteSixByteLocal;
    private Encoding4b6b encoding4b6b;

    // TODO maybe not needed
    private RileyLinkTargetFrequency rileyLinkTargetFrequency;

    @Inject
    public MedLinkUtil() {
    }

    @Override public CommunicatorEncodingType getEncoding() {
        return encoding;
    }

    @Override public void setEncoding(CommunicatorEncodingType encoding) {
        this.setEncoding(encoding);
    }

    @Override public void sendBroadcastMessage(String message, Context context) {
        Intent intent = new Intent(message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    // FIXME remove ?
    @Override public void setCurrentTask(ServiceTask task) {
        if (currentTask == null) {
            currentTask = task;
        } else {
            //LOG.error("setCurrentTask: Cannot replace current task");
        }
    }

    @Override public void finishCurrentTask(ServiceTask task) {
        if (task != currentTask) {
            //LOG.error("finishCurrentTask: task does not match");
        }
        // hack to force deep copy of transport contents
        ServiceTransport transport = task.getServiceTransport().clone();

        if (transport.hasServiceResult()) {
            sendServiceTransportResponse(transport, transport.getServiceResult());
        }
        currentTask = null;
    }

    private static void sendServiceTransportResponse(ServiceTransport transport, ServiceResult serviceResult) {
        // get the key (hashcode) of the client who requested this
        Integer clientHashcode = transport.getSenderHashcode();
        // make a new bundle to send as the message data
        transport.setServiceResult(serviceResult);
        // FIXME
        // transport.setTransportType(RT2Const.IPC.MSG_ServiceResult);
        // rileyLinkIPCConnection.sendTransport(transport, clientHashcode);
    }

    @Override public List<MLHistoryItem> getRileyLinkHistory() {
        return historyMedLink;
    }

    @Override public Encoding4b6b getEncoding4b6b() {
        return encoding4b6b;
    }

    @Override public void setRileyLinkTargetFrequency(RileyLinkTargetFrequency rileyLinkTargetFrequency_) {
        this.rileyLinkTargetFrequency = rileyLinkTargetFrequency_;
    }
}

