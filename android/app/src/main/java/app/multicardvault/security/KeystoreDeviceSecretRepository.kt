package app.multicardvault.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.multicardvault.core.toStableHex
import app.multicardvault.data.DeviceSecretEntity
import app.multicardvault.data.VaultDao
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreDeviceSecretRepository(
    private val dao: VaultDao,
    private val activity: FragmentActivity,
    private val secureRandom: SecureRandom = SecureRandom(),
) : DeviceSecretRepository {
    override fun generateDeviceSecret(): ByteArray {
        val secret = ByteArray(DeviceSecretLength)
        secureRandom.nextBytes(secret)
        return secret
    }

    override suspend fun saveDeviceSecret(
        vaultId: ByteArray,
        deviceSecret: ByteArray,
    ) {
        require(deviceSecret.size == DeviceSecretLength) { "device secret must be 32 bytes" }

        val vaultIdHex = vaultId.toStableHex()
        val keyAlias = keyAlias(vaultIdHex)
        val key = getOrCreateKey(keyAlias)
        val cipher = Cipher.getInstance(Transformation)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(deviceSecret)
            dao.upsertDeviceSecretRef(
                DeviceSecretEntity(
                    vaultIdHex = vaultIdHex,
                    keyAlias = keyAlias,
                    nonce = cipher.iv,
                    ciphertext = ciphertext,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            return
        } catch (_: InvalidKeyException) {
            // Key requires biometric auth — fall through to BiometricPrompt.
        }

        // Biometric-authenticated encrypt — DAO save happens here after biometric completes.
        val (ciphertext, nonce) = biometricEncrypt(key, deviceSecret)
        dao.upsertDeviceSecretRef(
            DeviceSecretEntity(
                vaultIdHex = vaultIdHex,
                keyAlias = keyAlias,
                nonce = nonce,
                ciphertext = ciphertext,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun getDeviceSecret(vaultId: ByteArray): ByteArray? {
        val ref = dao.getDeviceSecretRef(vaultId.toStableHex()) ?: return null
        val key = getOrCreateKey(ref.keyAlias)

        // Try direct decrypt first (works for old keys without biometric auth).
        val cipher = Cipher.getInstance(Transformation)
        try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GcmTagBits, ref.nonce),
            )
            return cipher.doFinal(ref.ciphertext)
        } catch (_: InvalidKeyException) {
            // Key requires biometric auth — fall through to BiometricPrompt.
        }

        return biometricDecrypt(key, ref)
    }

    override suspend fun deleteDeviceSecret(vaultId: ByteArray) {
        val vaultIdHex = vaultId.toStableHex()
        dao.deleteDeviceSecretRef(vaultIdHex)
        keyStore().deleteEntry(keyAlias(vaultIdHex))
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = keyStore()
        keyStore.getKey(alias, null)?.let { return it as SecretKey }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                AndroidKeyStore,
            )
        val specBuilder =
            KeyGenParameterSpec
                .Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            specBuilder.setUserAuthenticationParameters(
                AuthDurationSeconds,
                KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        } else {
            @Suppress("DEPRECATION")
            specBuilder
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(AuthDurationSeconds)
        }

        val spec = specBuilder.build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(AndroidKeyStore).apply {
            load(null)
        }

    private fun keyAlias(vaultIdHex: String): String = "mcv_device_secret_$vaultIdHex"

    private suspend fun biometricDecrypt(
        key: SecretKey,
        ref: DeviceSecretEntity,
    ): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GcmTagBits, ref.nonce),
            )

            val executor = ContextCompat.getMainExecutor(activity)
            val prompt =
                BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val crypto = result.cryptoObject
                            val authCipher = crypto?.cipher
                            if (authCipher == null) {
                                val err =
                                    IllegalStateException(
                                        "biometric auth succeeded but cipher is missing",
                                    )
                                continuation.resumeWith(Result.failure(err))
                                return
                            }
                            val decrypted = runCatching { authCipher.doFinal(ref.ciphertext) }
                            continuation.resumeWith(decrypted)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            val err = BiometricAuthException(errorCode, errString.toString())
                            continuation.resumeWith(Result.failure(err))
                        }

                        override fun onAuthenticationFailed() {
                            // Don't resume — user can retry fingerprint scan.
                        }
                    },
                )

            val promptInfo =
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle("验证身份")
                    .setSubtitle("使用指纹或设备密码访问保险库密钥")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    ).build()

            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))

            continuation.invokeOnCancellation {
                prompt.cancelAuthentication()
            }
        }

    private suspend fun biometricEncrypt(
        key: SecretKey,
        deviceSecret: ByteArray,
    ): BiometricEncryptResult =
        suspendCancellableCoroutine { continuation ->
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val executor = ContextCompat.getMainExecutor(activity)
            val prompt =
                BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val crypto = result.cryptoObject
                            val authCipher = crypto?.cipher
                            if (authCipher == null) {
                                val err =
                                    IllegalStateException(
                                        "biometric auth succeeded but cipher is missing",
                                    )
                                continuation.resumeWith(Result.failure(err))
                                return
                            }
                            val resultData =
                                runCatching {
                                    val ciphertext = authCipher.doFinal(deviceSecret)
                                    BiometricEncryptResult(
                                        ciphertext = ciphertext,
                                        nonce = authCipher.iv,
                                    )
                                }
                            continuation.resumeWith(resultData)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            val err = BiometricAuthException(errorCode, errString.toString())
                            continuation.resumeWith(Result.failure(err))
                        }

                        override fun onAuthenticationFailed() {
                            // Don't resume — user can retry.
                        }
                    },
                )

            val promptInfo =
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle("验证身份")
                    .setSubtitle("使用指纹或设备密码创建保险库")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    ).build()

            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))

            continuation.invokeOnCancellation {
                prompt.cancelAuthentication()
            }
        }

    class BiometricAuthException(
        val errorCode: Int,
        message: String,
    ) : SecurityException(message)

    private data class BiometricEncryptResult(
        val ciphertext: ByteArray,
        val nonce: ByteArray,
    )

    companion object {
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val Transformation = "AES/GCM/NoPadding"
        private const val DeviceSecretLength = 32
        private const val GcmTagBits = 128
        private const val AuthDurationSeconds = 30
    }
}
