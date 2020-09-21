package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_Bolus_Get_24_CIR_CF_Array(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_24_CIR_CF_ARRAY
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        danaPump.units = byteArrayToInt(getBytes(data, DATA_START, 1))
        for (i in 0 .. 23) {
            val cf =  byteArrayToInt(getBytes(data, DATA_START + 1 + 2 * i, 2)).toDouble()
            val cir =  if (danaPump.units == DanaPump.UNITS_MGDL)
                byteArrayToInt(getBytes(data, DATA_START + 1 + 48 + 2 * i, 2)).toDouble()
            else
                byteArrayToInt(getBytes(data, DATA_START + 1 + 48 + 2 * i, 2)) / 100.0
            danaPump.cir24[i] = cir
            danaPump.cf24[i] = cf
            aapsLogger.debug(LTag.PUMPCOMM, "$i: CIR: $cir  CF: $cf")
        }
        if (danaPump.units < 0 || danaPump.units > 1) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaPump.units == DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_24_ CIR_CF_ARRAY"
    }
}