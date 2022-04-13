package info.nightscout.androidaps.plugins.pump.medtronic.data

import kotlin.math.abs

object EnliteInterval {

    private val currentIntervals = arrayOf(240000L, 315000L, 370000L, 400000L)
    private var enliteIntervals = newArray()
    private var currentBGTime = 0L
    private var delta = 0L

    fun clear() {
        enliteIntervals = newArray()
    }


    private fun newArray() = arrayOf(
        EnliteWaitPeriod(0, 0, 0),
        EnliteWaitPeriod(0, 0, 0),
        EnliteWaitPeriod(0, 0, 0),
        EnliteWaitPeriod(0, 0, 0),
        EnliteWaitPeriod(0, 0, 0),
        EnliteWaitPeriod(0, 0, 0),
        EnliteWaitPeriod(0, 0, 0),
        EnliteWaitPeriod(0, 0, 0),
        EnliteWaitPeriod(0, 0, 0)
    )

    fun setBGTimeAndDelta(bgTime: Long, delta: Long) {
        if (currentBGTime == 0L) {
            currentBGTime = bgTime
            this.delta = delta
        }
        if (abs(delta) > abs(this.delta)) {
            this.delta = delta
        }
    }

    private fun currentBGTime(): Boolean {
        if (currentBGTime < System.currentTimeMillis()) {
            currentBGTime += 300000
            return true
        }
        return false
    }

    fun nextBGTime(): Long {
        var interval = 0L
        if (currentBGTime()) {
            interval =  nextInterval()
        } else {
            interval =  currentIntervals[enliteIntervals[index].interval]
        }
        return currentBGTime + interval + delta
    }

    private var index = 0
    private fun nextInterval(): Long {
        if (index == 8) {
            index = 0
        } else {
            index++
        }
        return currentIntervals[enliteIntervals[index].interval]
    }

    private const val MAX_FAILED_TIMES = 3
    var lastFailed = 0L
    fun currentFailed() {
        if (lastFailed > 0 && System.currentTimeMillis() - lastFailed > currentIntervals[0] &&
            enliteIntervals[index].interval != 3 && (enliteIntervals[index].lastSuccess == 0L || enliteIntervals[index].failedTimes < MAX_FAILED_TIMES)
        ) {
            enliteIntervals[index].interval++
            if (enliteIntervals[index].failedTimes > MAX_FAILED_TIMES) {
                enliteIntervals[index].failedTimes = 0
            }
        } else if (lastFailed > 0 && System.currentTimeMillis() - lastFailed > currentIntervals[0]) {
            enliteIntervals[index].failedTimes++
        }
    }

    override fun toString(): String {
        return enliteIntervals.joinToString(separator = ",")
    }

    fun currentSuccess(lastSuccess: Long) {
        enliteIntervals[index].lastSuccess = lastSuccess
    }

}