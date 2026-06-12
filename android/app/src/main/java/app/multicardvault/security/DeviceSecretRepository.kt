package app.multicardvault.security

interface DeviceSecretRepository {
    fun generateDeviceSecret(): ByteArray

    suspend fun saveDeviceSecret(
        vaultId: ByteArray,
        deviceSecret: ByteArray,
    )

    suspend fun getDeviceSecret(vaultId: ByteArray): ByteArray?

    suspend fun deleteDeviceSecret(vaultId: ByteArray)
}
