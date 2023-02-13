package info.nightscout.implementation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.receivers.Intents
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.safeQueryBroadcastReceivers
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@OpenForTesting
@Singleton
class XDripBroadcastImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction
) : XDripBroadcast {

    override fun sendCalibration(bg: Double): Boolean {
        val bundle = Bundle()
        bundle.putDouble("glucose_number", bg)
        bundle.putString("units", if (profileFunction.getUnits() == GlucoseUnit.MGDL) "mgdl" else "mmol")
        bundle.putLong("timestamp", System.currentTimeMillis())
        val intent = Intent(Intents.ACTION_REMOTE_CALIBRATION)
        intent.putExtras(bundle)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        context.sendBroadcast(intent)
        val q = context.packageManager.safeQueryBroadcastReceivers(intent, 0)
        return if (q.isEmpty()) {
            ToastUtils.errorToast(context, R.string.xdrip_not_installed)
            aapsLogger.debug(rh.gs(R.string.xdrip_not_installed))
            false
        } else {
            ToastUtils.errorToast(context, R.string.calibration_sent)
            aapsLogger.debug(rh.gs(R.string.calibration_sent))
            true
        }
    }

    // sent in 640G mode
    override fun send(glucoseValue: GlucoseValue) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_medlink_xdripupload, false) || sp.getBoolean(info.nightscout.core.utils.R.string.key_dexcomg5_xdripupload, false)) {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            try {
                val entriesBody = JSONArray()
                val json = JSONObject()
                json.put("sgv", glucoseValue.value)
                json.put("direction", glucoseValue.trendArrow.text)
                json.put("device", "G5")
                json.put("type", "sgv")
                json.put("date", glucoseValue.timestamp)
                json.put("dateString", format.format(glucoseValue.timestamp))
                entriesBody.put(json)
                val bundle = Bundle()
                bundle.putString("action", "add")
                bundle.putString("collection", "entries")
                bundle.putString("data", entriesBody.toString())
                val intent = Intent(Intents.XDRIP_PLUS_NS_EMULATOR)
                intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                context.sendBroadcast(intent)
                val receivers = context.packageManager.safeQueryBroadcastReceivers(intent, 0)
                if (receivers.isEmpty()) {
                    //NSUpload.log.debug("No xDrip receivers found. ")
                    aapsLogger.debug(LTag.BGSOURCE, "No xDrip receivers found.")
                } else {
                    aapsLogger.debug(LTag.BGSOURCE, "${receivers.size} xDrip receivers")
                }
            } catch (e: JSONException) {
                aapsLogger.error(LTag.BGSOURCE, "Unhandled exception", e)
            }
        }
    }

    // sent in NSClient dbaccess mode
    override fun sendProfile(profileStoreJson: JSONObject) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_nsclient_localbroadcasts, false))
            broadcast(
                Intent(Intents.ACTION_NEW_PROFILE).apply {
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtras(Bundle().apply { putString("profile", profileStoreJson.toString()) })
                }
            )

    }

    // sent in NSClient dbaccess mode
    override fun sendTreatments(addedOrUpdatedTreatments: JSONArray) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_nsclient_localbroadcasts, false))
            splitArray(addedOrUpdatedTreatments).forEach { part ->
                broadcast(
                    Intent(Intents.ACTION_NEW_TREATMENT).apply {
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtras(Bundle().apply { putString("treatments", part.toString()) })
                    }
                )
            }
    }

    // sent in NSClient dbaccess mode
    override fun sendSgvs(sgvs: JSONArray) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_nsclient_localbroadcasts, false))
            splitArray(sgvs).forEach { part ->
                broadcast(
                    Intent(Intents.ACTION_NEW_SGV).apply {
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtras(Bundle().apply { putString("sgvs", part.toString()) })
                    }
                )
            }
    }

    private fun splitArray(array: JSONArray): List<JSONArray> {
        var ret: MutableList<JSONArray> = ArrayList()
        try {
            val size = array.length()
            var count = 0
            var newarr: JSONArray? = null
            for (i in 0 until size) {
                if (count == 0) {
                    if (newarr != null) ret.add(newarr)
                    newarr = JSONArray()
                    count = 20
                }
                newarr?.put(array[i])
                --count
            }
            if (newarr != null && newarr.length() > 0) ret.add(newarr)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
            ret = ArrayList()
            ret.add(array)
        }
        return ret
    }

    private fun broadcast(intent: Intent) {
        context.packageManager.safeQueryBroadcastReceivers(intent, 0).forEach { resolveInfo ->
            resolveInfo.activityInfo.packageName?.let {
                intent.setPackage(it)
                context.sendBroadcast(intent)
                aapsLogger.debug(LTag.CORE, "Sending broadcast " + intent.action + " to: " + it)
            }
        }
    }
}