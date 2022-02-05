package info.nightscout.androidaps.plugins.pump.common.dialog

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import info.nightscout.androidaps.utils.ToastUtils.showToastInUiThread
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import javax.inject.Inject
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.plugins.pump.common.ble.BlePreCheck
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkUtil
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.MedLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkPumpDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.R
import info.nightscout.shared.logging.LTag
import org.apache.commons.lang3.StringUtils
import java.util.ArrayList
import java.util.HashMap
import java.util.stream.Collectors

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
/**
 * Created by dirceu on 9/20/20
 */
class MedLinkBLEScanActivity : NoSplashAppCompatActivity() {


    @Inject
    lateinit var sp: SP

    @Inject
    lateinit var rxBus: RxBus

    @Inject
    lateinit var resourceHelper: ResourceHelper

    @Inject
    lateinit var blePrecheck: BlePreCheck

    @Inject
    lateinit var medLinkUtil: MedLinkUtil

    @Inject
    lateinit var activePlugin: ActivePlugin
    var mScanning = false
    var settings: ScanSettings? = null
    var filters: List<ScanFilter>? = null
    var listBTScan: ListView? = null
    var toolbarBTScan: Toolbar? = null
    var mContext: Context = this
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mLEScanner: BluetoothLeScanner? = null
    private var mLeDeviceListAdapter: LeDeviceListAdapter? = null
    private var mHandler: Handler? = null
    private var actionTitleStart: String? = null
    private var actionTitleStop: String? = null
    private var menuItem: MenuItem? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.medlink_scan_activity)

        // Initializes Bluetooth adapter.
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        mHandler = Handler()
        mLeDeviceListAdapter = LeDeviceListAdapter()
        val listBTScan = findViewById<ListView>(R.id.medlink_listBTScan)
        listBTScan.adapter = mLeDeviceListAdapter
        listBTScan.onItemClickListener = AdapterView.OnItemClickListener { parent: AdapterView<*>?, view: View, position: Int, id: Long ->
        this.listBTScan = listBTScan

            // stop scanning if still active
            if (mScanning) {
                mScanning = false
                mLEScanner!!.stopScan(mScanCallback2)
            }
            val textview = view.findViewById<TextView>(R.id.riley_link_ble_config_scan_item_device_address)
            val bleAddress = textview.text.toString()
            sp!!.putString(MedLinkConst.Prefs.MedLinkAddress, bleAddress)
            val medlinkPumpDevice = activePlugin!!.activePump as MedLinkPumpDevice
            medlinkPumpDevice.getRileyLinkService()!!.verifyConfiguration() // force reloading of address
            medlinkPumpDevice.triggerPumpConfigurationChangedEvent()
            finish()
        }
        toolbarBTScan = findViewById(R.id.rileylink_toolbarBTScan)
        findViewById<Toolbar>(R.id.rileylink_toolbarBTScan).setTitle(R.string.medlink_scanner_title)
        setSupportActionBar(toolbarBTScan)
        prepareForScanning()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_medlink_ble_scan, menu)
        actionTitleStart = resourceHelper!!.gs(R.string.medlink_scanner_title)
        actionTitleStop = resourceHelper!!.gs(R.string.riley_link_ble_config_scan_stop)
        menuItem = menu.getItem(0)
        menu.getItem(0).title = actionTitleStart
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.title == actionTitleStart) {
            scanLeDevice(true)
            true
        } else if (item.title == actionTitleStop) {
            scanLeDevice(false)
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    fun prepareForScanning() {
        val checkOK = blePrecheck!!.prerequisitesCheck(this)
        if (checkOK) {
            mLEScanner = mBluetoothAdapter!!.bluetoothLeScanner
            settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
            aapsLogger.debug("filtering ble")
            filters = buildFilters()
        }

        // disable currently selected RL, so that we can discover it
        medLinkUtil!!.sendBroadcastMessage(MedLinkConst.Intents.MedLinkDisconnect, this)
    }

    private fun buildFilters(): List<ScanFilter> {
        val filters: MutableList<ScanFilter> = ArrayList()
        MedLinkConst.DEVICE_NAME.stream().forEach { s: String? -> filters.add(ScanFilter.Builder().setDeviceName(s).build()) }
        return filters
    }

    private val mScanCallback2: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
            Log.d(TAG, scanRecord.toString())
            aapsLogger.info(LTag.APS, "scan results")
            runOnUiThread {
                if (addDevice(scanRecord)) mLeDeviceListAdapter!!.notifyDataSetChanged()
                aapsLogger.info(LTag.APS, scanRecord.device.toString())
                aapsLogger.info(LTag.APS, scanRecord.scanRecord.toString())
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            Log.d(TAG, java.lang.String.join(",", results.stream().map { obj: ScanResult -> obj.toString() }
                .collect(Collectors.toList())))
            aapsLogger.info(LTag.APS, "bach scan results")
            runOnUiThread {
                var added = false
                for (result in results) {
                    if (addDevice(result)) added = true
                }
                aapsLogger.info(LTag.APS, "result size " + java.lang.String.join(",", results.stream().map { obj: ScanResult -> obj.toString() }
                    .collect(Collectors.toList())))
                showToastInUiThread(mContext, "result size" + java.lang.String.join(",", results.stream().map { obj: ScanResult -> obj.toString() }
                    .collect(Collectors.toList())))
                if (added) mLeDeviceListAdapter!!.notifyDataSetChanged()
            }
        }

        private fun addDevice(result: ScanResult): Boolean {
            val device = result.device
            val deviceName = device.name.trim { it <= ' ' }
            if (MedLinkConst.DEVICE_NAME.contains(deviceName)) {
                Log.i(TAG, "Found Medlink with address: " + device.address)
                rxBus?.let { showToastInUiThread(mContext, it, deviceName, Toast.LENGTH_SHORT) }
                mLeDeviceListAdapter!!.addDevice(result)
                return true
            } else {
                Log.v(TAG, "Device " + device.address + " has incorrect uuid (Not RileyLink).")
            }
            return false
        }

        private fun getDeviceDebug(device: BluetoothDevice): String {
            return "BluetoothDevice [name=" + device.name + ", address=" + device.address +  //
                ", type=" + device.type // + ", alias=" + device.getAlias();
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("Scan Failed", "Error Code: $errorCode")
            rxBus?.let {
                showToastInUiThread(
                    mContext, it, resourceHelper!!.gs(R.string.riley_link_ble_config_scan_error, errorCode),
                    Toast.LENGTH_LONG
                )
            }
        }
    }

    private fun scanLeDevice(enable: Boolean) {
        if (mLEScanner == null) return
        if (enable) {
            mLeDeviceListAdapter!!.clear()
            mLeDeviceListAdapter!!.notifyDataSetChanged()

            // Stops scanning after a pre-defined scan period.
            mHandler!!.postDelayed({
                                       if (mScanning) {
                                           mScanning = false
                                           mLEScanner!!.stopScan(mScanCallback2)
                                           aapsLogger.debug("scanLeDevice: Scanning Stop")
                                           rxBus?.let { showToastInUiThread(mContext, it, resourceHelper!!.gs(R.string.riley_link_ble_config_scan_finished, 40), Toast.LENGTH_SHORT) }
                                           menuItem!!.title = actionTitleStart
                                       }
                                   }, SCAN_PERIOD)
            mScanning = true
            mLEScanner!!.startScan(filters, settings, mScanCallback2)
            aapsLogger.debug("scanLeDevice: Scanning Start")
            rxBus?.let { showToastInUiThread(this, it, resourceHelper!!.gs(R.string.riley_link_ble_config_scan_scanning), Toast.LENGTH_SHORT) }
            menuItem!!.title = actionTitleStop
        } else {
            if (mScanning) {
                mScanning = false
                mLEScanner!!.stopScan(mScanCallback2)
                aapsLogger.debug("scanLeDevice: Scanning Stop 2")
                Toast.makeText(this, R.string.riley_link_ble_config_scan_finished, Toast.LENGTH_SHORT).show()
                menuItem!!.title = actionTitleStart
            }
        }
    }

    private inner class LeDeviceListAdapter : BaseAdapter() {

        private val mLeDevices: ArrayList<BluetoothDevice>
        private val medLinkDevices: MutableMap<BluetoothDevice, Int>
        private val mInflator: LayoutInflater
        var currentlySelectedAddress: String
        fun addDevice(result: ScanResult) {
            if (!mLeDevices.contains(result.device)) {
                mLeDevices.add(result.device)
            }
            medLinkDevices[result.device] = result.rssi
            notifyDataSetChanged()
        }

        fun clear() {
            mLeDevices.clear()
            medLinkDevices.clear()
            notifyDataSetChanged()
        }

        override fun getCount(): Int {
            return mLeDevices.size
        }

        override fun getItem(i: Int): Any {
            return mLeDevices[i]
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(i: Int, view: View, viewGroup: ViewGroup): View {
            // General ListView optimization code.
            val viewHolder: ViewHolder = view.tag as ViewHolder
            val device = mLeDevices[i]
            var deviceName = device.name
            if (StringUtils.isBlank(deviceName)) {
                deviceName = "MedLink"
            }
            deviceName += " [" + medLinkDevices[device]!!.toInt() + "]"
            if (currentlySelectedAddress == device.address) {
                // viewHolder.deviceName.setTextColor(getColor(R.color.secondary_text_light));
                // viewHolder.deviceAddress.setTextColor(getColor(R.color.secondary_text_light));
                deviceName += " (" + resources.getString(R.string.riley_link_ble_config_scan_selected) + ")"
            }
            viewHolder.deviceName!!.text = deviceName
            viewHolder.deviceAddress!!.text = device.address
            return view
        }

        init {
            mLeDevices = ArrayList()
            medLinkDevices = HashMap()
            mInflator = this@MedLinkBLEScanActivity.layoutInflater
            currentlySelectedAddress = sp!!.getString(MedLinkConst.Prefs.MedLinkAddress, "")
        }
    }

    internal class ViewHolder {

        var deviceName: TextView? = null
        var deviceAddress: TextView? = null
    }

    companion object {

        private const val PERMISSION_REQUEST_COARSE_LOCATION = 30241 // arbitrary.
        private const val REQUEST_ENABLE_BT = 30242 // arbitrary
        private const val TAG = "MedLinkBLEScanActivity"

        // Stops scanning after 30 seconds.
        private const val SCAN_PERIOD: Long = 30000
    }
}