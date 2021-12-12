package info.nightscout.androidaps.plugins.pump.medtronic.util

import com.google.gson.GsonBuilder
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.PumpSettingDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.RLHistoryItemMedtronic
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicNotificationType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.joda.time.LocalTime
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.or

/**
 * Created by andy on 5/9/18.
 */
@Singleton
class MedtronicUtil @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val rileyLinkUtil: RileyLinkUtil,
    private val medtronicPumpStatus: MedtronicPumpStatus
) {

    private val ENVELOPE_SIZE = 4 // 0xA7 S1 S2 S3 CMD PARAM_COUNT [PARAMS]

    //private MedtronicDeviceType medtronicPumpModel;
    private var currentCommand: MedtronicCommandType? = null
    var settings: Map<String, PumpSettingDTO>? = null
    private val BIG_FRAME_LENGTH = 65
    private val doneBit = 1 shl 7
    var pumpTime: ClockDTO? = null
    var gsonInstance = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    fun getTimeFrom30MinInterval(interval: Int): LocalTime {
        return if (interval % 2 == 0) {
            LocalTime(interval / 2, 0)
        } else {
            LocalTime((interval - 1) / 2, 30)
        }
    }

    fun decodeBasalInsulin(i: Int, j: Int): Double {
        return decodeBasalInsulin(makeUnsignedShort(i, j))
    }

    fun decodeBasalInsulin(i: Int): Double {
        return i.toDouble() / 40.0
    }

    fun getBasalStrokes(amount: Double): ByteArray {
        return getBasalStrokes(amount, false)
    }

    fun getBasalStrokesInt(amount: Double): Int {
        return getStrokesInt(amount, 40)
    }

    fun getBolusStrokes(amount: Double): ByteArray {
        val strokesPerUnit = medtronicPumpStatus.medtronicDeviceType.bolusStrokes
        val length: Int
        val scrollRate: Int
        if (strokesPerUnit >= 40) {
            length = 2

            // 40-stroke pumps scroll faster for higher unit values
            scrollRate = if (amount > 10) 4 else if (amount > 1) 2 else 1
        } else {
            length = 1
            scrollRate = 1
        }
        val strokes = (amount * (strokesPerUnit * 1.0 / (scrollRate * 1.0))).toInt() * scrollRate
        return ByteUtil.fromHexString(String.format("%02x%0" + 2 * length + "x", length, strokes))
    }

    fun createCommandBody(input: ByteArray): ByteArray {
        return ByteUtil.concat(input.size.toByte(), input)
    }

    fun sendNotification(notificationType: MedtronicNotificationType, rh: ResourceHelper, rxBus: RxBus) {
        val notification = Notification( //
            notificationType.notificationType,  //
            rh.gs(notificationType.resourceId),  //
            notificationType.notificationUrgency)
        rxBus.send(EventNewNotification(notification))
    }

    fun sendNotification(notificationType: MedtronicNotificationType, rh: ResourceHelper, rxBus: RxBus, vararg parameters: Any?) {
        val notification = Notification( //
            notificationType.notificationType,  //
            rh.gs(notificationType.resourceId, *parameters),  //
            notificationType.notificationUrgency)
        rxBus.send(EventNewNotification(notification))
    }

    fun dismissNotification(notificationType: MedtronicNotificationType, rxBus: RxBus) {
        rxBus.send(EventDismissNotification(notificationType.notificationType))
    }

    fun buildCommandPayload(rileyLinkServiceData: RileyLinkServiceData, commandType: MedtronicCommandType, parameters: ByteArray?): ByteArray {
        return buildCommandPayload(rileyLinkServiceData, commandType.commandCode, parameters)
    }

    fun buildCommandPayload(rileyLinkServiceData: RileyLinkServiceData, commandType: Byte, parameters: ByteArray?): ByteArray {
        // A7 31 65 51 C0 00 52
        val commandLength = (if (parameters == null) 2 else 2 + parameters.size).toByte()
        val sendPayloadBuffer = ByteBuffer.allocate(ENVELOPE_SIZE + commandLength) // + CRC_SIZE
        sendPayloadBuffer.order(ByteOrder.BIG_ENDIAN)
        val serialNumberBCD = rileyLinkServiceData.pumpIDBytes
        sendPayloadBuffer.put(0xA7.toByte())
        sendPayloadBuffer.put(serialNumberBCD[0])
        sendPayloadBuffer.put(serialNumberBCD[1])
        sendPayloadBuffer.put(serialNumberBCD[2])
        sendPayloadBuffer.put(commandType)
        if (parameters == null) {
            sendPayloadBuffer.put(0x00.toByte())
        } else {
            sendPayloadBuffer.put(parameters.size.toByte()) // size
            for (`val` in parameters) {
                sendPayloadBuffer.put(`val`)
            }
        }
        val payload = sendPayloadBuffer.array()
        aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "buildCommandPayload [%s]", ByteUtil.shortHexString(payload)))

        // int crc = computeCRC8WithPolynomial(payload, 0, payload.length - 1);

        // LOG.info("crc: " + crc);

        // sendPayloadBuffer.put((byte) crc);
        return sendPayloadBuffer.array()
    }

    // Note: at the moment supported only for 24 items, if you will use it for more than
    // that you will need to add
    fun getBasalProfileFrames(data: ByteArray): List<List<Byte>> {
        var done = false
        var start = 0
        var frame = 1
        val frames: MutableList<List<Byte>> = ArrayList()
        var lastFrame = false
        do {
            var frameLength = BIG_FRAME_LENGTH - 1
            if (start + frameLength > data.size) {
                frameLength = data.size - start
            }

            // System.out.println("Framelength: " + frameLength);
            val substring = ByteUtil.substring(data, start, frameLength)

            // System.out.println("Subarray: " + ByteUtil.getCompactString(substring));
            // System.out.println("Subarray Lenths: " + substring.length);
            val frameData = ByteUtil.getListFromByteArray(substring)
            if (isEmptyFrame(frameData)) {
                var b = frame.toByte()
                // b |= 0x80;
                b = b or 128.toByte()
                // b |= doneBit;
                frameData.add(0, b)
                checkAndAppendLastFrame(frameData)
                lastFrame = true
                done = true
            } else {
                frameData.add(0, frame.toByte())
            }

            // System.out.println("Subarray: " + ByteUtil.getCompactString(substring));
            frames.add(frameData)
            frame++
            start += BIG_FRAME_LENGTH - 1
            if (start == data.size) {
                done = true
            }
        } while (!done)
        if (!lastFrame) {
            val frameData: MutableList<Byte> = ArrayList()
            var b = frame.toByte()
            b = b or 128.toByte()
            // b |= doneBit;
            frameData.add(b)
            checkAndAppendLastFrame(frameData)
        }
        return frames
    }

    private fun checkAndAppendLastFrame(frameData: MutableList<Byte>) {
        if (frameData.size == BIG_FRAME_LENGTH) return
        val missing = BIG_FRAME_LENGTH - frameData.size
        for (i in 0 until missing) {
            frameData.add(0x00.toByte())
        }
    }

    private fun isEmptyFrame(frameData: List<Byte>): Boolean {
        for (frameDateEntry in frameData) {
            if (frameDateEntry.toInt() != 0x00) {
                return false
            }
        }
        return true
    }

    var isModelSet: Boolean = false
    // get() = medtronicPumpStatus.medtronicDeviceType != null

    var medtronicPumpModel: MedtronicDeviceType
        get() = medtronicPumpStatus.medtronicDeviceType
        set(medtronicPumpModel) {
            medtronicPumpStatus.medtronicDeviceType = medtronicPumpModel
        }

    fun getCurrentCommand(): MedtronicCommandType? {
        return currentCommand
    }

    fun setCurrentCommand(currentCommandIn: MedtronicCommandType?) {
        this.currentCommand = currentCommandIn
        if (currentCommand != null) rileyLinkUtil.rileyLinkHistory.add(RLHistoryItemMedtronic(currentCommandIn!!))
    }

    var pageNumber = 0
    var frameNumber: Int? = null

    fun setCurrentCommand(currentCommand: MedtronicCommandType, pageNumber_: Int, frameNumber_: Int?) {
        pageNumber = pageNumber_
        frameNumber = frameNumber_
        if (this.currentCommand !== currentCommand) {
            setCurrentCommand(currentCommand)
        }
        rxBus.send(EventRileyLinkDeviceStatusChange(medtronicPumpStatus.pumpDeviceState))
    }

    companion object {

        const val isLowLevelDebug = true
        fun getIntervalFromMinutes(minutes: Int): Int {
            return minutes / 30
        }

        fun makeUnsignedShort(b2: Int, b1: Int): Int {
            return b2 and 0xff shl 8 or b1 and 0xff
        }

        fun getByteArrayFromUnsignedShort(shortValue: Int, returnFixedSize: Boolean): ByteArray {
            val highByte = (shortValue shr 8 and 0xFF).toByte()
            val lowByte = (shortValue and 0xFF).toByte()
            return if (highByte > 0) {
                createByteArray(highByte, lowByte)
            } else {
                if (returnFixedSize) createByteArray(highByte, lowByte) else createByteArray(lowByte)
            }
        }

        fun createByteArray(vararg data: Byte): ByteArray {
            return data
        }

        fun createByteArray(data: List<Byte>): ByteArray {
            val array = ByteArray(data.size)
            for (i in data.indices) {
                array[i] = data[i]
            }
            return array
        }

        fun getBasalStrokes(amount: Double, returnFixedSize: Boolean): ByteArray {
            return getStrokes(amount, 40, returnFixedSize)
        }

        fun getStrokes(amount: Double, strokesPerUnit: Int, returnFixedSize: Boolean): ByteArray {
            val strokes = getStrokesInt(amount, strokesPerUnit)
            return getByteArrayFromUnsignedShort(strokes, returnFixedSize)
        }

        fun getStrokesInt(amount: Double, strokesPerUnit: Int): Int {
            //var length = 1
            var scrollRate = 1
            if (strokesPerUnit >= 40) {
                //    length = 2

                // 40-stroke pumps scroll faster for higher unit values
                if (amount > 10) scrollRate = 4 else if (amount > 1) scrollRate = 2
            }
            var strokes = (amount * (strokesPerUnit / (scrollRate * 1.0))).toInt()
            strokes *= scrollRate
            return strokes
        }

        fun isSame(d1: Double, d2: Double): Boolean {
            val diff = d1 - d2
            return Math.abs(diff) <= 0.000001
        }
    }

}