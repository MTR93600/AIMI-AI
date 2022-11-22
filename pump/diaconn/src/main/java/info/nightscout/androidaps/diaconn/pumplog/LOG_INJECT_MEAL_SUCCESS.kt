package info.nightscout.androidaps.diaconn.pumplog

import okhttp3.internal.and
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 식사주입 성공
*/
class LOG_INJECT_MEAL_SUCCESS private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,    // 47.5=4750
    private val setAmount: Short,    // 47.5=4750
    val injectAmount: Short,    // 1분단위 주입시간(124=124분=2시간4분)
    private val injectTime: Byte,    // 아침=1, 점심=2, 저녁=3
    val time: Byte,
    batteryRemain: Byte
) {

    val type: Byte = PumplogUtil.getType(typeAndKind)
    val kind: Byte = PumplogUtil.getKind(typeAndKind)
    val batteryRemain: Byte = batteryRemain
    fun getInjectTime(): Int {
        return injectTime and 0xff
    }

    override fun toString(): String {
        val sb = StringBuilder("LOG_INJECT_MEAL_SUCCESS{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", setAmount=").append(setAmount.toInt())
        sb.append(", injectAmount=").append(injectAmount.toInt())
        sb.append(", injectTime=").append(injectTime and 0xff)
        sb.append(", time=").append(time.toInt())
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x08
        fun parse(data: String): LOG_INJECT_MEAL_SUCCESS {
            val bytes = PumplogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LOG_INJECT_MEAL_SUCCESS(
                data,
                PumplogUtil.getDttm(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getShort(buffer),
                PumplogUtil.getShort(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getByte(buffer)
            )
        }
    }

}