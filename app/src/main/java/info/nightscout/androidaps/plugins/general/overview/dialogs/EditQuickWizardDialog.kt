package info.nightscout.androidaps.plugins.general.overview.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.wizard.QuickWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventQuickWizardChange
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.SafeParse
import kotlinx.android.synthetic.main.okcancel.*
import kotlinx.android.synthetic.main.overview_editquickwizard_dialog.*
import org.json.JSONException
import java.util.*
import javax.inject.Inject

class EditQuickWizardDialog : DaggerDialogFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil

    var position = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
        return inflater.inflate(R.layout.overview_editquickwizard_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (arguments ?: savedInstanceState)?.let { bundle ->
            position = bundle.getInt("position", -1)
        }
        val entry = if (position ==-1) quickWizard.newEmptyItem() else quickWizard[position]
        ok.setOnClickListener {
            if (overview_editquickwizard_from_spinner.selectedItem == null) return@setOnClickListener
            if (overview_editquickwizard_to_spinner.selectedItem == null) return@setOnClickListener
            try {
                entry.storage.put("buttonText", overview_editquickwizard_button_edit.text.toString())
                entry.storage.put("carbs", SafeParse.stringToInt(overview_editquickwizard_carbs_edit.text.toString()))
                val validFromInt = DateUtil.toSeconds(overview_editquickwizard_from_spinner.selectedItem.toString())
                entry.storage.put("validFrom", validFromInt)
                val validToInt = DateUtil.toSeconds(overview_editquickwizard_to_spinner.selectedItem.toString())
                entry.storage.put("validTo", validToInt)
                entry.storage.put("useBG", overview_editquickwizard_usebg_spinner.selectedItemPosition)
                entry.storage.put("useCOB", overview_editquickwizard_usecob_spinner.selectedItemPosition)
                entry.storage.put("useBolusIOB", overview_editquickwizard_usebolusiob_spinner.selectedItemPosition)
                entry.storage.put("useBasalIOB", overview_editquickwizard_usebasaliob_spinner.selectedItemPosition)
                entry.storage.put("useTrend", overview_editquickwizard_usetrend_spinner.selectedItemPosition)
                entry.storage.put("useSuperBolus", overview_editquickwizard_usesuperbolus_spinner.selectedItemPosition)
                entry.storage.put("useTempTarget", overview_editquickwizard_usetemptarget_spinner.selectedItemPosition)
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            }

            quickWizard.addOrUpdate(entry)
            rxBus.send(EventQuickWizardChange())
            dismiss()
        }
        cancel.setOnClickListener { dismiss() }

        var posFrom = 0
        var posTo = 95
        val timeList = ArrayList<CharSequence>()
        var pos = 0
        var t = 0
        while (t < 24 * 60 * 60) {
            timeList.add(dateUtil.timeString(DateUtil.toDate(t)))
            if (entry.validFrom() == t) posFrom = pos
            if (entry.validTo() == t) posTo = pos
            pos++
            t += 15 * 60
        }
        timeList.add(dateUtil.timeString(DateUtil.toDate(24 * 60 * 60 - 60)))

        val adapter = context?.let { context -> ArrayAdapter(context, R.layout.spinner_centered, timeList) }
        overview_editquickwizard_from_spinner.adapter = adapter
        overview_editquickwizard_to_spinner.adapter = adapter

        overview_editquickwizard_button_edit.setText(entry.buttonText())
        overview_editquickwizard_carbs_edit.setText(entry.carbs().toString())
        overview_editquickwizard_from_spinner.setSelection(posFrom)
        overview_editquickwizard_to_spinner.setSelection(posTo)

        overview_editquickwizard_usebg_spinner.setSelection(entry.useBG())
        overview_editquickwizard_usecob_spinner.setSelection(entry.useCOB())
        overview_editquickwizard_usebolusiob_spinner.setSelection(entry.useBolusIOB())
        overview_editquickwizard_usebasaliob_spinner.setSelection(entry.useBasalIOB())
        overview_editquickwizard_usetrend_spinner.setSelection(entry.useTrend())
        overview_editquickwizard_usesuperbolus_spinner.setSelection(entry.useSuperBolus())
        overview_editquickwizard_usetemptarget_spinner.setSelection(entry.useTempTarget())

        overview_editquickwizard_usecob_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) = processCob()
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        processCob()
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("position", position)
    }

    private fun processCob() {
        if (overview_editquickwizard_usecob_spinner.selectedItemPosition == QuickWizardEntry.YES) {
            overview_editquickwizard_usebolusiob_spinner.isEnabled = false
            overview_editquickwizard_usebasaliob_spinner.isEnabled = false
            overview_editquickwizard_usebolusiob_spinner.setSelection(QuickWizardEntry.YES)
            overview_editquickwizard_usebasaliob_spinner.setSelection(QuickWizardEntry.YES)
        } else {
            overview_editquickwizard_usebolusiob_spinner.isEnabled = true
            overview_editquickwizard_usebasaliob_spinner.isEnabled = true
        }
    }
}
