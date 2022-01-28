package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

/**
 * InjectionBasalReportPacket
 */
class InjectionBasalReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    init {
        msgType = 0xCC.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionBasalReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "InjectionBasalReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        diaconnG8Pump.systemBasePattern =  getByteToInt(bufferData)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump Report BasalPattern --> ${diaconnG8Pump.systemBasePattern} (1:basic, 2: life1 , 3: life2 , 4: life3 , 5:dr1, 6:dr2) ")
    }

    override fun getFriendlyName(): String {
        return "PUMP_INJECTION_BASAL_REPORT"
    }
}