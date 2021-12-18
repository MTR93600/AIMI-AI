package info.nightscout.androidaps.queue

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP

class QueueThread internal constructor(
    private val queue: CommandQueue,
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val activePlugin: ActivePlugin,
    private val rh: ResourceHelper,
    private val sp: SP
) : Thread() {

    private var connectLogged = false
    var waitingForDisconnect = false
    private var mWakeLock: PowerManager.WakeLock? = null

    init {
        mWakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, rh.gs(R.string.app_name) + ":QueueThread")
    }

    override fun run() {
        mWakeLock?.acquire(T.mins(10).msecs())
        rxBus.send(EventQueueChanged())
        var lastCommandTime: Long
        lastCommandTime = System.currentTimeMillis()
        var connectionStartTime = lastCommandTime
        try {
            while (true) {
                val secondsElapsed = (System.currentTimeMillis() - connectionStartTime) / 1000
                val pump = activePlugin.activePump
                if (!pump.isConnected() && secondsElapsed > Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS) {
                    rxBus.send(EventDismissBolusProgressIfRunning(null, null))
                    rxBus.send(EventPumpStatusChanged(rh.gs(R.string.connectiontimedout)))
                    aapsLogger.debug(LTag.PUMPQUEUE, "timed out")
                    pump.stopConnecting()

                    //BLUETOOTH-WATCHDOG
                    var watchdog = sp.getBoolean(R.string.key_btwatchdog, false)
                    val lastWatchdog = sp.getLong(R.string.key_btwatchdog_lastbark, 0L)
                    watchdog = watchdog && System.currentTimeMillis() - lastWatchdog > Constants.MIN_WATCHDOG_INTERVAL_IN_SECONDS * 1000
                    if (watchdog) {
                        aapsLogger.debug(LTag.PUMPQUEUE, "BT watchdog - toggling the phone bluetooth")
                        //write time
                        sp.putLong(R.string.key_btwatchdog_lastbark, System.currentTimeMillis())
                        //toggle BT
                        pump.disconnect("watchdog")
                        SystemClock.sleep(1000)
                        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.let { bluetoothAdapter ->
                            bluetoothAdapter.disable()
                            SystemClock.sleep(1000)
                            bluetoothAdapter.enable()
                            SystemClock.sleep(1000)
                        }
                        //start over again once after watchdog barked
                        //Notification notification = new Notification(Notification.OLD_NSCLIENT, "Watchdog", Notification.URGENT);
                        //rxBus.send(new EventNewNotification(notification));
                        lastCommandTime = System.currentTimeMillis()
                        connectionStartTime = lastCommandTime
                        pump.connect("watchdog")
                    } else {
                        queue.clear()
                        aapsLogger.debug(LTag.PUMPQUEUE, "no connection possible")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
                        pump.disconnect("Queue empty")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                        return
                    }
                }
                if (pump.isHandshakeInProgress()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "handshaking $secondsElapsed")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.HANDSHAKING, secondsElapsed.toInt()))
                    SystemClock.sleep(100)
                    continue
                }
                if (pump.isConnecting()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connecting $secondsElapsed")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    SystemClock.sleep(1000)
                    continue
                }
                if (!pump.isConnected()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connect")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    pump.connect("Connection needed")
                    SystemClock.sleep(1000)
                    continue
                }
                if (queue.performing() == null) {
                    if (!connectLogged) {
                        connectLogged = true
                        aapsLogger.debug(LTag.PUMPQUEUE, "connection time " + secondsElapsed + "s")
                    }
                    // Pickup 1st command and set performing variable
                    if (queue.size() > 0) {
                        queue.pickup()
                        val cont = queue.performing()?.let {
                            aapsLogger.debug(LTag.PUMPQUEUE, "performing " + it.log())
                            rxBus.send(EventQueueChanged())
                            rxBus.send(EventPumpStatusChanged(it.status()))
                            it.execute()
                            queue.resetPerforming()
                            rxBus.send(EventQueueChanged())
                            lastCommandTime = System.currentTimeMillis()
                            SystemClock.sleep(100)
                            true
                        } ?: false
                        if (cont) {
                            continue
                        }
                    }
                }
                if (queue.size() == 0 && queue.performing() == null) {
                    val secondsFromLastCommand = (System.currentTimeMillis() - lastCommandTime) / 1000
                    if (secondsFromLastCommand >= pump.waitForDisconnectionInSeconds()) {
                        waitingForDisconnect = true
                        aapsLogger.debug(LTag.PUMPQUEUE, "queue empty. disconnect")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
                        pump.disconnect("Queue empty")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                        aapsLogger.debug(LTag.PUMPQUEUE, "disconnected")
                        return
                    } else {
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.WAITING_FOR_DISCONNECTION))
                        aapsLogger.debug(LTag.PUMPQUEUE, "waiting for disconnect")
                        SystemClock.sleep(1000)
                    }
                }
            }
        } finally {
            if (mWakeLock?.isHeld == true) mWakeLock?.release()
            aapsLogger.debug(LTag.PUMPQUEUE, "thread end")
        }
    }
}