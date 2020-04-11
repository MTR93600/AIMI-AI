package info.nightscout.androidaps.plugins.general.wear

import android.content.Intent
import dagger.Lazy
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.wear.wearintegration.WatchUpdaterService
import info.nightscout.androidaps.plugins.general.wear.tizenintegration.TizenUpdaterService
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

import android.util.Log
import android.widget.Toast
import com.samsung.android.sdk.accessory.SAAgentV2
import com.samsung.android.sdk.accessory.SAAgentV2.RequestAgentCallback

@Singleton
class WearPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    private val mainApp: MainApp,
    private val fabricPrivacy: FabricPrivacy,
    private val loopPlugin: Lazy<LoopPlugin>,
    private val rxBus: RxBusWrapper

) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(WearFragment::class.java.name)
    .pluginName(R.string.wear)
    .shortName(R.string.wear_shortname)
    .preferencesId(R.xml.pref_wear)
    .description(R.string.description_wear),
    aapsLogger, resourceHelper, injector
) {
    private val TAG = "Tizen plugin"
    private var tizenUS: TizenUpdaterService? = null
    private val mAgentCallback: RequestAgentCallback = object : RequestAgentCallback {
        override fun onAgentAvailable(agent: SAAgentV2) {
            tizenUS = agent as TizenUpdaterService
            tizenUS!!.findPeers()
        }
        override fun onError(errorCode: Int, message: String) {
            Log.e(TAG, "Agent initialization error: $errorCode. ErrorMsg: $message")
        }
    }

    private val disposable = CompositeDisposable()
    override fun onStart() {
        if (sp.getBoolean(TizenUpdaterService.TIZEN_ENABLE, false)) {
            SAAgentV2.requestAgent(mainApp, TizenUpdaterService::class.java.name, mAgentCallback)
        }
        super.onStart()
        disposable.add(rxBus
            .toObservable(EventOpenAPSUpdateGui::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = false) }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = false) }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = false) }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = false) }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventNewBasalProfile::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendDataToWatch(status = false, basals = true, bgValue = false) }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = true) }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                // possibly new high or low mark
                resendDataToWatch()
                // status may be formatted differently
                sendDataToWatch(status = true, basals = false, bgValue = false)
            }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                if (WatchUpdaterService.shouldReportLoopStatus(loopPlugin.get().isEnabled(PluginType.LOOP)) || TizenUpdaterService.shouldReportLoopStatus(loopPlugin.get().isEnabled(PluginType.LOOP)))
                    sendDataToWatch(status = true, basals = false, bgValue = false)
            }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventBolusRequested::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventBolusRequested ->
                val status = String.format(resourceHelper.gs(R.string.bolusrequested), event.amount)
                val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS)
                intent.putExtra("progresspercent", 0)
                intent.putExtra("progressstatus", status)
                mainApp.startService(intent)
                if (tizenOK()) { tizenUS!!.sendBolusProgress(0,status) }
            }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventDismissBolusProgressIfRunning::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventDismissBolusProgressIfRunning ->
                if (event.result == null) return@subscribe
                val status: String = if (event.result!!.success) {
                    resourceHelper.gs(R.string.success)
                } else {
                    resourceHelper.gs(R.string.nosuccess)
                }
                val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS)
                intent.putExtra("progresspercent", 100)
                intent.putExtra("progressstatus", status)
                mainApp.startService(intent)
                if (tizenOK()) { tizenUS!!.sendBolusProgress(100,status) }
            }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventOverviewBolusProgress ->
                if (!event.isSMB() || sp.getBoolean("wear_notifySMB", true)) {
                    val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS)
                    intent.putExtra("progresspercent", event.percent)
                    intent.putExtra("progressstatus", event.status)
                    mainApp.startService(intent)
                    if (tizenOK()) { tizenUS!!.sendBolusProgress(event.percent,event.status) }
                }
            }) { fabricPrivacy.logException(it) })
    }

    override fun onStop() {
        disposable.clear()
        stopTizenAgent()
        super.onStop()
    }


    private fun sendDataToWatch(status: Boolean, basals: Boolean, bgValue: Boolean) {
        //Log.d(TAG, "WR: WearPlugin:sendDataToWatch (status=" + status + ",basals=" + basals + ",bgValue=" + bgValue + ")");
        if (isEnabled(getType())) {
            // only start service when this plugin is enabled
            if (bgValue) {
                mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java))
            }
            if (basals) {
                mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BASALS))
            }
            if (status) {
                mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_STATUS))
            }

            if (tizenOK()) {     // Tizen enabled in settings and connected to a watch
                if (bgValue) {
                    tizenUS!!.sendData()
                }
                if (basals) {
                    tizenUS!!.sendBasals()
                }
                if (status) {
                    tizenUS!!.sendStatus()
                }
            }
        }
    }

    fun resendDataToWatch() {
        //Log.d(TAG, "WR: WearPlugin:resendDataToWatch");
        mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_RESEND))

        if (tizenOK()) { tizenUS!!.resendData() }
    }

    fun openSettings() {
        //Log.d(TAG, "WR: WearPlugin:openSettings");
        mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_OPEN_SETTINGS))

        if (tizenOK()) { tizenUS!!.sendNotification() }
    }

    fun requestNotificationCancel(actionString: String?) { //Log.d(TAG, "WR: WearPlugin:requestNotificationCancel");
        val intent = Intent(mainApp, WatchUpdaterService::class.java)
            .setAction(WatchUpdaterService.ACTION_CANCEL_NOTIFICATION)
        intent.putExtra("actionstring", actionString)
        mainApp.startService(intent)

        if (tizenOK()) { tizenUS!!.sendCancelNotificationRequest(actionString) }
    }

    fun requestActionConfirmation(title: String, message: String, actionString: String) {
        val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_ACTIONCONFIRMATIONREQUEST)
        intent.putExtra("title", title)
        intent.putExtra("message", message)
        intent.putExtra("actionstring", actionString)
        mainApp.startService(intent)

        if (tizenOK()) { tizenUS!!.sendActionConfirmationRequest(title, message, actionString) }
    }

    fun requestChangeConfirmation(title: String, message: String, actionString: String) {
        val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_CHANGECONFIRMATIONREQUEST)
        intent.putExtra("title", title)
        intent.putExtra("message", message)
        intent.putExtra("actionstring", actionString)
        mainApp.startService(intent)

        if (tizenOK()) { tizenUS!!.sendChangeConfirmationRequest(title, message, actionString) }
    }

    private fun stopTizenAgent() {
        if (tizenUS != null) {
            tizenUS!!.closeConnection()
            tizenUS!!.releaseAgent()
            tizenUS = null
        }
    }

    private fun restartTizenAgent() {
        if (tizenUS != null) {
            stopTizenAgent()
        }
        SAAgentV2.requestAgent(mainApp, TizenUpdaterService::class.java.name, mAgentCallback)
    }


    private fun tizenOK(): Boolean {
        var tizenOk=false
        if (sp.getBoolean(TizenUpdaterService.TIZEN_ENABLE, false)) { // if Tizen is enable check if SAAgent is launched and if a watch is connected
            if (tizenUS == null) {
                Toast.makeText(mainApp, "Agent KO, Restart peerAgent", Toast.LENGTH_LONG).show()
                restartTizenAgent()
            } else  if (tizenUS!!.connected) {  // Tizen watch enabled in settings and connected, we can send data
                Toast.makeText(mainApp, "Connected", Toast.LENGTH_LONG).show()
                tizenOk=true
            } else {                            // Tizen agent is launched but not connected, try to find peers should not appear because findpeer is automatic in TizenUpdaterService...
                Toast.makeText(mainApp, "Agent OK, try to find peers", Toast.LENGTH_LONG).show()
                tizenUS!!.findPeers()
            }
        } else {                                // if tizen is disable in settings, then close connection and stop Tizen agent
            if (tizenUS != null) Toast.makeText(mainApp, "Tizen disabled, close communication and stop tizen Agent", Toast.LENGTH_LONG).show()
            stopTizenAgent()
        }
        return tizenOk
    }

}