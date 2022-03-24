package info.nightscout.androidaps.dialogs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.MaterialTimePicker.INPUT_MODE_CLOCK
import com.google.android.material.timepicker.TimeFormat
import com.ms_square.etsyblur.BlurConfig
import com.ms_square.etsyblur.BlurDialogFragment
import com.ms_square.etsyblur.SmartAsyncPolicy
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject

abstract class DialogFragmentWithDate : BlurDialogFragment() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil

    fun interface OnValueChangedListener {
        fun onValueChanged(value: Long)
    }

    var eventTime: Long = 0
    var eventTimeOriginal: Long = 0
    val eventTimeChanged: Boolean
        get() = eventTime != eventTimeOriginal

    var eventDateView: TextView? = null
    var eventTimeView: TextView? = null
    private var mOnValueChangedListener: OnValueChangedListener? = null

    //one shot guards
    private var okClicked: Boolean = false

    companion object {

        private var seconds: Int = (Math.random() * 59.0).toInt()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        aapsLogger.debug(LTag.APS, "Dialog opened: ${this.javaClass.name}")
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putLong("eventTime", eventTime)
        savedInstanceState.putLong("eventTimeOriginal", eventTimeOriginal)
    }

    fun onCreateViewGeneral() {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        val themeToSet = sp.getInt("theme", ThemeUtil.THEME_DARKSIDE)
        try {
            val theme: Resources.Theme? = context?.getTheme()
            // https://stackoverflow.com/questions/11562051/change-activitys-theme-programmatically
            if (theme != null) {
                theme.applyStyle(ThemeUtil.getThemeId(themeToSet), true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val drawable: Drawable? = context?.let { ContextCompat.getDrawable(it, R.drawable.dialog) }
        drawable?.setColorFilter(PorterDuffColorFilter(rh.gac(context, R.attr.windowBackground ), PorterDuff.Mode.SRC_IN))
        dialog?.window?.setBackgroundDrawable(drawable)

        context?.let { SmartAsyncPolicy(it) }?.let {
            BlurConfig.Builder()
                .overlayColor(ContextCompat.getColor(requireContext(), R.color.white_alpha_40))  // semi-transparent white color
                .debug(false)
                .asyncPolicy(it)
                .build()
        }
    }

    fun updateDateTime(timeMs: Long) {
        eventTime = timeMs
        eventDateView?.text = dateUtil.dateString(eventTime)
        eventTimeView?.text = dateUtil.timeString(eventTime)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        eventDateView = view.findViewById(R.id.eventdate) as TextView?
        eventTimeView = view.findViewById(R.id.eventtime) as TextView?

        eventTimeOriginal = savedInstanceState?.getLong("eventTimeOriginal") ?: dateUtil.nowWithoutMilliseconds()
        eventTime = savedInstanceState?.getLong("eventTime") ?: eventTimeOriginal

        eventDateView?.text = dateUtil.dateString(eventTime)
        eventTimeView?.text = dateUtil.timeString(eventTime)

        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setSelection(eventTime)
                .setTitleText("Select date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

        datePicker.addOnPositiveButtonClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = eventTime
            cal.time = Date(it)

            val c = Calendar.getInstance()
            c.time =  Date(it)
            cal.set(Calendar.YEAR, c.get(Calendar.YEAR))
            cal.set(Calendar.MONTH,  c.get(Calendar.MONTH))
            cal.set(Calendar.DAY_OF_MONTH, c.get(Calendar.DAY_OF_MONTH))
            eventTime = cal.timeInMillis
            eventDateView?.text = dateUtil.dateString(eventTime)
            callValueChangedListener()
        }

        val cinit = Calendar.getInstance()
        cinit.time =  Date(eventTime)

        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour( cinit.get(Calendar.HOUR_OF_DAY))
            .setMinute(cinit.get(Calendar.MINUTE))
            .setInputMode(INPUT_MODE_CLOCK)
            .build()

        timePicker.addOnPositiveButtonClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = eventTime
            cal.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            cal.set(Calendar.MINUTE, timePicker.minute)
            cal.set(
                Calendar.SECOND,
                seconds++
            ) // randomize seconds to prevent creating record of the same time, if user choose time manually
            eventTime = cal.timeInMillis
            eventTimeView?.text = dateUtil.timeString(eventTime)
            callValueChangedListener()
        }

        eventDateView?.setOnClickListener {
            datePicker.show(getParentFragmentManager(), "Test")
        }

        eventTimeView?.setOnClickListener {
            timePicker.show(getParentFragmentManager(), "Set Time")
        }

        (view.findViewById(R.id.notes_layout) as View?)?.visibility =
            sp.getBoolean(R.string.key_show_notes_entry_dialogs, false).toVisibility()

        (view.findViewById(R.id.ok) as Button?)?.setOnClickListener {
            synchronized(okClicked) {
                if (okClicked) {
                    aapsLogger.warn(LTag.UI, "guarding: ok already clicked for dialog: ${this.javaClass.name}")
                } else {
                    okClicked = true
                    if (submit()) {
                        aapsLogger.debug(LTag.APS, "Submit pressed for Dialog: ${this.javaClass.name}")
                        dismiss()
                    } else {
                        aapsLogger.debug(LTag.APS, "Submit returned false for Dialog: ${this.javaClass.name}")
                        okClicked = false
                    }
                }
            }
        }
        (view.findViewById(R.id.cancel) as Button?)?.setOnClickListener {
            aapsLogger.debug(LTag.APS, "Cancel pressed for dialog: ${this.javaClass.name}")
            dismiss()
        }

    }

    private fun callValueChangedListener() {
        mOnValueChangedListener?.onValueChanged(eventTime)
    }

    fun setOnValueChangedListener(onValueChangedListener: OnValueChangedListener?) {
        mOnValueChangedListener = onValueChangedListener
    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: "")
        }
    }

    abstract fun submit(): Boolean
}