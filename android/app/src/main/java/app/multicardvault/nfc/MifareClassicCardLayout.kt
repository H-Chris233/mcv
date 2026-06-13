package app.multicardvault.nfc

object MifareClassicCardLayout {
    const val BlockSize = 16
    private const val SectorCount = 16
    private const val BlocksPerSector = 4
    private const val TrailerOffset = 3
    private const val HeaderSize = 7
    private val Magic = byteArrayOf('M'.code.toByte(), 'C'.code.toByte(), 'V'.code.toByte(), 'C'.code.toByte())

    val dataBlocks: List<Int> =
        buildList {
            for (sector in 0 until SectorCount) {
                val firstBlock = sector * BlocksPerSector
                for (offset in 0 until BlocksPerSector) {
                    val block = firstBlock + offset
                    val isManufacturerBlock = sector == 0 && offset == 0
                    val isTrailerBlock = offset == TrailerOffset
                    if (!isManufacturerBlock && !isTrailerBlock) {
                        add(block)
                    }
                }
            }
        }

    val storageSize: Int = dataBlocks.size * BlockSize
    val maxPayloadSize: Int = storageSize - HeaderSize

    fun encode(payload: ByteArray): ByteArray {
        require(payload.size <= maxPayloadSize) { "payload too large" }
        val storage = ByteArray(storageSize)
        Magic.copyInto(storage, destinationOffset = 0)
        storage[4] = FormatVersion
        storage[5] = ((payload.size ushr 8) and 0xff).toByte()
        storage[6] = (payload.size and 0xff).toByte()
        payload.copyInto(storage, destinationOffset = HeaderSize)
        return storage
    }

    fun decode(storage: ByteArray): DecodeResult {
        if (storage.all { it == 0.toByte() || it == 0xff.toByte() }) {
            return DecodeResult.Empty
        }
        if (storage.size < HeaderSize) {
            return DecodeResult.Invalid("卡片数据长度不足。")
        }
        if (!storage.copyOfRange(0, Magic.size).contentEquals(Magic)) {
            return DecodeResult.Invalid("卡片不是本应用写入的 CUID 数据。")
        }
        if (storage[4] != FormatVersion) {
            return DecodeResult.Invalid("卡片数据版本不受支持。")
        }

        val payloadSize = ((storage[5].toInt() and 0xff) shl 8) or (storage[6].toInt() and 0xff)
        if (payloadSize > maxPayloadSize || HeaderSize + payloadSize > storage.size) {
            return DecodeResult.Invalid("卡片 payload 长度无效。")
        }
        return DecodeResult.Success(storage.copyOfRange(HeaderSize, HeaderSize + payloadSize))
    }

    sealed interface DecodeResult {
        data class Success(
            val payload: ByteArray,
        ) : DecodeResult

        data object Empty : DecodeResult

        data class Invalid(
            val reason: String,
        ) : DecodeResult
    }

    private const val FormatVersion: Byte = 1
}
