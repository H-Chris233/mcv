package app.multicardvault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.multicardvault.core.toStableHex
import app.multicardvault.data.DeviceSecretEntity
import app.multicardvault.data.VaultDao
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreDeviceSecretRepository(
    private val dao: VaultDao,
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
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(keyAlias))
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
    }

    override suspend fun getDeviceSecret(vaultId: ByteArray): ByteArray? {
        val ref = dao.getDeviceSecretRef(vaultId.toStableHex()) ?: return null
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(ref.keyAlias),
            GCMParameterSpec(GcmTagBits, ref.nonce),
        )
        return cipher.doFinal(ref.ciphertext)
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
        val spec =
            KeyGenParameterSpec
                .Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(AndroidKeyStore).apply {
            load(null)
        }

    private fun keyAlias(vaultIdHex: String): String = "mcv_device_secret_$vaultIdHex"

    companion object {
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val Transformation = "AES/GCM/NoPadding"
        private const val DeviceSecretLength = 32
        private const val GcmTagBits = 128
    }
}
