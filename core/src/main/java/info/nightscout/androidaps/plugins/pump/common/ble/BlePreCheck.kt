package info.nightscout.androidaps.plugins.pump.common.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlePreCheck @Inject constructor(
    private val context: Context,
    private val rh: ResourceHelper
) {

    companion object {

        private const val PERMISSION_REQUEST_COARSE_LOCATION = 30241 // arbitrary.
        private const val PERMISSION_REQUEST_BLUETOOTH = 30242 // arbitrary.
    }

    fun prerequisitesCheck(activity: AppCompatActivity): Boolean {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            OKDialog.show(activity, rh.gs(R.string.message), rh.gs(R.string.ble_not_supported))
            return false
        } else {
            // Use this check to determine whether BLE is supported on the device. Then
            // you can selectively disable BLE-related features.
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // your code that requires permission
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_COARSE_LOCATION)
            }
            // change after SDK = 31+
            if (Build.VERSION.SDK_INT >= /*Build.VERSION_CODES.S*/31) {
                if (ContextCompat.checkSelfPermission(context, "android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, "android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED
                )
                //ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_BLUETOOTH)
                    ActivityCompat.requestPermissions(activity, arrayOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"), PERMISSION_REQUEST_BLUETOOTH)
                return false
            }

            val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
            // Ensures Bluetooth is available on the device and it is enabled. If not,
            // displays a dialog requesting user permission to enable Bluetooth.
            if (bluetoothAdapter?.isEnabled != true) {
                bluetoothAdapter?.enable()
                SystemClock.sleep(3000)
            }
            if (bluetoothAdapter?.isEnabled != true) {
                OKDialog.show(activity, rh.gs(R.string.message), rh.gs(R.string.ble_not_enabled))
                return false
            } else {
                // Will request that GPS be enabled for devices running Marshmallow or newer.
                if (!isLocationEnabled(activity)) {
                    requestLocation(activity)
                    return false
                }
            }
        }
        return true
    }

    /**
     * Determine if GPS is currently enabled.
     *
     *
     * On Android 6 (Marshmallow), location needs to be enabled for Bluetooth discovery to work.
     *
     * @param context The current app context.
     * @return true if location is enabled, false otherwise.
     */
    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Prompt the user to enable GPS location if it isn't already on.
     *
     * @param activity The currently visible activity.
     */
    private fun requestLocation(activity: AppCompatActivity) {
        if (isLocationEnabled(activity)) {
            return
        }

        // Shamelessly borrowed from http://stackoverflow.com/a/10311877/868533
        OKDialog.showConfirmation(activity, rh.gs(R.string.location_not_found_title), rh.gs(R.string.location_not_found_message), Runnable {
            activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        })
    }
}