package info.nightscout.androidaps.plugins.pump.common.dialog

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.pump.common.ble.BlePreCheck
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.GattAttributes
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.databinding.RileyLinkBleConfigActivityBinding
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import org.apache.commons.lang3.StringUtils
import java.util.*
import javax.inject.Inject

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class RileyLinkBLEConfigActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var rileyLinkUtil: RileyLinkUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var context: Context

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var deviceListAdapter = LeDeviceListAdapter()
    private var settings: ScanSettings? = null
    private var filters: List<ScanFilter>? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanning = false

    private lateinit var binding: RileyLinkBleConfigActivityBinding
    private val stopScanAfterTimeoutRunnable = Runnable {
        if (scanning) {
            stopLeDeviceScan()
            rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet, this) // Reconnect current RL
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RileyLinkBleConfigActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initializes Bluetooth adapter.
        binding.rileyLinkBleConfigScanDeviceList.adapter = deviceListAdapter
        binding.rileyLinkBleConfigScanDeviceList.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, view: View, _: Int, _: Long ->
            // stop scanning if still active
            if (scanning) stopLeDeviceScan()

            val bleAddress = (view.findViewById(R.id.riley_link_ble_config_scan_item_device_address) as TextView).text.toString()
            val deviceName = (view.findViewById(R.id.riley_link_ble_config_scan_item_device_name) as TextView).text.toString()
            sp.putString(RileyLinkConst.Prefs.RileyLinkAddress, bleAddress)
            sp.putString(RileyLinkConst.Prefs.RileyLinkName, deviceName)
            val rileyLinkPump = activePlugin.activePump as RileyLinkPumpDevice
            rileyLinkPump.rileyLinkService.verifyConfiguration(true) // force reloading of address to assure that the RL gets reconnected (even if the address didn't change)
            rileyLinkPump.triggerPumpConfigurationChangedEvent()
            finish()
        }
        binding.rileyLinkBleConfigScanStart.setOnClickListener {
            // disable currently selected RL, so that we can discover it
            rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkDisconnect, this)
            startLeDeviceScan()
        }
        binding.rileyLinkBleConfigButtonScanStop.setOnClickListener {
            if (scanning) {
                stopLeDeviceScan()
                rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet, this) // Reconnect current RL
            }
        }
        binding.rileyLinkBleConfigButtonRemoveRileyLink.setOnClickListener {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(R.string.riley_link_ble_config_remove_riley_link_confirmation_title))
                .setMessage(getString(R.string.riley_link_ble_config_remove_riley_link_confirmation))
                .setPositiveButton(getString(R.string.riley_link_common_yes)) { _: DialogInterface?, _: Int ->
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkDisconnect, this@RileyLinkBLEConfigActivity)
                    sp.remove(RileyLinkConst.Prefs.RileyLinkAddress)
                    sp.remove(RileyLinkConst.Prefs.RileyLinkName)
                    updateCurrentlySelectedRileyLink()
                }
                .setNegativeButton(getString(R.string.riley_link_common_no), null)
                .show()
        }
    }

    private fun updateCurrentlySelectedRileyLink() {
        val address = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "")
        if (StringUtils.isEmpty(address)) {
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkName.setText(R.string.riley_link_ble_config_no_riley_link_selected)
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkAddress.visibility = View.GONE
            binding.rileyLinkBleConfigButtonRemoveRileyLink.visibility = View.GONE
        } else {
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkAddress.visibility = View.VISIBLE
            binding.rileyLinkBleConfigButtonRemoveRileyLink.visibility = View.VISIBLE
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkName.text = sp.getString(RileyLinkConst.Prefs.RileyLinkName, "RileyLink (?)")
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkAddress.text = address
        }
    }

    override fun onResume() {
        super.onResume()
        prepareForScanning()
        updateCurrentlySelectedRileyLink()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (scanning) {
            stopLeDeviceScan()
            rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet, this) // Reconnect current RL
        }
    }

    private fun prepareForScanning() {
        val checkOK = blePreCheck.prerequisitesCheck(this)
        if (checkOK) {
            bleScanner = bluetoothAdapter?.bluetoothLeScanner
            settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            filters = listOf(
                ScanFilter.Builder().setServiceUuid(
                    ParcelUuid.fromString(GattAttributes.SERVICE_RADIO)
                ).build()
            )
        }
    }

    private val bleScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
            aapsLogger.debug(LTag.PUMPBTCOMM, scanRecord.toString())
            runOnUiThread { if (addDevice(scanRecord)) deviceListAdapter.notifyDataSetChanged() }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            runOnUiThread {
                var added = false
                for (result in results) {
                    if (addDevice(result)) added = true
                }
                if (added) deviceListAdapter.notifyDataSetChanged()
            }
        }

        private fun addDevice(result: ScanResult): Boolean {
            val device = result.device
            val serviceUuids = result.scanRecord?.serviceUuids
            if (serviceUuids == null || serviceUuids.size == 0) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Device " + device.address + " has no serviceUuids (Not RileyLink).")
            } else if (serviceUuids.size > 1) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Device " + device.address + " has too many serviceUuids (Not RileyLink).")
            } else {
                val uuid = serviceUuids[0].uuid.toString().lowercase(Locale.getDefault())
                if (uuid == GattAttributes.SERVICE_RADIO) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Found RileyLink with address: " + device.address)
                    deviceListAdapter.addDevice(result)
                    return true
                } else {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Device " + device.address + " has incorrect uuid (Not RileyLink).")
                }
            }
            return false
        }

        override fun onScanFailed(errorCode: Int) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Scan Failed", "Error Code: $errorCode")
            Toast.makeText(
                this@RileyLinkBLEConfigActivity, rh.gs(R.string.riley_link_ble_config_scan_error, errorCode),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startLeDeviceScan() {
        if (bleScanner == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "startLeDeviceScan failed: bleScanner is null")
            return
        }
        deviceListAdapter.clear()
        deviceListAdapter.notifyDataSetChanged()
        handler.postDelayed(stopScanAfterTimeoutRunnable, SCAN_PERIOD_MILLIS)
        runOnUiThread {
            binding.rileyLinkBleConfigScanStart.isEnabled = false
            binding.rileyLinkBleConfigButtonScanStop.visibility = View.VISIBLE
        }
        scanning = true
        bleScanner?.startScan(filters, settings, bleScanCallback)
        aapsLogger.debug(LTag.PUMPBTCOMM, "startLeDeviceScan: Scanning Start")
        Toast.makeText(this@RileyLinkBLEConfigActivity, R.string.riley_link_ble_config_scan_scanning, Toast.LENGTH_SHORT).show()
    }

    private fun stopLeDeviceScan() {
        if (scanning) {
            scanning = false
            bleScanner?.stopScan(bleScanCallback)
            aapsLogger.debug(LTag.PUMPBTCOMM, "stopLeDeviceScan: Scanning Stop")
            Toast.makeText(this, R.string.riley_link_ble_config_scan_finished, Toast.LENGTH_SHORT).show()
            handler.removeCallbacks(stopScanAfterTimeoutRunnable)
        }
        runOnUiThread {
            binding.rileyLinkBleConfigScanStart.isEnabled = true
            binding.rileyLinkBleConfigButtonScanStop.visibility = View.GONE
        }
    }

    private inner class LeDeviceListAdapter : BaseAdapter() {

        private val leDevices: ArrayList<BluetoothDevice> = ArrayList()
        private val rileyLinkDevices: MutableMap<BluetoothDevice, Int> = HashMap()

        fun addDevice(result: ScanResult) {
            if (!leDevices.contains(result.device)) {
                leDevices.add(result.device)
            }
            rileyLinkDevices[result.device] = result.rssi
            notifyDataSetChanged()
        }

        fun clear() {
            leDevices.clear()
            rileyLinkDevices.clear()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = leDevices.size
        override fun getItem(i: Int): Any = leDevices[i]
        override fun getItemId(i: Int): Long = i.toLong()

        @SuppressLint("InflateParams")
        override fun getView(i: Int, v: View?, viewGroup: ViewGroup): View {
            var view = v
            val viewHolder: ViewHolder
            // General ListView optimization code.
            if (view == null) {
                view = View.inflate(applicationContext, R.layout.riley_link_ble_config_scan_item, null)
                viewHolder = ViewHolder(view)
                view.tag = viewHolder
            } else viewHolder = view.tag as ViewHolder

            val device = leDevices[i]
            var deviceName = device.name
            if (StringUtils.isBlank(deviceName)) deviceName = "RileyLink (?)"
            deviceName += " [" + rileyLinkDevices[device] + "]"
            val currentlySelectedAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "")
            if (currentlySelectedAddress == device.address) {
                deviceName += " (" + resources.getString(R.string.riley_link_ble_config_scan_selected) + ")"
            }
            viewHolder.deviceName.text = deviceName
            viewHolder.deviceAddress.text = device.address
            return view!!
        }
    }

    internal class ViewHolder(view: View) {

        val deviceName: TextView = view.findViewById(R.id.riley_link_ble_config_scan_item_device_name)
        val deviceAddress: TextView = view.findViewById(R.id.riley_link_ble_config_scan_item_device_address)
    }

    companion object {

        private const val SCAN_PERIOD_MILLIS: Long = 15000
    }
}