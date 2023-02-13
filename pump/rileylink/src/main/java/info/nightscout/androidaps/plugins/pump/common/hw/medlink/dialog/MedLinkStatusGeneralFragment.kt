package info.nightscout.androidaps.plugins.pump.common.hw.medlink.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.databinding.RileylinkStatusGeneralBinding
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.pump.core.utils.StringUtil
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.joda.time.LocalDateTime
import javax.inject.Inject

/**
 * Created by andy on 5/19/18.
 */
class MedLinkStatusGeneralFragment : DaggerFragment() {

     @Inject
    lateinit var activePlugin: ActivePlugin

    @Inject
    lateinit var resourceHelper: ResourceHelper

    @Inject
    lateinit var medLinkServiceData: MedLinkServiceData

     @Inject
     lateinit  var dateUtil: DateUtil

     @Inject
   lateinit var sp: SP
    var rileyLinkName: String? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.rileylink_status_general, container, false)
    }

    private var _binding: RileylinkStatusGeneralBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun refreshData() {
        val targetDevice = medLinkServiceData.targetDevice
        binding.connectionStatus.text = resourceHelper.gs(medLinkServiceData.medLinkServiceState.resourceId)
        binding.configuredRileyLinkAddress.text = medLinkServiceData.rileylinkAddress ?: EMPTY
        binding.configuredRileyLinkName.text = medLinkServiceData.rileylinkName ?: EMPTY
        if (sp.getBoolean(resourceHelper.gs(R.string.key_riley_link_show_battery_level), false)) {
            binding.batteryLevelRow.visibility = View.VISIBLE
            val batteryLevel = medLinkServiceData.batteryLevel
            binding.batteryLevel.text = batteryLevel?.let { resourceHelper.gs(R.string.rileylink_battery_level_value, it) } ?: EMPTY
        } else binding.batteryLevelRow.visibility = View.GONE
        binding.connectionError.text = medLinkServiceData.medLinkError?.let { resourceHelper.gs(it.getResourceId(targetDevice)) } ?: EMPTY
        // if (medLinkServiceData.isOrange && medLinkServiceData.versionBLE113 != null) {
        //     binding.firmwareVersion.text = resourceHelper.gs(
        //         R.string.rileylink_firmware_version_value_orange,
        //         medLinkServiceData.versionOrangeFirmware,
        //         medLinkServiceData.versionOrangeHardware ?: RileyLinkStatusGeneralFragment.EMPTY
        //     )
        // } else {
        //     binding.firmwareVersion.text = resourceHelper.gs(
        //         R.string.rileylink_firmware_version_value,
        //         medLinkServiceData.versionBLE113 ?: RileyLinkStatusGeneralFragment.EMPTY,
        //         medLinkServiceData.versionCC110 ?: RileyLinkStatusGeneralFragment.EMPTY
        //     )
        // }
        val rileyLinkPumpDevice = activePlugin.activePump as RileyLinkPumpDevice
        val rileyLinkPumpInfo = rileyLinkPumpDevice.pumpInfo
        targetDevice?.resourceId?.let { binding.deviceType.setText(it) }
        if (targetDevice == RileyLinkTargetDevice.MedtronicPump) {
            binding.connectedDeviceDetails.visibility = View.VISIBLE
            binding.configuredDeviceModel.text = activePlugin.activePump.pumpDescription.pumpType.description
            binding.connectedDeviceModel.text = rileyLinkPumpInfo.connectedDeviceModel
        } else binding.connectedDeviceDetails.visibility = View.GONE
        binding.serialNumber.text = rileyLinkPumpInfo.connectedDeviceSerialNumber
        binding.pumpFrequency.text = rileyLinkPumpInfo.pumpFrequency
        if (medLinkServiceData.lastGoodFrequency != null) {
            binding.lastUsedFrequency.text = resourceHelper.gs(R.string.rileylink_pump_frequency_value, medLinkServiceData.lastGoodFrequency)
        }
        val lastConnectionTimeMillis = rileyLinkPumpDevice.lastConnectionTimeMillis
        if (lastConnectionTimeMillis == 0L) binding.lastDeviceContact.text = resourceHelper.gs(R.string.riley_link_ble_config_connected_never)
        else binding.lastDeviceContact.text = StringUtil.toDateTimeString(LocalDateTime(lastConnectionTimeMillis))
    }


    companion object {

        private const val EMPTY= "-"
    }
}