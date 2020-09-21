package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateTrigger
import info.nightscout.androidaps.plugins.general.automation.events.EventTriggerChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventTriggerClone
import info.nightscout.androidaps.plugins.general.automation.events.EventTriggerRemove
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDummy
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.automation_dialog_edit_trigger.*
import org.json.JSONObject
import javax.inject.Inject

class EditTriggerDialog : DialogFragmentWithDate() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var triggers: Trigger? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            bundle.getString("trigger")?.let { triggers = TriggerDummy(mainApp).instantiate(JSONObject(it)) }
        }

        onCreateViewGeneral()
        return inflater.inflate(R.layout.automation_dialog_edit_trigger, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        disposable += rxBus
            .toObservable(EventTriggerChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                automation_layoutTrigger.removeAllViews()
                triggers?.generateDialog(automation_layoutTrigger)
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventTriggerRemove::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                findParent(triggers, it.trigger)?.list?.remove(it.trigger)
                automation_layoutTrigger.removeAllViews()
                triggers?.generateDialog(automation_layoutTrigger)
            }, { fabricPrivacy.logException(it) })

        disposable += rxBus
            .toObservable(EventTriggerClone::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                findParent(triggers, it.trigger)?.list?.add(it.trigger.duplicate())
                automation_layoutTrigger.removeAllViews()
                triggers?.generateDialog(automation_layoutTrigger)
            }, { fabricPrivacy.logException(it) })

        // display root trigger
        triggers?.generateDialog(automation_layoutTrigger)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
    }

    override fun submit(): Boolean {
        triggers?.let { trigger -> rxBus.send(EventAutomationUpdateTrigger(trigger)) }
        return true
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        triggers?.let { savedInstanceState.putString("trigger", it.toJSON()) }
    }

    private fun findParent(where: Trigger?, what: Trigger): TriggerConnector? {
        if (where == null) return null
        if (where is TriggerConnector) {
            for (i in where.list) {
                if (i == what) return where
                if (i is TriggerConnector) return findParent(i, what)
            }
        }
        return null
    }
}