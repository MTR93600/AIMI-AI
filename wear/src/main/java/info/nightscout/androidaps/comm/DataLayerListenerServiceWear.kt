package info.nightscout.androidaps.comm

import android.app.NotificationManager
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import dagger.android.AndroidInjection
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventWearToMobile
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.androidaps.interaction.utils.WearUtil
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.EventData
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DataLayerListenerServiceWear : WearableListenerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var wearUtil: WearUtil
    @Inject lateinit var persistence: Persistence
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    //private val nodeClient by lazy { Wearable.getNodeClient(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val disposable = CompositeDisposable()

    private val rxPath get() = getString(R.string.path_rx_bridge)

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        handler.post { updateTranscriptionCapability() }
        disposable += rxBus
            .toObservable(EventWearToMobile::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                sendMessage(rxPath, it.payload.serialize())
            }
    }

    override fun onPeerConnected(p0: Node) {
        super.onPeerConnected(p0)
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        super.onCapabilityChanged(p0)
        handler.post { updateTranscriptionCapability() }
        aapsLogger.debug(LTag.WEAR, "onCapabilityChanged:  ${p0.name} ${p0.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        disposable.clear()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        //aapsLogger.debug(LTag.WEAR, "onDataChanged")

        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path

                aapsLogger.debug(LTag.WEAR, "onDataChanged: Path: $path, EventDataItem=${event.dataItem}")
                try {
                    @Suppress("ControlFlowWithEmptyBody", "UNUSED_EXPRESSION")
                    when (path) {
                    }
                } catch (exception: Exception) {
                    aapsLogger.error(LTag.WEAR, "onDataChanged failed", exception)
                }
            }
        }
        super.onDataChanged(dataEvents)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        when (messageEvent.path) {
            rxPath -> {
                aapsLogger.debug(LTag.WEAR, "onMessageReceived: ${String(messageEvent.data)}")
                val command = EventData.deserialize(String(messageEvent.data))
                rxBus.send(command.also { it.sourceNodeId = messageEvent.sourceNodeId })
                // Use this sender
                transcriptionNodeId = messageEvent.sourceNodeId
                aapsLogger.debug(LTag.WEAR, "Updated node: $transcriptionNodeId")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            INTENT_CANCEL_BOLUS        -> {
                //dismiss notification
                NotificationManagerCompat.from(this).cancel(BOLUS_PROGRESS_NOTIF_ID)
                //send cancel-request to phone.
                rxBus.send(EventWearToMobile(EventData.CancelBolus(System.currentTimeMillis())))
            }

            INTENT_WEAR_TO_MOBILE      -> sendMessage(rxPath, intent.extras?.getString(KEY_ACTION_DATA))
            INTENT_CANCEL_NOTIFICATION -> (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(CHANGE_NOTIF_ID)
        }
        return START_STICKY
    }

    private var transcriptionNodeId: String? = null

    private fun updateTranscriptionCapability() {
        val capabilityInfo: CapabilityInfo = Tasks.await(
            capabilityClient.getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        )
        aapsLogger.debug(LTag.WEAR, "Nodes: ${capabilityInfo.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
        pickBestNodeId(capabilityInfo.nodes)?.let { transcriptionNodeId = it }
        aapsLogger.debug(LTag.WEAR, "Selected node: $transcriptionNodeId")
    }

    // Find a nearby node or pick one arbitrarily
    private fun pickBestNodeId(nodes: Set<Node>): String? =
        nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id

    @Suppress("unused")
    private fun sendData(path: String, vararg params: DataMap) {
        scope.launch {
            try {
                for (dm in params) {
                    val request = PutDataMapRequest.create(path).apply {
                        dataMap.putAll(dm)
                    }
                        .asPutDataRequest()
                        .setUrgent()

                    val result = dataClient.putDataItem(request).await()
                    aapsLogger.debug(LTag.WEAR, "sendData: ${result.uri} ${params.joinToString()}")
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                aapsLogger.error(LTag.WEAR, "DataItem failed: $exception")
            }
        }
    }

    private fun sendMessage(path: String, data: String?) {
        transcriptionNodeId?.also { nodeId ->
            aapsLogger.debug(LTag.WEAR, "sendMessage: $path $data")
            messageClient
                .sendMessage(nodeId, path, data?.toByteArray() ?: byteArrayOf()).apply {
                    addOnSuccessListener { }
                    addOnFailureListener {
                        aapsLogger.debug(LTag.WEAR, "sendMessage:  $path failure $it")
                    }
                }
        } ?: aapsLogger.debug(LTag.WEAR, "sendMessage: Ignoring message. No node selected.")
    }

    @Suppress("unused")
    private fun sendMessage(path: String, data: ByteArray) {
        aapsLogger.debug(LTag.WEAR, "sendMessage: $path")
        transcriptionNodeId?.also { nodeId ->
            messageClient
                .sendMessage(nodeId, path, data).apply {
                    addOnSuccessListener { }
                    addOnFailureListener {
                        aapsLogger.debug(LTag.WEAR, "sendMessage:  $path failure")
                    }
                }
        }
    }

    companion object {

        const val PHONE_CAPABILITY = "androidaps_mobile"

        // Accepted intents
        val INTENT_NEW_DATA = DataLayerListenerServiceWear::class.java.name + ".NewData"
        val INTENT_CANCEL_BOLUS = DataLayerListenerServiceWear::class.java.name + ".CancelBolus"
        val INTENT_WEAR_TO_MOBILE = DataLayerListenerServiceWear::class.java.name + ".WearToMobile"
        val INTENT_CANCEL_NOTIFICATION = DataLayerListenerServiceWear::class.java.name + ".CancelNotification"

        //data keys
        const val KEY_ACTION_DATA = "actionData"
        const val KEY_ACTION = "action"
        const val KEY_MESSAGE = "message"
        const val KEY_TITLE = "title"

        const val BOLUS_PROGRESS_NOTIF_ID = 1
        const val CONFIRM_NOTIF_ID = 2
        const val CHANGE_NOTIF_ID = 556677

        const val AAPS_NOTIFY_CHANNEL_ID_OPEN_LOOP = "AndroidAPS-OpenLoop"
        const val AAPS_NOTIFY_CHANNEL_ID_BOLUS_PROGRESS = "bolus progress vibration"
        const val AAPS_NOTIFY_CHANNEL_ID_BOLUS_PROGRESS_SILENT = "bolus progress silent"
    }
}