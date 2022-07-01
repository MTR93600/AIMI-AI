package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.databinding.RileylinkStatusGeneralBinding
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import org.joda.time.LocalDateTime
import javax.inject.Inject

class RileyLinkStatusGeneralFragment : DaggerFragment() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP

    private var _binding: RileylinkStatusGeneralBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        RileylinkStatusGeneralBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.refresh.setOnClickListener { refreshData() }
    }

    private fun refreshData() {
        val targetDevice = rileyLinkServiceData.targetDevice
        binding.connectionStatus.text = rh.gs(rileyLinkServiceData.rileyLinkServiceState.resourceId)
        binding.configuredRileyLinkAddress.text = rileyLinkServiceData.rileyLinkAddress ?: EMPTY
        binding.configuredRileyLinkName.text = rileyLinkServiceData.rileyLinkName ?: EMPTY
        if (sp.getBoolean(rh.gs(R.string.key_riley_link_show_battery_level), false)) {
            binding.batteryLevelRow.visibility = View.VISIBLE
            val batteryLevel = rileyLinkServiceData.batteryLevel
            binding.batteryLevel.text = batteryLevel?.let { rh.gs(R.string.rileylink_battery_level_value, it) } ?: EMPTY
        } else binding.batteryLevelRow.visibility = View.GONE
        binding.connectionError.text = rileyLinkServiceData.rileyLinkError?.let { rh.gs(it.getResourceId(targetDevice)) } ?: EMPTY
        if (rileyLinkServiceData.isOrange && rileyLinkServiceData.versionOrangeFirmware != null) {
            binding.firmwareVersion.text = rh.gs(
                R.string.rileylink_firmware_version_value_orange,
                rileyLinkServiceData.versionOrangeFirmware,
                rileyLinkServiceData.versionOrangeHardware ?: EMPTY
            )
        } else {
            binding.firmwareVersion.text = rh.gs(
                R.string.rileylink_firmware_version_value,
                rileyLinkServiceData.versionBLE113 ?: EMPTY,
                rileyLinkServiceData.versionCC110 ?: EMPTY
            )
        }
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
        if (rileyLinkServiceData.lastGoodFrequency != null) {
            binding.lastUsedFrequency.text = rh.gs(R.string.rileylink_pump_frequency_value, rileyLinkServiceData.lastGoodFrequency)
        }
        val lastConnectionTimeMillis = rileyLinkPumpDevice.lastConnectionTimeMillis
        if (lastConnectionTimeMillis == 0L) binding.lastDeviceContact.text = rh.gs(R.string.riley_link_ble_config_connected_never)
        else binding.lastDeviceContact.text = StringUtil.toDateTimeString(dateUtil, LocalDateTime(lastConnectionTimeMillis))
    }

    companion object {

        private const val EMPTY = "-"
    }
}