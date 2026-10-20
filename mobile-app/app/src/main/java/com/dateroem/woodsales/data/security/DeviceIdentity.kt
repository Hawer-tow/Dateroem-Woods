package com.dateroem.woodsales.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.dateroem.woodsales.BuildConfig
import com.dateroem.woodsales.data.model.DeviceProof
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Locale

/**
 * "allows only one user per account per mobile phone ... like netflix" - this
 * install's own identity: a signing key created once in the phone's secure
 * hardware (Android Keystore, StrongBox where the phone has it). The private
 * half can never be read or copied off the phone, so a copied password, a
 * cloned app ("dual apps") or a second phone all show up to the server as a
 * different phone that an admin has to approve. Reinstalling the app or a
 * factory reset makes a new key - that phone then needs approving again.
 */
class DeviceIdentity {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Signs the server's one-time challenge and describes this phone. Slow on first use (creates the key). */
    fun proofFor(challenge: String): DeviceProof {
        ensureKey()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyStore.getKey(ALIAS, null) as PrivateKey)
            update(challenge.toByteArray(Charsets.UTF_8))
            sign()
        }
        return DeviceProof(
            publicKey = Base64.encodeToString(keyStore.getCertificate(ALIAS).publicKey.encoded, Base64.NO_WRAP),
            challenge = challenge,
            signature = Base64.encodeToString(signature, Base64.NO_WRAP),
            model = phoneName(),
            osVersion = Build.VERSION.RELEASE,
            appVersion = BuildConfig.VERSION_NAME
        )
    }

    private fun ensureKey() {
        if (keyStore.containsAlias(ALIAS)) return
        // StrongBox (a separate security chip) where the phone has one,
        // otherwise the phone's normal hardware-backed keystore.
        val created = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && runCatching { generate(strongBox = true) }.isSuccess
        if (!created) generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "dateroem_device_key"

        /** "Samsung SM-A155F" - what the admin sees when approving this phone. */
        fun phoneName(): String {
            val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(Locale.ROOT) }
            val model = Build.MODEL.orEmpty()
            return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
        }
    }
}
