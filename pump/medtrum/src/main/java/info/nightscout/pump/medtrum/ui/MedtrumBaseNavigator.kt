package info.nightscout.pump.medtrum.ui

interface MedtrumBaseNavigator {
    fun back()

    fun finish(finishAffinity: Boolean = false)
}
