package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.ui.WeekdayPicker
import java.util.*

class InputWeekDay(injector: HasAndroidInjector) : Element(injector) {

    enum class DayOfWeek {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

        fun toCalendarInt(): Int {
            return calendarInts[ordinal]
        }

        @get:StringRes val shortName: Int
            get() = shortNames[ordinal]

        companion object {
            private val calendarInts = intArrayOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY,
                Calendar.SUNDAY
            )
            private val shortNames = intArrayOf(
                R.string.weekday_monday_short,
                R.string.weekday_tuesday_short,
                R.string.weekday_wednesday_short,
                R.string.weekday_thursday_short,
                R.string.weekday_friday_short,
                R.string.weekday_saturday_short,
                R.string.weekday_sunday_short
            )

            fun fromCalendarInt(day: Int): DayOfWeek {
                for (i in calendarInts.indices) {
                    if (calendarInts[i] == day) return values()[i]
                }
                throw IllegalStateException("Invalid day")
            }
        }
    }

    val weekdays = BooleanArray(DayOfWeek.values().size)

    init {
        for (day in DayOfWeek.values()) set(day, false)
    }

    fun setAll(value: Boolean) {
        for (day in DayOfWeek.values()) set(day, value)
    }

    operator fun set(day: DayOfWeek, value: Boolean): InputWeekDay {
        weekdays[day.ordinal] = value
        return this
    }

    fun isSet(day: DayOfWeek): Boolean = weekdays[day.ordinal]

    fun getSelectedDays(): List<Int> {
        val selectedDays: MutableList<Int> = ArrayList()
        for (i in weekdays.indices) {
            val day = DayOfWeek.values()[i]
            val selected = weekdays[i]
            if (selected) selectedDays.add(day.toCalendarInt())
        }
        return selectedDays
    }

    override fun addToLayout(root: LinearLayout) {
        WeekdayPicker(root.context).apply {
            setSelectedDays(getSelectedDays())
            setOnWeekdaysChangeListener { i: Int, selected: Boolean -> set(DayOfWeek.fromCalendarInt(i), selected) }
            root.addView(this)
        }
    }
}
