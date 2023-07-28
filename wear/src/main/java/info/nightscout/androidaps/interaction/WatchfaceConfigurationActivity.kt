package info.nightscout.androidaps.interaction

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import info.nightscout.androidaps.R
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import preference.WearPreferenceActivity
import javax.inject.Inject

class WatchfaceConfigurationActivity : WearPreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject lateinit var aapsLogger: AAPSLogger
    private val PHYISCAL_ACTIVITY = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
        val view = window.decorView as ViewGroup
        removeBackgroundRecursively(view)
        view.background = ContextCompat.getDrawable(this, R.drawable.settings_background)
        view.requestFocus()
    }

    private fun removeBackgroundRecursively(parent: View) {
        if (parent is ViewGroup)
            for (i in 0 until parent.childCount)
                removeBackgroundRecursively(parent.getChildAt(i))
        parent.background = null
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {

        if (key == getString(R.string.key_heart_rate_sampling)) {
            if (sp.getBoolean(key, false)) {
                requestBodySensorPermission()
            }
        }
        if (key == getString(R.string.key_steps_sampling)) {
            if (sp.getBoolean(key, false)) {
                // Check if the permission is already granted, if not, request it
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_DENIED) {
                    // The id "PHYISCAL_ACTIVITY" is an integer that identifies the request. It should be defined somewhere in your code.
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), PHYISCAL_ACTIVITY)
                }
            }
        }

    }

    private fun requestBodySensorPermission() {
        val permission = Manifest.permission.BODY_SENSORS
        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), BODY_SENSOR_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        if (requestCode == BODY_SENSOR_PERMISSION_REQUEST_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                aapsLogger.info(LTag.WEAR, "Sensor permission for heart rate granted")
            } else {
                aapsLogger.warn(LTag.WEAR, "Sensor permission for heart rate denied")
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    companion object {
        private const val BODY_SENSOR_PERMISSION_REQUEST_CODE = 1
    }
}