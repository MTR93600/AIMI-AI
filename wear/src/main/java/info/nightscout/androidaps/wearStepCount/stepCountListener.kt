package info.nightscout.androidaps.wearStepCount

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import info.nightscout.androidaps.comm.IntentWearToMobile
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.EventData
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

class stepCountListener(
    private val ctx: Context,
    private val aapsLogger: AAPSLogger,
    aapsSchedulers: AapsSchedulers,
    now: Long = System.currentTimeMillis(),
) :  SensorEventListener, Disposable {

    private val samplingIntervalMillis = 300_000L
    private val stepsMap = LinkedHashMap<Long, Int>()
    private val fiveMinutesInMs = 300000
    private val numOf5MinBlocksToKeep = 20
    private var previousStepCount = -1
    private var schedule: Disposable? = null

    init {
        aapsLogger.info(LTag.WEAR, "Create ${javaClass.simpleName}")
        val sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        if (sensorManager == null) {
            aapsLogger.warn(LTag.WEAR, "Cannot get sensor manager to get steps rate readings")
        } else {
            val stepsRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            aapsLogger.debug(LTag.WEAR, "Steps rate sensor: $stepsRateSensor")
            if (stepsRateSensor == null) {
                aapsLogger.debug(LTag.WEAR, "Cannot get steps rate sensor")
            } else {
                sensorManager.registerListener(this, stepsRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
                aapsLogger.debug(LTag.WEAR, "Steps rate sensor registered")
            }
        }
        schedule = aapsSchedulers.io.schedulePeriodicallyDirect(
            ::send, samplingIntervalMillis, samplingIntervalMillis, TimeUnit.MILLISECONDS)
    }

    var sendStepsRate: (List<EventData.ActionStepsRate>)->Unit = { stepsList ->
        aapsLogger.info(LTag.WEAR, "sendStepsRate called")
        stepsList.forEach { steps ->
            ctx.startService(IntentWearToMobile(ctx, steps))
        }
    }

    override fun isDisposed() = schedule == null

    override fun dispose() {
        aapsLogger.info(LTag.WEAR, "Dispose ${javaClass.simpleName}")
        schedule?.dispose()
        (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager?)?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.i(LTag.WEAR.name, "onAccuracyChanged: Sensor: $sensor; accuracy: $accuracy")
    }

    private fun currentTimeIn5Min(): Long {
        return (System.currentTimeMillis() / fiveMinutesInMs.toDouble()).roundToLong()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onSensorChanged(event: SensorEvent) {
        aapsLogger.info(LTag.WEAR, "onSensorChanged called with event: $event")
        if (event.sensor?.type == Sensor.TYPE_STEP_COUNTER && event.values.isNotEmpty()) {
            val now = currentTimeIn5Min()
            val stepCount = event.values[0].toInt()
            if(previousStepCount >= 0) {
                var recentStepCount = stepCount - previousStepCount
                if(stepsMap.contains(now)) {
                    recentStepCount += stepsMap.getValue(now)
                }
                stepsMap[now] = recentStepCount
            }
            previousStepCount = stepCount

            if(stepsMap.size > numOf5MinBlocksToKeep) {
                val removeBefore = now - numOf5MinBlocksToKeep
                stepsMap.entries.removeIf { it.key < removeBefore}
            }
        }
    }

    private fun send() {
        send(System.currentTimeMillis())
    }

    @VisibleForTesting
    fun send(timestampMillis: Long) {
        val stepsInLast5Minutes = getStepsInLastXMin(1)
        val stepsInLast10Minutes = getStepsInLastXMin(2)
        val stepsInLast15Minutes = getStepsInLastXMin(3)
        val stepsInLast30Minutes = getStepsInLastXMin(6)
        val stepsInLast60Minutes = getStepsInLastXMin(12)

        aapsLogger.debug(LTag.WEAR, "Steps in last 5 minutes: $stepsInLast5Minutes")
        aapsLogger.debug(LTag.WEAR, "Steps in last 10 minutes: $stepsInLast10Minutes")
        aapsLogger.debug(LTag.WEAR, "Steps in last 15 minutes: $stepsInLast15Minutes")
        aapsLogger.debug(LTag.WEAR, "Steps in last 30 minutes: $stepsInLast30Minutes")
        aapsLogger.debug(LTag.WEAR, "Steps in last 60 minutes: $stepsInLast60Minutes")

        val device = (Build.MANUFACTURER ?: "unknown") + " " + (Build.MODEL ?: "unknown")

        val stepsList = listOf(
            EventData.ActionStepsRate(
                duration = 5 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = 0,
                steps15min = 0,
                steps30min = 0,
                steps60min = 0,
                device = device
            ),
            EventData.ActionStepsRate(
                duration = 10 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = stepsInLast10Minutes,
                steps15min = 0,
                steps30min = 0,
                steps60min = 0,
                device = device
            ),
            EventData.ActionStepsRate(
                duration = 15 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = stepsInLast10Minutes,
                steps15min = stepsInLast15Minutes,
                steps30min = 0,
                steps60min = 0,
                device = device
            ),
            EventData.ActionStepsRate(
                duration = 30 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = stepsInLast10Minutes,
                steps15min = stepsInLast15Minutes,
                steps30min = stepsInLast30Minutes,
                steps60min = 0,
                device = device
            ),
            EventData.ActionStepsRate(
                duration = 60 * 60 * 1000,
                timestamp = timestampMillis,
                steps5min = stepsInLast5Minutes,
                steps10min = stepsInLast10Minutes,
                steps15min = stepsInLast15Minutes,
                steps30min = stepsInLast30Minutes,
                steps60min = stepsInLast60Minutes,
                device = device
            )
        )
        sendStepsRate(stepsList)
    }

    private fun getStepsInLastXMin(numberOf5MinIncrements: Int): Int {
        var stepCount = 0
        val thirtyMinAgo = currentTimeIn5Min() - numberOf5MinIncrements
        for (entry in stepsMap.entries) {
            if (entry.key > thirtyMinAgo) {
                stepCount += entry.value
            }
        }
        return stepCount
    }
}
