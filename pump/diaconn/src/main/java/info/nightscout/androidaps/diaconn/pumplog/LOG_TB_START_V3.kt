package info.nightscout.androidaps.diaconn.pumplog

import okhttp3.internal.and
import org.apache.commons.lang3.StringUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 임시기저 설정(시작)
*/
class LOG_TB_START_V3 private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte, // 임시기저 시간(30분 ~ 24시간, 2 ~ 96, 1당 15분 단위 증감)
    val tbTime: Byte,  // 임시기저 주입량/률, Rate: 1000(0.00U) ~ 1600(6.00U), Ratio: 50000(0%) ~ 50200(200%), 50000이상이면 주입률로 판정, 별도 계산/역산식 참조
    private val tbInjectRateRatio: Short,
    private val tbDttm: String // 앱에서 생성 전달한 임시기저 시작(요청) 시간
) {

    val type: Byte = PumplogUtil.getType(typeAndKind)
    val kind: Byte = PumplogUtil.getKind(typeAndKind)

    fun getTbInjectRateRatio(): Int {
        return tbInjectRateRatio and 0xffff
    }

    override fun toString(): String {
        val sb = StringBuilder("LOG_TB_START_V3{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", tbTime=").append(tbTime.toInt())
        sb.append(", tbInjectRateRatio=").append(tbInjectRateRatio and 0xffff)
        if (!StringUtils.equals(tbDttm, PumplogUtil.getDttm("ffffffff"))) {
            sb.append(", tbDttm=").append(tbDttm)
        }
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x12
        fun parse(data: String): LOG_TB_START_V3 {
            val bytes = PumplogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LOG_TB_START_V3(
                data,
                PumplogUtil.getDttm(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getShort(buffer),
                PumplogUtil.getDttm(buffer)
            )
        }
    }

}