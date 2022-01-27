package info.nightscout.androidaps.plugins.pump.omnipod.dash.util

import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.FailedToConnectException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.NotConnectedException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ScanException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ScanFailFoundTooManyException
import info.nightscout.androidaps.utils.resources.ResourceHelper

class I8n {
    companion object {
        fun textFromException(exception: Throwable, rs: ResourceHelper): String {
            return when (exception) {
                is FailedToConnectException -> rs.gs(R.string.omnipod_dash_failed_to_connect)
                is ScanFailFoundTooManyException -> rs.gs(R.string.omnipod_dash_found_too_many_pods)
                is ScanException -> rs.gs(R.string.omnipod_dash_scan_failed)
                is NotConnectedException -> rs.gs(R.string.omnipod_dash_connection_lost)
                else ->
                    rs.gs(R.string.omnipod_dash_generic_error, exception.toString())
            }
        }
    }
}
