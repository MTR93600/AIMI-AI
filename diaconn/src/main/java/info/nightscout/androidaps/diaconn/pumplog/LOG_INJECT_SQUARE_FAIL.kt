package info.nightscout.androidaps.diaconn.pumplog

import okhttp3.internal.and
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 스퀘어주입 실패
*/
class LOG_INJECT_SQUARE_FAIL private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,    // 47.5=4750
    val injectAmount: Short,    // 1분단위 주입시간(124=124분=2시간4분)
    private val injectTime: Byte,    // 1=주입막힘, 2=배터리잔량부족, 3=약물부족, 4=사용자중지, 5=시스템리셋, 6=기타, 7=긴급정지
    val reason: Byte,
    val batteryRemain: Byte
) {

    val type: Byte = PumplogUtil.getType(typeAndKind)
    val kind: Byte = PumplogUtil.getKind(typeAndKind)

    fun getInjectTime(): Int {
        return injectTime and 0xff
    }

    override fun toString(): String {
        val sb = StringBuilder("LOG_INJECT_SQUARE_FAIL{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", injectAmount=").append(injectAmount.toInt())
        sb.append(", injectTime=").append(injectTime and 0xff)
        sb.append(", reason=").append(reason.toInt())
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x0E
        fun parse(data: String): LOG_INJECT_SQUARE_FAIL {
            val bytes = PumplogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LOG_INJECT_SQUARE_FAIL(
                data,
                PumplogUtil.getDttm(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getShort(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getByte(buffer)
            )
        }
    }

}