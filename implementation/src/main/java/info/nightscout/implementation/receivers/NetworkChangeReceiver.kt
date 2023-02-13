package info.nightscout.implementation.receivers

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.HandlerThread
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.core.utils.receivers.StringUtils
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNetworkChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class NetworkChangeReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        handler.post { rxBus.send(grabNetworkStatus(context)) }
    }

    @Suppress("DEPRECATION")
    private fun grabNetworkStatus(context: Context): EventNetworkChange {
        val event = EventNetworkChange()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks: Array<Network> = cm.allNetworks
        networks.forEach {
            val capabilities = cm.getNetworkCapabilities(it) ?: return@forEach
            event.wifiConnected = event.wifiConnected || (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            event.mobileConnected = event.mobileConnected || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            event.vpnConnected = event.vpnConnected || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            // if (event.vpnConnected) aapsLogger.debug(LTag.CORE, "NETCHANGE: VPN connected.")
            if (event.wifiConnected) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
                    event.ssid = StringUtils.removeSurroundingQuotes(wifiInfo.ssid)
                    // aapsLogger.debug(LTag.CORE, "NETCHANGE: Wifi connected. SSID: ${event.connectedSsid()}")
                }
            }
            if (event.mobileConnected) {
                event.mobileConnected = true
                event.roaming = event.roaming || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                event.metered = event.metered || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                aapsLogger.debug(LTag.CORE, "NETCHANGE: Mobile connected. Roaming: ${event.roaming} Metered: ${event.metered}")
            }
            // aapsLogger.info(LTag.CORE, "Network: $it")
        }

        aapsLogger.debug(LTag.CORE, event.toString())
        receiverStatusStore.lastNetworkEvent = event
        return event
    }
}