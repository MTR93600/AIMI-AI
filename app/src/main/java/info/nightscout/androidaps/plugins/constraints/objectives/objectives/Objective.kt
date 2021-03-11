package info.nightscout.androidaps.plugins.constraints.objectives.objectives

import android.content.Context
import android.graphics.Color
import android.text.util.Linkify
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import kotlin.math.floor

abstract class Objective(injector: HasAndroidInjector, spName: String, @StringRes objective: Int, @StringRes gate: Int) {

    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper

    private val spName: String
    @StringRes val objective: Int
    @StringRes val gate: Int
    var startedOn: Long = 0
        set(value) {
            field = value
            sp.putLong("Objectives_" + spName + "_started", startedOn)
        }
    var accomplishedOn: Long = 0
        set(value) {
            field = value
            sp.putLong("Objectives_" + spName + "_accomplished", value)
        }

    var tasks: MutableList<Task> = ArrayList()

    var hasSpecialInput = false

    val isCompleted: Boolean
        get() {
            for (task in tasks) {
                if (!task.shouldBeIgnored() && !task.isCompleted()) return false
            }
            return true
        }

    init {
        injector.androidInjector().inject(this)
        this.spName = spName
        this.objective = objective
        this.gate = gate
        startedOn = sp.getLong("Objectives_" + spName + "_started", 0L)
        accomplishedOn = sp.getLong("Objectives_" + spName + "_accomplished", 0L)
        if (accomplishedOn - DateUtil.now() > T.hours(3).msecs() || startedOn - DateUtil.now() > T.hours(3).msecs()) { // more than 3 hours in the future
            startedOn = 0
            accomplishedOn = 0
        }
    }

    fun isCompleted(trueTime: Long): Boolean {
        for (task in tasks) {
            if (!task.shouldBeIgnored() && !task.isCompleted(trueTime)) return false
        }
        return true
    }

    val isAccomplished: Boolean
        get() = accomplishedOn != 0L && accomplishedOn < DateUtil.now()
    val isStarted: Boolean
        get() = startedOn != 0L

    open fun specialActionEnabled(): Boolean {
        return true
    }

    open fun specialAction(activity: FragmentActivity, input: String) {}

    abstract inner class Task(var objective: Objective, @StringRes val task: Int) {

        var hints = ArrayList<Hint>()

        abstract fun isCompleted(): Boolean

        open fun isCompleted(trueTime: Long): Boolean = isCompleted

        open val progress: String
            get() = resourceHelper.gs(if (isCompleted) R.string.completed_well_done else R.string.not_completed_yet)

        fun hint(hint: Hint): Task {
            hints.add(hint)
            return this
        }

        open fun shouldBeIgnored(): Boolean = false
    }

    inner class MinimumDurationTask internal constructor(objective: Objective, private val minimumDuration: Long) : Task(objective, R.string.time_elapsed) {

        override fun isCompleted(): Boolean =
            objective.isStarted && System.currentTimeMillis() - objective.startedOn >= minimumDuration

        override fun isCompleted(trueTime: Long): Boolean {
            return objective.isStarted && trueTime - objective.startedOn >= minimumDuration
        }

        override val progress: String
            get() = (getDurationText(System.currentTimeMillis() - objective.startedOn)
                + " / " + getDurationText(minimumDuration))

        private fun getDurationText(duration: Long): String {
            val days = floor(duration.toDouble() / T.days(1).msecs()).toInt()
            val hours = floor(duration.toDouble() / T.hours(1).msecs()).toInt()
            val minutes = floor(duration.toDouble() / T.mins(1).msecs()).toInt()
            return when {
                days > 0  -> resourceHelper.gq(R.plurals.days, days, days)
                hours > 0 -> resourceHelper.gq(R.plurals.hours, hours, hours)
                else      -> resourceHelper.gq(R.plurals.minutes, minutes, minutes)
            }
        }
    }

    inner class ExamTask internal constructor(objective: Objective, @StringRes task: Int, @StringRes val question: Int, private val spIdentifier: String) : Task(objective, task) {

        var options = ArrayList<Option>()
        var answered: Boolean = false
            set(value) {
                field = value
                sp.putBoolean("ExamTask_$spIdentifier", value)
            }
        var disabledTo: Long = 0
            set(value) {
                field = value
                sp.putLong("DisabledTo_$spIdentifier", value)
            }

        init {
            answered = sp.getBoolean("ExamTask_$spIdentifier", false)
            disabledTo = sp.getLong("DisabledTo_$spIdentifier", 0L)
        }

        override fun isCompleted(): Boolean = answered

        fun isEnabledAnswer(): Boolean = disabledTo < DateUtil.now()

        fun option(option: Option): ExamTask {
            options.add(option)
            return this
        }
    }

    inner class Option internal constructor(@StringRes var option: Int, var isCorrect: Boolean) {

        var cb: CheckBox? = null // TODO: change it, this will block releasing memory

        fun generate(context: Context): CheckBox {
            cb = CheckBox(context)
            cb?.setText(option)
            return cb!!
        }

        fun evaluate(): Boolean {
            val selection = cb!!.isChecked
            return if (selection && isCorrect) true else !selection && !isCorrect
        }
    }

    inner class Hint internal constructor(@StringRes var hint: Int) {

        fun generate(context: Context): TextView {
            val textView = TextView(context)
            textView.setText(hint)
            textView.autoLinkMask = Linkify.WEB_URLS
            textView.linksClickable = true
            textView.setLinkTextColor(Color.YELLOW)
            Linkify.addLinks(textView, Linkify.WEB_URLS)
            return textView
        }
    }
}