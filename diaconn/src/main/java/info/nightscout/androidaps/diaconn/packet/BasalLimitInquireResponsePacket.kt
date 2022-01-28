package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.diaconn.R
import info.nightscout.androidaps.diaconn.pumplog.PumplogUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

/**
 * BasalLimitInquireResponsePacket
 */
class BasalLimitInquireResponsePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper

    init {
        msgType = 0x92.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BasalLimitInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray?) {
        val result = defect(data)
        if (result != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BasalLimitInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result2 =  getByteToInt(bufferData)
        if(!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }
        diaconnG8Pump.maxBasalPerHours = getShortToInt(bufferData).toDouble() / 100.0  // not include tempbasal limit
        val pumpFirmwareVersion = sp.getString(rh.gs(R.string.pumpversion), "")
        if(pumpFirmwareVersion.isNotEmpty() && PumplogUtil.isPumpVersionGe(pumpFirmwareVersion, 3, 0)) {
            diaconnG8Pump.maxBasal =  diaconnG8Pump.maxBasalPerHours * 2.5 // include tempbasal
        } else {
            diaconnG8Pump.maxBasal =  diaconnG8Pump.maxBasalPerHours * 2.0 // include tempbasal
        }

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${diaconnG8Pump.result}")
        aapsLogger.debug(LTag.PUMPCOMM, "maxBasal --> ${diaconnG8Pump.maxBasal}")
    }

    override fun getFriendlyName(): String {
        return "PUMP_BASAL_LIMIT_INQUIRE_RESPONSE"
    }
}