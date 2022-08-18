package info.nightscout.androidaps.interaction.utils

@Suppress("unused")
object Constants {

    const val SECOND_IN_MS: Long = 1000
    const val MINUTE_IN_MS: Long = 60000
    const val HOUR_IN_MS: Long = 3600000
    const val DAY_IN_MS: Long = 86400000
    const val WEEK_IN_MS = DAY_IN_MS * 7
    const val MONTH_IN_MS = DAY_IN_MS * 30
    const val STALE_MS = MINUTE_IN_MS * 12
}