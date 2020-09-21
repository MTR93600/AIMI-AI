package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dialogs.DialogFragmentWithDate
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import kotlinx.android.synthetic.main.automation_dialog_choose_trigger.*
import javax.inject.Inject
import kotlin.reflect.full.primaryConstructor

class ChooseTriggerDialog : DialogFragmentWithDate() {
    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var mainApp : MainApp

    private var checkedIndex = -1
    private var clickListener: OnClickListener? = null

    interface OnClickListener {
        fun onClick(newTriggerObject: Trigger)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // restore checked radio button
        savedInstanceState?.let { bundle ->
            checkedIndex = bundle.getInt("checkedIndex")
        }

        onCreateViewGeneral()
        return inflater.inflate(R.layout.automation_dialog_choose_trigger, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        for (t in automationPlugin.getTriggerDummyObjects()) {
            val radioButton = RadioButton(context)
            radioButton.setText(t.friendlyName())
            radioButton.tag = t.javaClass.name
            automation_chooseTriggerRadioGroup.addView(radioButton)
        }

        if (checkedIndex != -1)
            (automation_chooseTriggerRadioGroup.getChildAt(checkedIndex) as RadioButton).isChecked = true
    }

    override fun submit(): Boolean {
        instantiateTrigger()?.let {
            clickListener?.onClick(it)
        }
        return true
    }

    fun setOnClickListener(clickListener: OnClickListener) {
        this.clickListener = clickListener
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("checkedIndex", determineCheckedIndex())
    }

    private fun instantiateTrigger(): Trigger? {
        return getTriggerClass()?.let {
            val clazz = Class.forName(it).kotlin
            clazz.primaryConstructor?.call(mainApp) as Trigger
        }
    }

    private fun getTriggerClass(): String? {
        val radioButtonID = automation_chooseTriggerRadioGroup.checkedRadioButtonId
        val radioButton = automation_chooseTriggerRadioGroup.findViewById<RadioButton>(radioButtonID)
        return radioButton?.let {
            it.tag as String
        }
    }

    private fun determineCheckedIndex(): Int {
        for (i in 0 until automation_chooseTriggerRadioGroup.childCount) {
            if ((automation_chooseTriggerRadioGroup.getChildAt(i) as RadioButton).isChecked)
                return i
        }
        return -1
    }
}
