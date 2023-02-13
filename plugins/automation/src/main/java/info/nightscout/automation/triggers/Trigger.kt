package info.nightscout.automation.triggers

import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.dialogs.ChooseTriggerDialog
import info.nightscout.automation.events.EventTriggerChanged
import info.nightscout.automation.events.EventTriggerClone
import info.nightscout.automation.events.EventTriggerRemove
import info.nightscout.automation.services.LastLocationDataContainer
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject
import javax.inject.Inject

abstract class Trigger(val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var sp: SP
    @Inject lateinit var locationDataContainer: LastLocationDataContainer
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var dateUtil: DateUtil

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
    }

    abstract fun shouldRun(): Boolean
    abstract fun dataJSON(): JSONObject
    abstract fun fromJSON(data: String): Trigger

    abstract fun friendlyName(): Int
    abstract fun friendlyDescription(): String
    abstract fun icon(): Optional<Int>
    abstract fun duplicate(): Trigger

    fun scanForActivity(cont: Context?): AppCompatActivity? {
        return when (cont) {
            null                 -> null
            is AppCompatActivity -> cont
            is ContextWrapper    -> scanForActivity(cont.baseContext)
            else                 -> null
        }
    }

    open fun generateDialog(root: LinearLayout) {
        val title = TextView(root.context)
        title.setText(friendlyName())
        root.addView(title)
    }

    fun toJSON(): String =
        JSONObject()
            .put("type", this::class.java.simpleName)
            .put("data", dataJSON())
            .toString()

    fun instantiate(obj: JSONObject): Trigger {
        try {
            var type = obj.getString("type")
            val data = obj.getJSONObject("data")
            // stripe off package name
            val dotIndex = type.lastIndexOf('.')
            if (dotIndex > 0) type = type.substring(dotIndex + 1)
            return when (type) {
                TriggerAutosensValue::class.java.simpleName      -> TriggerAutosensValue(injector).fromJSON(data.toString())
                TriggerBg::class.java.simpleName                 -> TriggerBg(injector).fromJSON(data.toString())
                TriggerBolusAgo::class.java.simpleName           -> TriggerBolusAgo(injector).fromJSON(data.toString())
                TriggerBTDevice::class.java.simpleName           -> TriggerBTDevice(injector).fromJSON(data.toString())
                TriggerIob::class.java.simpleName                -> TriggerIob(injector).fromJSON(data.toString())
                TriggerCOB::class.java.simpleName                -> TriggerCOB(injector).fromJSON(data.toString())
                TriggerConnector::class.java.simpleName          -> TriggerConnector(injector).fromJSON(data.toString())
                TriggerDelta::class.java.simpleName              -> TriggerDelta(injector).fromJSON(data.toString())
                TriggerDummy::class.java.simpleName              -> TriggerDummy(injector).fromJSON(data.toString())
                TriggerIob::class.java.simpleName                -> TriggerIob(injector).fromJSON(data.toString())
                TriggerLocation::class.java.simpleName           -> TriggerLocation(injector).fromJSON(data.toString())
                TriggerProfilePercent::class.java.simpleName     -> TriggerProfilePercent(injector).fromJSON(data.toString())
                TriggerPumpLastConnection::class.java.simpleName -> TriggerPumpLastConnection(injector).fromJSON(data.toString())
                TriggerRecurringTime::class.java.simpleName      -> TriggerRecurringTime(injector).fromJSON(data.toString())
                TriggerTempTarget::class.java.simpleName         -> TriggerTempTarget(injector).fromJSON(data.toString())
                TriggerTempTargetValue::class.java.simpleName    -> TriggerTempTargetValue(injector).fromJSON(data.toString())
                TriggerTime::class.java.simpleName               -> TriggerTime(injector).fromJSON(data.toString())
                TriggerTimeRange::class.java.simpleName          -> TriggerTimeRange(injector).fromJSON(data.toString())
                TriggerWifiSsid::class.java.simpleName           -> TriggerWifiSsid(injector).fromJSON(data.toString())

                else                                             -> TriggerConnector(injector)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOMATION, "Error parsing $obj")
        }
        return TriggerConnector(injector)
    }

    fun createAddButton(context: Context, trigger: TriggerConnector): ImageButton =
        // Button [+]
        ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(info.nightscout.core.main.R.drawable.ic_add)
            contentDescription = rh.gs(R.string.add_short)
            setOnClickListener {
                scanForActivity(context)?.supportFragmentManager?.let {
                    val dialog = ChooseTriggerDialog()
                    dialog.show(it, "ChooseTriggerDialog")
                    dialog.setOnClickListener(object : ChooseTriggerDialog.OnClickListener {
                        override fun onClick(newTriggerObject: Trigger) {
                            trigger.list.add(newTriggerObject)
                            rxBus.send(EventTriggerChanged())
                        }
                    })
                }
            }
        }

    fun createDeleteButton(context: Context, trigger: Trigger): ImageButton =
        // Button [-]
        ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(info.nightscout.core.main.R.drawable.ic_remove)
            contentDescription = rh.gs(R.string.delete_short)
            setOnClickListener {
                rxBus.send(EventTriggerRemove(trigger))
            }
        }

    fun createCloneButton(context: Context, trigger: Trigger): ImageButton =
        // Button [*]
        ImageButton(context).apply {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = params
            setImageResource(info.nightscout.core.main.R.drawable.ic_clone)
            contentDescription = rh.gs(R.string.copy_short)
            setOnClickListener {
                rxBus.send(EventTriggerClone(trigger))
            }
        }
}