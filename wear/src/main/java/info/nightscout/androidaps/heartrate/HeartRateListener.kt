package info.nightscout.androidaps.heartrate

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
class HeartRateListener(
    private val ctx: Context,
    private val aapsLogger: AAPSLogger,
    aapsSchedulers: AapsSchedulers,
    now: Long = System.currentTimeMillis(),
) :  SensorEventListener, Disposable {

    /** How often we send values to the phone. */
    private val samplingIntervalMillis = 60_000L
    private val sampler = Sampler(now)
    private var schedule: Disposable? = null

    /** We only use values with these accuracies and ignore NO_CONTACT and UNRELIABLE. */
    private val goodAccuracies = arrayOf(
        SensorManager.SENSOR_STATUS_ACCURACY_LOW,
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    )

    init {
        aapsLogger.info(LTag.WEAR, "Create ${javaClass.simpleName}")
        val sensorManager = ctx.getSystemService(SENSOR_SERVICE) as SensorManager?
        if (sensorManager == null) {
            aapsLogger.warn(LTag.WEAR, "Cannot get sensor manager to get heart rate readings")
        } else {
            val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            if (heartRateSensor == null) {
                aapsLogger.warn(LTag.WEAR, "Cannot get heart rate sensor")
            } else {
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        schedule = aapsSchedulers.io.schedulePeriodicallyDirect(
            ::send, samplingIntervalMillis, samplingIntervalMillis, TimeUnit.MILLISECONDS)
    }

    /**
     * Gets the most recent heart rate reading and null if there is no valid
     * value at the moment.
     */
    val currentHeartRateBpm get() = sampler.currentBpm?.roundToInt()

    @VisibleForTesting
    var sendHeartRate: (EventData.ActionHeartRate)->Unit = { hr -> ctx.startService(IntentWearToMobile(ctx, hr)) }

    override fun isDisposed() = schedule == null

    override fun dispose() {
        aapsLogger.info(LTag.WEAR, "Dispose ${javaClass.simpleName}")
        schedule?.dispose()
        (ctx.getSystemService(SENSOR_SERVICE) as SensorManager?)?.unregisterListener(this)
    }

    /** Sends currently sampled value to the phone. Executed every [samplingIntervalMillis]. */
    private fun send() {
        send(System.currentTimeMillis())
    }

    @VisibleForTesting
    fun send(timestampMillis: Long) {
        sampler.getAndReset(timestampMillis)?.let { hr ->
            aapsLogger.info(LTag.WEAR, "Send heart rate $hr")
            sendHeartRate(hr)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        onAccuracyChanged(sensor.type, accuracy, System.currentTimeMillis())
    }

    @VisibleForTesting
    fun onAccuracyChanged(sensorType: Int, accuracy: Int, timestampMillis: Long) {
        if (sensorType != Sensor.TYPE_HEART_RATE) {
            aapsLogger.error(LTag.WEAR, "Invalid SensorEvent $sensorType $accuracy")
            return
        }
        if (accuracy !in goodAccuracies) sampler.setHeartRate(timestampMillis, null)
    }

    override fun onSensorChanged(event: SensorEvent) {
        onSensorChanged(event.sensor?.type, event.accuracy, System.currentTimeMillis(), event.values)
    }

    @VisibleForTesting
    fun onSensorChanged(sensorType: Int?, accuracy: Int, timestampMillis: Long, values: FloatArray) {
        if (sensorType == null || sensorType != Sensor.TYPE_HEART_RATE || values.isEmpty()) {
            aapsLogger.error(LTag.WEAR, "Invalid SensorEvent $sensorType $accuracy $timestampMillis ${values.joinToString()}")
            return
        }
        val heartRate = values[0].toDouble().takeIf { accuracy in goodAccuracies }
        sampler.setHeartRate(timestampMillis, heartRate)
    }

    private class Sampler(timestampMillis: Long) {
        private var startMillis: Long = timestampMillis
        private var lastEventMillis: Long = timestampMillis
        /** Number of heart beats sampled so far. */
        private var beats: Double = 0.0
        /** Time we could sample valid values during the current sampling interval. */
        private var activeMillis: Long = 0
        private val device = (Build.MANUFACTURER ?: "unknown") + " " + (Build.MODEL ?: "unknown")
        private val lock = ReentrantLock()

        var currentBpm: Double? = null
            get() = field
            private set(value) { field = value }

        private fun Long.toMinute(): Double = this / 60_000.0

        private fun fix(timestampMillis: Long) {
            currentBpm?.let { bpm ->
                val elapsed = timestampMillis - lastEventMillis
                beats += elapsed.toMinute() * bpm
                activeMillis += elapsed
            }
            lastEventMillis = timestampMillis
        }

        /** Gets the current sampled value and resets the samplers clock to the given timestamp. */
        fun getAndReset(timestampMillis: Long): EventData.ActionHeartRate? {
            lock.withLock {
                fix(timestampMillis)
                return if (10 * activeMillis > lastEventMillis - startMillis) {
                    val bpm = beats / activeMillis.toMinute()
                    EventData.ActionHeartRate(timestampMillis - startMillis, timestampMillis, bpm, device)
                } else {
                    null
                }.also {
                    startMillis = timestampMillis
                    lastEventMillis = timestampMillis
                    beats = 0.0
                    activeMillis = 0
                }
            }
        }

        fun setHeartRate(timestampMillis: Long, heartRate: Double?) {
            lock.withLock {
                if (timestampMillis < lastEventMillis) return
                fix(timestampMillis)
                currentBpm = heartRate
            }
        }
    }
}
