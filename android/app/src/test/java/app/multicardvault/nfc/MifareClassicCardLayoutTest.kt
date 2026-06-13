package app.multicardvault.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MifareClassicCardLayoutTest {
    @Test
    fun layoutUsesClassic1kDataBlocksOnly() {
        assertEquals(47, MifareClassicCardLayout.dataBlocks.size)
        assertEquals(752, MifareClassicCardLayout.storageSize)
        assertEquals(745, MifareClassicCardLayout.maxPayloadSize)
        assertTrue(0 !in MifareClassicCardLayout.dataBlocks)
        assertTrue(MifareClassicCardLayout.dataBlocks.none { (it + 1) % 4 == 0 })
    }

    @Test
    fun payloadRoundtripsThroughCardStorageFrame() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val storage = MifareClassicCardLayout.encode(payload)
        val decoded = MifareClassicCardLayout.decode(storage)

        assertTrue(decoded is MifareClassicCardLayout.DecodeResult.Success)
        assertEquals(payload.toList(), (decoded as MifareClassicCardLayout.DecodeResult.Success).payload.toList())
    }

    @Test
    fun blankStorageDecodesAsEmptyTag() {
        val decoded = MifareClassicCardLayout.decode(ByteArray(MifareClassicCardLayout.storageSize))

        assertEquals(MifareClassicCardLayout.DecodeResult.Empty, decoded)
    }
}
