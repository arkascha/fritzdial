package info.christianreiner.fritzdial.data

import android.content.Context
import info.christianreiner.fritzdial.tr064.BoxDiscovery
import info.christianreiner.fritzdial.tr064.BoxIdentity

/** What a look around the current network found. */
class Recognition(
    /** The boxes that answered, with their device ids. */
    val found: List<BoxIdentity>,
    /** The network's gateway, normally its FritzBox; null without network. */
    val gateway: String?,
    /** The configured box of this network, if any (see [BoxRecognition.recognise]). */
    val match: BoxProfile?,
)

/** Recognises which of the configured FritzBoxes is reachable from the current network. */
object BoxRecognition {

    /** The name every FritzBox answers to in its own network. */
    private const val LOCAL_BOX_NAME = "fritz.box"

    /**
     * Asks the gateway of the current network, "fritz.box" and the hosts of [profiles] for their
     * device id. The box of this very network wins; one that only answers at its configured host
     * (through a VPN, or at its MyFRITZ address) is taken only when this network has none of them.
     * Connections without device id are not matched: any box answering at their host – even one in a
     * foreign network – would be taken for them.
     */
    suspend fun recognise(context: Context, profiles: List<BoxProfile>): Recognition {
        val gateway = BoxDiscovery.currentGateway(context)
        val local = listOfNotNull(gateway, LOCAL_BOX_NAME)
        val found = BoxDiscovery.identifyAll(local + profiles.map { it.config.host })
        fun matchIn(boxes: List<BoxIdentity>) =
            boxes.firstNotNullOfOrNull { identity -> profiles.firstOrNull { it.deviceId == identity.udn } }
        return Recognition(found, gateway, matchIn(found.filter { it.host in local }) ?: matchIn(found))
    }
}
