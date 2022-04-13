package info.nightscout.androidaps.plugins.pump.medtronic.data

class EnliteWaitPeriod(var interval: Int, var lastSuccess: Long = 0L, var failedTimes: Int = 0){
    override fun toString(): String {
        return "EnliteWaitPeriod(interval=$interval, lastSuccess=$lastSuccess, failedTimes = $failedTimes)"
    }
}
