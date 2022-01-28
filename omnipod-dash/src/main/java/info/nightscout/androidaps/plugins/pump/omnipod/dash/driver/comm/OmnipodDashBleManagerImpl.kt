package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import info.nightscout.androidaps.extensions.toHex
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.BuildConfig
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair.LTKExchanger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.PodScanner
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import io.reactivex.Observable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class OmnipodDashBleManagerImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val podState: OmnipodDashPodStateManager
) : OmnipodDashBleManager {

    private val busy = AtomicBoolean(false)
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var connection: Connection? = null
    private val ids = Ids(podState)

    override fun sendCommand(cmd: Command, responseType: KClass<out Response>): Observable<PodEvent> =
        Observable.create { emitter ->
            if (!busy.compareAndSet(false, true)) {
                throw BusyException()
            }
            try {
                val session = assertSessionEstablished()

                emitter.onNext(PodEvent.CommandSending(cmd))
            /*
                if (Random.nextBoolean()) {
                    // XXX use this to test "failed to confirm" commands
                    emitter.onNext(PodEvent.CommandSendNotConfirmed(cmd))
                    emitter.tryOnError(MessageIOException("XXX random failure to test unconfirmed commands"))
                    return@create
                }
*/
                when (session.sendCommand(cmd)) {
                    is CommandSendErrorSending -> {
                        emitter.tryOnError(CouldNotSendCommandException())
                        return@create
                    }

                    is CommandSendSuccess ->
                        emitter.onNext(PodEvent.CommandSent(cmd))
                    is CommandSendErrorConfirming ->
                        emitter.onNext(PodEvent.CommandSendNotConfirmed(cmd))
                }
                /*
                if (Random.nextBoolean()) {
                    // XXX use this commands confirmed with success
                    emitter.tryOnError(MessageIOException("XXX random failure to test unconfirmed commands"))
                    return@create
                }*/
                when (val readResult = session.readAndAckResponse()) {
                    is CommandReceiveSuccess ->
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))

                    is CommandAckError ->
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))

                    is CommandReceiveError -> {
                        emitter.tryOnError(MessageIOException("Could not read response: $readResult"))
                        return@create
                    }
                }
                emitter.onComplete()
            } catch (ex: Exception) {
                disconnect(false)
                emitter.tryOnError(ex)
            } finally {
                busy.set(false)
            }
        }

    private fun assertSessionEstablished(): Session {
        val conn = assertConnected()
        return conn.session
            ?: throw NotConnectedException("Missing session")
    }

    override fun getStatus(): ConnectionState {
        return connection?.connectionState()
            ?: NotConnected
    }
    // used for sync connections
    override fun connect(timeoutMs: Long): Observable<PodEvent> {
        return connect(ConnectionWaitCondition(timeoutMs = timeoutMs))
    }

    // used for async connections
    override fun connect(stopConnectionLatch: CountDownLatch): Observable<PodEvent> {
        return connect(ConnectionWaitCondition(stopConnection = stopConnectionLatch))
    }

    private fun connect(connectionWaitCond: ConnectionWaitCondition): Observable<PodEvent> = Observable
        .create {
            emitter ->
            if (!busy.compareAndSet(false, true)) {
                throw BusyException()
            }
            try {
                emitter.onNext(PodEvent.BluetoothConnecting)

                val podAddress =
                    podState.bluetoothAddress
                        ?: throw FailedToConnectException("Missing bluetoothAddress, activate the pod first")
                val podDevice = bluetoothAdapter?.getRemoteDevice(podAddress)
                    ?: throw ConnectException("Bluetooth not available")
                val conn = connection
                    ?: Connection(podDevice, aapsLogger, context, podState)
                connection = conn
                if (conn.connectionState() is Connected && conn.session != null) {
                    emitter.onNext(PodEvent.AlreadyConnected(podAddress))
                    emitter.onComplete()
                    return@create
                }

                conn.connect(connectionWaitCond)

                emitter.onNext(PodEvent.BluetoothConnected(podAddress))
                emitter.onNext(PodEvent.EstablishingSession)
                establishSession(1.toByte())
                emitter.onNext(PodEvent.Connected)

                emitter.onComplete()
            } catch (ex: Exception) {
                disconnect(false)
                emitter.tryOnError(ex)
            } finally {
                busy.set(false)
            }
        }

    private fun establishSession(msgSeq: Byte) {
        val conn = assertConnected()

        val ltk = assertPaired()

        val eapSqn = podState.increaseEapAkaSequenceNumber()

        var newSqn = conn.establishSession(ltk, msgSeq, ids, eapSqn)

        if (newSqn != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Updating EAP SQN to: $newSqn")
            podState.eapAkaSequenceNumber = newSqn.toLong()
            newSqn = conn.establishSession(ltk, msgSeq, ids, podState.increaseEapAkaSequenceNumber())
            if (newSqn != null) {
                throw SessionEstablishmentException("Received resynchronization SQN for the second time")
            }
        }
        podState.successfulConnections++
        podState.commitEapAkaSequenceNumber()
    }

    private fun assertPaired(): ByteArray {
        return podState.ltk
            ?: throw FailedToConnectException("Missing LTK, activate the pod first")
    }

    private fun assertConnected(): Connection {
        return connection
            ?: throw FailedToConnectException("connection lost")
    }

    override fun pairNewPod(): Observable<PodEvent> = Observable.create { emitter ->
        if (!busy.compareAndSet(false, true)) {
            throw BusyException()
        }
        try {
            if (podState.ltk != null) {
                emitter.onNext(PodEvent.AlreadyPaired)
                emitter.onComplete()
                return@create
            }
            aapsLogger.info(LTag.PUMPBTCOMM, "Starting new pod activation")

            emitter.onNext(PodEvent.Scanning)
            val adapter = bluetoothAdapter
                ?: throw ConnectException("Bluetooth not available")
            val podScanner = PodScanner(aapsLogger, adapter)
            val podAddress = podScanner.scanForPod(
                PodScanner.SCAN_FOR_SERVICE_UUID,
                PodScanner.POD_ID_NOT_ACTIVATED
            ).scanResult.device.address
            podState.bluetoothAddress = podAddress

            emitter.onNext(PodEvent.BluetoothConnecting)
            val podDevice = adapter.getRemoteDevice(podAddress)
            val conn = Connection(podDevice, aapsLogger, context, podState)
            connection = conn
            conn.connect(ConnectionWaitCondition(timeoutMs = 3 * Connection.BASE_CONNECT_TIMEOUT_MS))
            emitter.onNext(PodEvent.BluetoothConnected(podAddress))

            emitter.onNext(PodEvent.Pairing)
            val mIO = conn.msgIO ?: throw ConnectException("Connection lost")
            val ltkExchanger = LTKExchanger(
                aapsLogger,
                mIO,
                ids,
            )
            val pairResult = ltkExchanger.negotiateLTK()
            emitter.onNext(PodEvent.Paired(ids.podId))
            podState.updateFromPairing(ids.podId, pairResult)
            if (BuildConfig.DEBUG) {
                aapsLogger.info(LTag.PUMPCOMM, "Got LTK: ${pairResult.ltk.toHex()}")
            }
            emitter.onNext(PodEvent.EstablishingSession)
            establishSession(pairResult.msgSeq)
            podState.successfulConnections++
            emitter.onNext(PodEvent.Connected)
            emitter.onComplete()
        } catch (ex: Exception) {
            disconnect(false)
            emitter.tryOnError(ex)
        } finally {
            busy.set(false)
        }
    }

    override fun disconnect(closeGatt: Boolean) {
        connection?.disconnect(closeGatt)
            ?: aapsLogger.info(LTag.PUMPBTCOMM, "Trying to disconnect a null connection")
    }

    companion object {
        const val CONTROLLER_ID = 4242 // TODO read from preferences or somewhere else.
    }
}
