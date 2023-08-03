package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.MessageBlock
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.OmnipodMessage
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command.AssignAddressCommand
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.VersionResponse
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PacketType
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception.IllegalMessageAddressException
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception.IllegalPacketTypeException
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception.IllegalVersionResponseTypeException
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import info.nightscout.rx.logging.AAPSLogger
import org.joda.time.DateTimeZone
import java.security.SecureRandom
import java.util.Random

class AssignAddressAction(
    private val podStateManager: ErosPodStateManager,
    private val aapsLogger: AAPSLogger
) : OmnipodAction<Any> {

    override fun execute(communicationService: OmnipodRileyLinkCommunicationManager) {
        if (!podStateManager.hasPodState()) {
            podStateManager.initState(generateRandomAddress())
        }
        if (podStateManager.activationProgress.needsPairing()) {
            val assignAddress = AssignAddressCommand(podStateManager.address)
            val assignAddressMessage = OmnipodMessage(OmnipodConstants.DEFAULT_ADDRESS, listOf<MessageBlock>(assignAddress), podStateManager.messageNumber)
            try {
                val assignAddressResponse = communicationService.exchangeMessages(
                    VersionResponse::class.java, podStateManager, assignAddressMessage,
                    OmnipodConstants.DEFAULT_ADDRESS, podStateManager.address
                )
                if (!assignAddressResponse.isAssignAddressVersionResponse) {
                    throw IllegalVersionResponseTypeException("assignAddress", "setupPod")
                }
                if (assignAddressResponse.address != podStateManager.address) {
                    throw IllegalMessageAddressException(podStateManager.address, assignAddressResponse.address)
                }
                podStateManager.setInitializationParameters(
                    assignAddressResponse.lot, assignAddressResponse.tid,  //
                    assignAddressResponse.piVersion, assignAddressResponse.pmVersion, DateTimeZone.getDefault(), assignAddressResponse.podProgressStatus
                )
            } catch (ex: IllegalPacketTypeException) {
                if (ex.actual == PacketType.ACK && podStateManager.isPodInitialized) {
                    // When we already assigned the address before, it's possible to only get an ACK here
                    aapsLogger.debug("Received ACK instead of response in AssignAddressAction. Ignoring because we already assigned the address successfully")
                } else {
                    throw ex
                }
            }
        }
    }

    companion object {

        private val random: Random = SecureRandom()
        private fun generateRandomAddress(): Int =
            // Create random address with 20 bits to match PDM, could easily use 24 bits instead
            0x1f000000 or (random.nextInt() and 0x000fffff)
    }
}