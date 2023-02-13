package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.CommandPriority
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkCommandType
import info.nightscout.interfaces.profile.Profile
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 05/02/21.
 */
class BasalMedLinkMessage<B>(
    baseCallBack: Function<Supplier<Stream<String>>, MedLinkStandardReturn<B>>,
    profileCallback: Function<Supplier<Stream<String>>, MedLinkStandardReturn<Profile?>>,
    btSleepSize: Long,
    bleCommand: BleCommand
) : MedLinkPumpMessage<B, Profile?>(MedLinkCommandType.ActiveBasalProfile,
                                    baseCallBack,
                                    btSleepSize, bleCommand,
                                    CommandPriority.NORMAL) {
    init {
        this.supplementalCommands = mutableListOf(
            CommandStructure(MedLinkCommandType.BaseProfile, Optional.of(profileCallback), Optional.of(bleCommand), CommandPriority.NORMAL)
        )
    }



}