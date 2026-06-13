package app.multicardvault.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import java.io.IOException

class MifareClassicNfcRepository : NfcRepository {
    override fun readPayload(tag: Tag): NfcCardResult {
        val classic =
            MifareClassic.get(tag)
                ?: return NfcCardResult.UnsupportedTag("卡片不支持 MIFARE Classic。")

        return try {
            classic.connect()
            if (!isSupportedCard(classic)) {
                NfcCardResult.UnsupportedTag("当前仅支持 MIFARE Classic 1K 兼容 CUID 卡。")
            } else {
                readStorage(classic)
            }
        } catch (_error: IOException) {
            NfcCardResult.IoError("读取 CUID 卡失败。")
        } catch (_error: SecurityException) {
            NfcCardResult.IoError("没有权限访问 CUID 卡。")
        } finally {
            closeQuietly(classic)
        }
    }

    override fun writePayload(
        tag: Tag,
        payload: ByteArray,
    ): NfcCardResult {
        if (payload.size > MifareClassicCardLayout.maxPayloadSize) {
            return NfcCardResult.CapacityTooSmall(
                requiredBytes = payload.size,
                maxBytes = MifareClassicCardLayout.maxPayloadSize,
            )
        }

        val classic =
            MifareClassic.get(tag)
                ?: return NfcCardResult.UnsupportedTag("卡片不支持 MIFARE Classic。")

        return try {
            classic.connect()
            if (!isSupportedCard(classic)) {
                NfcCardResult.UnsupportedTag("当前仅支持 MIFARE Classic 1K 兼容 CUID 卡。")
            } else {
                writeStorage(classic, payload)
            }
        } catch (_error: IOException) {
            NfcCardResult.IoError("写入 CUID 卡失败。")
        } catch (_error: SecurityException) {
            NfcCardResult.IoError("没有权限访问 CUID 卡。")
        } finally {
            closeQuietly(classic)
        }
    }

    private fun readStorage(classic: MifareClassic): NfcCardResult {
        val storage = ByteArray(MifareClassicCardLayout.storageSize)
        var offset = 0
        for (sector in 0 until classic.sectorCount) {
            val blocks = MifareClassicCardLayout.dataBlocks.filter { classic.blockToSector(it) == sector }
            if (blocks.isEmpty()) continue
            if (!authenticateDefault(classic, sector)) {
                return NfcCardResult.UnsupportedTag("无法使用默认密钥认证第 ${sector + 1} 个扇区。")
            }
            for (block in blocks) {
                classic.readBlock(block).copyInto(storage, destinationOffset = offset)
                offset += MifareClassicCardLayout.BlockSize
            }
        }

        return when (val decoded = MifareClassicCardLayout.decode(storage)) {
            MifareClassicCardLayout.DecodeResult.Empty -> NfcCardResult.EmptyTag
            is MifareClassicCardLayout.DecodeResult.Invalid -> NfcCardResult.InvalidPayload(decoded.reason)
            is MifareClassicCardLayout.DecodeResult.Success -> NfcCardResult.Success(decoded.payload)
        }
    }

    private fun writeStorage(
        classic: MifareClassic,
        payload: ByteArray,
    ): NfcCardResult {
        val storage = MifareClassicCardLayout.encode(payload)
        var offset = 0
        for (sector in 0 until classic.sectorCount) {
            val blocks = MifareClassicCardLayout.dataBlocks.filter { classic.blockToSector(it) == sector }
            if (blocks.isEmpty()) continue
            if (!authenticateDefault(classic, sector)) {
                return NfcCardResult.UnsupportedTag("无法使用默认密钥认证第 ${sector + 1} 个扇区。")
            }
            for (block in blocks) {
                classic.writeBlock(
                    block,
                    storage.copyOfRange(offset, offset + MifareClassicCardLayout.BlockSize),
                )
                offset += MifareClassicCardLayout.BlockSize
            }
        }
        return NfcCardResult.Success(payload)
    }

    private fun authenticateDefault(
        classic: MifareClassic,
        sector: Int,
    ): Boolean =
        DefaultKeys.any { key ->
            classic.authenticateSectorWithKeyA(sector, key) ||
                classic.authenticateSectorWithKeyB(sector, key)
        }

    private fun isSupportedCard(classic: MifareClassic): Boolean =
        classic.size == MifareClassic.SIZE_1K &&
            classic.sectorCount == ExpectedSectorCount &&
            classic.blockCount == ExpectedBlockCount

    private fun closeQuietly(classic: MifareClassic) {
        runCatching { classic.close() }
    }

    companion object {
        private const val ExpectedSectorCount = 16
        private const val ExpectedBlockCount = 64
        private val DefaultKeys =
            listOf(
                MifareClassic.KEY_DEFAULT,
                MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
                MifareClassic.KEY_NFC_FORUM,
            )
    }
}
