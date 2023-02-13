    package info.nightscout.androidaps.plugins.pump.medtronic.data


    /**
     * used by medlink
     */
object EnliteInterval {
    private val currentIntervals = arrayOf(240000, 315000, 370000, 400000)
    private var enliteIntervals = newArray()

    fun clear() {
        enliteIntervals = newArray()
    }

    private fun newArray() = arrayOf(
            EnliteWaitPeriod(0, 0),
            EnliteWaitPeriod(0, 0),
            EnliteWaitPeriod(0, 0),
            EnliteWaitPeriod(0, 0),
            EnliteWaitPeriod(0, 0),
            EnliteWaitPeriod(0, 0),
            EnliteWaitPeriod(0, 0),
            EnliteWaitPeriod(0, 0),
            EnliteWaitPeriod(0, 0))


    private var index = 0
    fun nextInterval(): Int {
        if (index == 8) {
            index = 0
        } else {
            index++
        }
        return currentIntervals[enliteIntervals[index].interval]
    }

    var lastFailed = 0L
    fun currentFailed() {
        if (lastFailed >0 && System.currentTimeMillis() - lastFailed > currentIntervals[0] &&
                enliteIntervals[index].interval != 3 && enliteIntervals[index].lastSuccess == 0L) {
            enliteIntervals[index].interval++
        }
    }

    override fun toString(): String {
        return enliteIntervals.joinToString(separator = ",")
    }

    fun currentSuccess(lastSuccess: Long) {
        enliteIntervals[index].lastSuccess = lastSuccess
    }


}