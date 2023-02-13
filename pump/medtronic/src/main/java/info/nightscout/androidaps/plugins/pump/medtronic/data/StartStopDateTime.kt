package info.nightscout.androidaps.plugins.pump.medtronic.data

import org.joda.time.LocalDateTime

data class StartStopDateTime(val startOperationTime: LocalDateTime, val endOperationTime: LocalDateTime)


/**
 * used by medlink
 */
class NextStartStop() {

    lateinit var startStopDateTime: StartStopDateTime
    fun getNextStartStop(duration: Int, interval: Int): StartStopDateTime {
        startStopDateTime = if (!::startStopDateTime.isInitialized) {
            val startTime = LocalDateTime.now()
            StartStopDateTime(startTime, startTime.plusMinutes(duration))
        } else {
            var start = startStopDateTime.endOperationTime.plusMinutes(interval)
            StartStopDateTime(start, start.plusMinutes(duration))

        }
        return startStopDateTime
    }
}
