package info.nightscout.androidaps.diaconn.pumplog

import okhttp3.internal.and
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 듀얼(일반) 주입량: 듀얼(일반) 주입 완료 시 기록하는 방식
*/
class LOG_INJECTION_DUAL_NORMAL private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,    // 설정량 47.5=4750
    val setAmount: Short,    // 주입량 47.5=4750
    val injectAmount: Short,    // 1분 단위 주입 시간 Ex) 124 = 124분 = 2시간 4분
    private val injectTime: Byte,
    val batteryRemain: Byte
) {

    val type: Byte = PumplogUtil.getType(typeAndKind)
    val kind: Byte = PumplogUtil.getKind(typeAndKind)
    fun getInjectTime(): Int {
        return injectTime and 0xff
    }

    override fun toString(): String {
        val sb = StringBuilder("LOG_INJECTION_DUAL_NORMAL{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", setAmount=").append(setAmount.toInt())
        sb.append(", injectAmount=").append(injectAmount.toInt())
        sb.append(", injectTime=").append(injectTime and 0xff)
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x35
        fun parse(data: String): LOG_INJECTION_DUAL_NORMAL {
            val bytes = PumplogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LOG_INJECTION_DUAL_NORMAL(
                data,
                PumplogUtil.getDttm(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getShort(buffer),
                PumplogUtil.getShort(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getByte(buffer)
            )
        }
    }

}