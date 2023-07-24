package info.nightscout.androidaps.wearStepCount

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import info.nightscout.androidaps.comm.IntentWearToMobile
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.EventData
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

/**
 * Gets heart rate readings from watch and sends them to the phone.
 *
 * The Android API doesn't define how often heart rate events are sent do the
 * listener, it could be once per second or only when the heart rate changes.
 *
 * Heart rate is not a point in time measurement but is always sampled over a
 * certain time, i.e. you count the the number of heart beats and divide by the
 * minutes that have passed. Therefore, the provided value has to be for the past.
 * However, we ignore this here.
 *
 * We don't need very exact values, but rather values that are easy to consume
 * and don't produce too much data that would cause much battery consumption.
 * Therefore, this class averages the heart rate over a minute ([samplingIntervalMillis])
 * and sends this value to the phone.
 *
 * We will not always get valid values, e.g. if the watch is taken of. The listener
 * ignores such time unless we don't get good values for more than 90% of time. Since
 * heart rate doesn't change so fast this should be good enough.
 */
class stepCountListener(
    private val ctx: Context,
    private val aapsLogger: AAPSLogger,
    aapsSchedulers: AapsSchedulers,
    now: Long = System.currentTimeMillis(),
) :  SensorEventListener, Disposable {

    /** How often we send values to the phone. */
    private val samplingIntervalMillis = 300_000L
    private val sampler = Sampler(now)
    private var schedule: Disposable? = null

    init {
        aapsLogger.info(LTag.WEAR, "Create ${javaClass.simpleName}")
        val sensorManager = ctx.getSystemService(SENSOR_SERVICE) as SensorManager?
        if (sensorManager == null) {
            aapsLogger.warn(LTag.WEAR, "Cannot get sensor manager to get steps rate readings")
        } else {
            val stepsRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepsRateSensor == null) {
                aapsLogger.warn(LTag.WEAR, "Cannot get steps rate sensor")
            } else {
                sensorManager.registerListener(this, stepsRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        schedule = aapsSchedulers.io.schedulePeriodicallyDirect(
            ::send, samplingIntervalMillis, samplingIntervalMillis, TimeUnit.MILLISECONDS)
    }

    val currentStepsRateBpm get() = sampler.currentTotalSteps

    @VisibleForTesting
    var sendStepsRate: (EventData.ActionStepsRate)->Unit = { steps -> ctx.startService(IntentWearToMobile(ctx, steps)) }

    override fun isDisposed() = schedule == null

    override fun dispose() {
        aapsLogger.info(LTag.WEAR, "Dispose ${javaClass.simpleName}")
        schedule?.dispose()
        (ctx.getSystemService(SENSOR_SERVICE) as SensorManager?)?.unregisterListener(this)
    }

    private fun send() {
        send(System.currentTimeMillis())
    }

    @VisibleForTesting
    fun send(timestampMillis: Long) {
        sampler.getAndReset(timestampMillis)?.let { steps ->
            aapsLogger.info(LTag.WEAR, "Send Steps count $steps")
            sendStepsRate(steps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Ignored
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor?.type == Sensor.TYPE_STEP_COUNTER && event.values.isNotEmpty()) {
            val steps = event.values[0].toInt()
            sampler.setStepsRate(System.currentTimeMillis(), steps, aapsLogger)
        }
    }

    private class Sampler(timestampMillis: Long) {
        private var startMillis: Long = timestampMillis
        private var lastEventMillis: Long = timestampMillis
        private var steps: Int = 0
        private var activeMillis: Long = 0
        private val device = (Build.MANUFACTURER ?: "unknown") + " " + (Build.MODEL ?: "unknown")

        var currentTotalSteps: Int? = null
            private set(value) { field = value }

        private fun Long.toMinute(): Double = this / 60_000.0

        private fun fix(timestampMillis: Long) {
            currentTotalSteps?.let { bpm ->
                val elapsed = timestampMillis - lastEventMillis
                steps += (elapsed.toMinute() * bpm).toInt()
                activeMillis += elapsed
            }
            lastEventMillis = timestampMillis
        }

        fun getAndReset(timestampMillis: Long): EventData.ActionStepsRate? {
            fix(timestampMillis)
            return if (10 * activeMillis > lastEventMillis - startMillis) {
                val bpm = (steps / activeMillis.toMinute()).toInt()
                EventData.ActionStepsRate(timestampMillis - startMillis, timestampMillis, bpm, device)
            } else {
                null
            }.also {
                startMillis = timestampMillis
                lastEventMillis = timestampMillis
                steps = 0
                activeMillis = 0
            }
        }

        fun setStepsRate(timestampMillis: Long, totalStepCount: Int?, aapsLogger: AAPSLogger) {
            if (timestampMillis >= lastEventMillis) {
                fix(timestampMillis)
                currentTotalSteps = totalStepCount
            }
        }
    }
}

