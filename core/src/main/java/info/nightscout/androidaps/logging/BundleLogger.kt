package info.nightscout.androidaps.logging

import android.os.Bundle

object BundleLogger {

    fun log(bundle: Bundle?): String {
        if (bundle == null) {
            return "null"
        }
        var string = "Bundle{"
        for (key in bundle.keySet()) {
            string += " " + key + " => " + bundle[key] + ";"
        }
        string += " }Bundle"
        return string
    }
}