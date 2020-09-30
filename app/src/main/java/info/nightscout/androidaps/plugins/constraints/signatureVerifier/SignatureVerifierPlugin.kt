package info.nightscout.androidaps.plugins.constraints.signatureVerifier

import android.content.Context
import android.content.pm.PackageManager
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ConstraintsInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.spongycastle.util.encoders.Hex
import java.io.*
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AndroidAPS is meant to be build by the user.
 * In case someone decides to leak a ready-to-use APK nonetheless, we can still disable it.
 * Self-compiled APKs with privately held certificates cannot and will not be disabled.
 */
@Singleton
class SignatureVerifierPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val context: Context
) : PluginBase(PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .neverVisible(true)
    .alwaysEnabled(true)
    .showInList(false)
    .pluginName(R.string.signature_verifier),
    aapsLogger, resourceHelper, injector
), ConstraintsInterface {

    private val REVOKED_CERTS_URL = "https://raw.githubusercontent.com/nightscout/AndroidAPS/master/app/src/main/assets/revoked_certs.txt"
    private val UPDATE_INTERVAL = TimeUnit.DAYS.toMillis(1)

    private val lock: Any = arrayOfNulls<Any>(0)
    private var revokedCertsFile: File? = null
    private var revokedCerts: List<ByteArray>? = null
    override fun onStart() {
        super.onStart()
        revokedCertsFile = File(context.filesDir, "revoked_certs.txt")
        Thread(Runnable {
            loadLocalRevokedCerts()
            if (shouldDownloadCerts()) {
                try {
                    downloadAndSaveRevokedCerts()
                } catch (e: IOException) {
                    aapsLogger.error("Could not download revoked certs", e)
                }
            }
            if (hasIllegalSignature()) showNotification()
        }).start()
    }

    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (hasIllegalSignature()) {
            showNotification()
            value.set(aapsLogger, false)
        }
        if (shouldDownloadCerts()) {
            Thread(Runnable {
                try {
                    downloadAndSaveRevokedCerts()
                } catch (e: IOException) {
                    aapsLogger.error("Could not download revoked certs", e)
                }
            }).start()
        }
        return value
    }

    private fun showNotification() {
        val notification = Notification(Notification.INVALID_VERSION, resourceHelper.gs(R.string.running_invalid_version), Notification.URGENT)
        rxBus.send(EventNewNotification(notification))
    }

    private fun hasIllegalSignature(): Boolean {
        try {
            synchronized(lock) {
                if (revokedCerts == null) return false
                // TODO Change after raising min API to 28
                @Suppress("DEPRECATION", "PackageManagerGetSignatures")
                val signatures = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
                if (signatures != null) {
                    for (signature in signatures) {
                        val digest = MessageDigest.getInstance("SHA256")
                        val fingerprint = digest.digest(signature.toByteArray())
                        for (cert in revokedCerts!!) {
                            if (Arrays.equals(cert, fingerprint)) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        } catch (e: NoSuchAlgorithmException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        }
        return false
    }

    fun shortHashes(): List<String> {
        val hashes: MutableList<String> = ArrayList()
        try {
            // TODO Change after raising min API to 28
            @Suppress("DEPRECATION", "PackageManagerGetSignatures")
            val signatures = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            if (signatures != null) {
                for (signature in signatures) {
                    val digest = MessageDigest.getInstance("SHA256")
                    val fingerprint = digest.digest(signature.toByteArray())
                    val hash = Hex.toHexString(fingerprint)
                    aapsLogger.debug("Found signature: $hash")
                    aapsLogger.debug("Found signature (short): " + singleCharMap(fingerprint))
                    hashes.add(singleCharMap(fingerprint))
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        } catch (e: NoSuchAlgorithmException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        }
        return hashes
    }

    var map = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"§$%&/()=?,.-;:_<>|°^`´\\@€*'#+~{}[]¿¡áéíóúàèìòùöäü`ÁÉÍÓÚÀÈÌÒÙÖÄÜßÆÇÊËÎÏÔŒÛŸæçêëîïôœûÿĆČĐŠŽćđšžñΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡ\u03A2ΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρςστυφχψωϨϩϪϫϬϭϮϯϰϱϲϳϴϵ϶ϷϸϹϺϻϼϽϾϿЀЁЂЃЄЅІЇЈЉЊЋЌЍЎЏАБВГДЕЖЗ"
    private fun singleCharMap(array: ByteArray): String {
        val sb = StringBuilder()
        for (b in array) {
            sb.append(map[b.toInt() and 0xFF])
        }
        return sb.toString()
    }

    fun singleCharUnMap(shortHash: String): String {
        val array = ByteArray(shortHash.length)
        val sb = StringBuilder()
        for (i in array.indices) {
            if (i != 0) sb.append(":")
            sb.append(String.format("%02X", 0xFF and map[map.indexOf(shortHash[i])].toInt()))
        }
        return sb.toString()
    }

    private fun shouldDownloadCerts(): Boolean {
        return System.currentTimeMillis() - sp.getLong(R.string.key_last_revoked_certs_check, 0L) >= UPDATE_INTERVAL
    }

    @Throws(IOException::class) private fun downloadAndSaveRevokedCerts() {
        val download = downloadRevokedCerts()
        saveRevokedCerts(download)
        sp.putLong(R.string.key_last_revoked_certs_check, System.currentTimeMillis())
        synchronized(lock) { revokedCerts = parseRevokedCertsFile(download) }
    }

    private fun loadLocalRevokedCerts() {
        try {
            var revokedCerts = readCachedDownloadedRevokedCerts()
            if (revokedCerts == null) revokedCerts = readRevokedCertsInAssets()
            synchronized(lock) { this.revokedCerts = parseRevokedCertsFile(revokedCerts) }
        } catch (e: IOException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        }
    }

    @Throws(IOException::class)
    private fun saveRevokedCerts(revokedCerts: String) {
        val outputStream: OutputStream = FileOutputStream(revokedCertsFile)
        outputStream.write(revokedCerts.toByteArray(StandardCharsets.UTF_8))
        outputStream.close()
    }

    @Throws(IOException::class) private fun downloadRevokedCerts(): String {
        val connection = URL(REVOKED_CERTS_URL).openConnection()
        return readInputStream(connection.getInputStream())
    }

    @Throws(IOException::class)
    private fun readInputStream(inputStream: InputStream): String {
        return try {
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                baos.write(buffer, 0, read)
            }
            baos.flush()
            String(baos.toByteArray(), StandardCharsets.UTF_8)
        } finally {
            inputStream.close()
        }
    }

    @Throws(IOException::class) private fun readRevokedCertsInAssets(): String {
        val inputStream = context.assets.open("revoked_certs.txt")
        return readInputStream(inputStream)
    }

    @Throws(IOException::class)
    private fun readCachedDownloadedRevokedCerts(): String? {
        return if (!revokedCertsFile!!.exists()) null else readInputStream(FileInputStream(revokedCertsFile))
    }

    private fun parseRevokedCertsFile(file: String?): List<ByteArray> {
        val revokedCerts: MutableList<ByteArray> = ArrayList()
        for (line in file!!.split("\n").toTypedArray()) {
            if (line.startsWith("#")) continue
            revokedCerts.add(Hex.decode(line.replace(" ", "").replace(":", "")))
        }
        return revokedCerts
    }
}