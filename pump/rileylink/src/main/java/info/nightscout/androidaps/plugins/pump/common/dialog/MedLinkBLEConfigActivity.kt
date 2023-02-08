package info.nightscout.androidaps.plugins.pump.common.dialog

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.core.app.ActivityCompat
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst.DEVICE_NAME
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.GattAttributes
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.databinding.MedLinkBleConfigActivityBinding
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.BlePreCheck
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.apache.commons.lang3.StringUtils
import java.util.*
import javax.inject.Inject

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class MedLinkBLEConfigActivity : DaggerAppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var medLinkUtil: MedLinkUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var context: Context
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var deviceListAdapter = LeDeviceListAdapter()
    private var settings: ScanSettings? = null
    private var filters: List<ScanFilter>? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanning = false

    private lateinit var binding: MedLinkBleConfigActivityBinding
    private val stopScanAfterTimeoutRunnable = Runnable {
        if (scanning) {
            stopLeDeviceScan()
            medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkNewAddressSet, this) // Reconnect current RL
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MedLinkBleConfigActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initializes Bluetooth adapter.
        binding.medLinkBleConfigScanDeviceList.adapter = deviceListAdapter
        binding.medLinkBleConfigScanDeviceList.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, view: View, _: Int, _: Long ->
            // stop scanning if still active
            if (scanning) stopLeDeviceScan()

            val bleAddress = (view.findViewById(R.id.med_link_ble_config_scan_item_device_address) as TextView).text.toString()
            val deviceName = (view.findViewById(R.id.med_link_ble_config_scan_item_device_name) as TextView).text.toString()
            sp.putString(MedLinkConst.Prefs.MedLinkAddress, bleAddress)
            sp.putString(MedLinkConst.Prefs.MedLinkName, deviceName)
            val rileyLinkPump = activePlugin.activePump as MedLinkPumpDevice
            rileyLinkPump.getRileyLinkService()?.verifyConfiguration() // force reloading of address to assure that the RL gets reconnected (even if the address didn't change)
            rileyLinkPump.triggerPumpConfigurationChangedEvent()
            finish()
        }
        binding.medLinkBleConfigScanStart.setOnClickListener {
            // disable currently selected RL, so that we can discover it
            medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkDisconnect, this)
            startLeDeviceScan()
        }
        binding.medLinkBleConfigButtonScanStop.setOnClickListener {
            if (scanning) {
                stopLeDeviceScan()
                medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkNewAddressSet, this) // Reconnect current RL
            }
        }
        binding.medLinkBleConfigButtonRemoveMedLink.setOnClickListener {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(R.string.med_link_ble_config_remove_med_link_confirmation_title))
                .setMessage(getString(R.string.med_link_ble_config_remove_med_link_confirmation))
                .setPositiveButton(getString(R.string.riley_link_common_yes)) { _: DialogInterface?, _: Int ->
                    medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkDisconnect, this@MedLinkBLEConfigActivity)
                    sp.remove(MedLinkConst.Prefs.MedLinkAddress)
                    sp.remove(MedLinkConst.Prefs.MedLinkName)
                    updateCurrentlySelectedRileyLink()
                }
                .setNegativeButton(getString(R.string.riley_link_common_no), null)
                .show()
        }
    }

    private fun updateCurrentlySelectedRileyLink() {
        val address = sp.getString(MedLinkConst.Prefs.MedLinkAddress, "")
        if (StringUtils.isEmpty(address)) {
            binding.medLinkBleConfigCurrentlySelectedMedLinkName.setText(R.string.med_link_ble_config_no_med_link_selected)
            binding.medLinkBleConfigCurrentlySelectedMedLinkAddress.visibility = View.GONE
            binding.medLinkBleConfigButtonRemoveMedLink.visibility = View.GONE
        } else {
            binding.medLinkBleConfigCurrentlySelectedMedLinkAddress.visibility = View.VISIBLE
            binding.medLinkBleConfigButtonRemoveMedLink.visibility = View.VISIBLE
            binding.medLinkBleConfigCurrentlySelectedMedLinkName.text = sp.getString(MedLinkConst.Prefs.MedLinkName, "MedLink (?)")
            binding.medLinkBleConfigCurrentlySelectedMedLinkAddress.text = address
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
            medLinkUtil.sendBroadcastMessage(MedLinkConst.Intents.MedLinkNewAddressSet, this) // Reconnect current RL
        }
    }

    private fun prepareForScanning() {
        val checkOK = blePreCheck.prerequisitesCheck(this)
        if (checkOK) {
            bleScanner = bluetoothAdapter?.bluetoothLeScanner
            settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            filters = DEVICE_NAME.map { f ->
                ScanFilter.Builder().setDeviceName(f).build()
            }
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
                if (uuid == GattAttributes.MED_LINK_SERVICE_RADIO) {
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
                this@MedLinkBLEConfigActivity, rh.gs(R.string.riley_link_ble_config_scan_error, errorCode),
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
            binding.medLinkBleConfigScanStart.isEnabled = false
            binding.medLinkBleConfigButtonScanStop.visibility = View.VISIBLE
        }
        scanning = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {

            bleScanner?.startScan(filters, settings, bleScanCallback)

            aapsLogger.debug(LTag.PUMPBTCOMM, "startLeDeviceScan: Scanning Start")
            Toast.makeText(this@MedLinkBLEConfigActivity, R.string.riley_link_ble_config_scan_scanning, Toast.LENGTH_SHORT).show()

        }
    }

    private fun stopLeDeviceScan() {
        if (scanning) {
            scanning = false
            if (bluetoothAdapter?.isEnabled == true && bluetoothAdapter?.state == BluetoothAdapter.STATE_ON)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bleScanner?.stopScan(bleScanCallback)
                }
            aapsLogger.debug(LTag.PUMPBTCOMM, "stopLeDeviceScan: Scanning Stop")
            Toast.makeText(this, R.string.riley_link_ble_config_scan_finished, Toast.LENGTH_SHORT).show()
            handler.removeCallbacks(stopScanAfterTimeoutRunnable)
        }

        runOnUiThread {
            binding.medLinkBleConfigScanStart.isEnabled = true
            binding.medLinkBleConfigButtonScanStop.visibility = View.GONE
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

        @SuppressLint("InflateParams", "MissingPermission")
        override fun getView(i: Int, v: View?, viewGroup: ViewGroup): View {
            var view = v
            val viewHolder: ViewHolder
            // General ListView optimization code.
            if (view == null) {
                view = View.inflate(applicationContext, R.layout.medlink_scan_activity, null)
                viewHolder = ViewHolder(view)
                view.tag = viewHolder
            } else viewHolder = view.tag as ViewHolder

            val device = leDevices[i]
            var deviceName = device.name
            if (StringUtils.isBlank(deviceName)) deviceName = "MedLink (?)"
            deviceName += " [" + rileyLinkDevices[device] + "]"
            val currentlySelectedAddress = sp.getString(MedLinkConst.Prefs.MedLinkAddress, "")
            if (currentlySelectedAddress == device.address) {
                deviceName += " (" + resources.getString(R.string.riley_link_ble_config_scan_selected) + ")"
            }
            viewHolder.deviceName.text = deviceName
            viewHolder.deviceAddress.text = device.address
            return view!!
        }
    }

    internal class ViewHolder(view: View) {

        val deviceName: TextView = view.findViewById(R.id.med_link_ble_config_scan_item_device_name)
        val deviceAddress: TextView = view.findViewById(R.id.med_link_ble_config_scan_item_device_address)
    }

    companion object {

        private const val SCAN_PERIOD_MILLIS: Long = 15000
    }
}