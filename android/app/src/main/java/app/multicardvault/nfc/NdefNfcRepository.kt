package app.multicardvault.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException
import java.nio.charset.StandardCharsets

class NdefNfcRepository : NfcRepository {
    override fun readPayload(tag: Tag): NfcCardResult {
        val ndef =
            Ndef.get(tag)
                ?: return NfcCardResult.UnsupportedTag("标签不支持 NDEF。")

        return try {
            ndef.connect()
            val message = ndef.ndefMessage ?: return NfcCardResult.EmptyTag
            readProjectPayload(message)
        } catch (_error: FormatException) {
            NfcCardResult.InvalidPayload("标签中的 NDEF 数据格式无效。")
        } catch (_error: IOException) {
            NfcCardResult.IoError("读取标签失败。")
        } finally {
            closeQuietly(ndef)
        }
    }

    override fun writePayload(
        tag: Tag,
        payload: ByteArray,
    ): NfcCardResult {
        val message =
            NdefMessage(
                arrayOf(
                    NdefRecord.createMime(ProjectMimeType, payload),
                ),
            )
        val messageSize = message.toByteArray().size
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return writeExistingNdef(ndef, message, messageSize, payload)
        }

        val formatable =
            NdefFormatable.get(tag)
                ?: return NfcCardResult.UnsupportedTag("标签不支持 NDEF 写入。")
        return formatBlankTag(formatable, message, payload)
    }

    private fun writeExistingNdef(
        ndef: Ndef,
        message: NdefMessage,
        messageSize: Int,
        payload: ByteArray,
    ): NfcCardResult =
        try {
            ndef.connect()
            if (!ndef.isWritable) {
                NfcCardResult.UnsupportedTag("标签是只读的。")
            } else if (messageSize > ndef.maxSize) {
                NfcCardResult.CapacityTooSmall(
                    requiredBytes = messageSize,
                    maxBytes = ndef.maxSize,
                )
            } else {
                ndef.writeNdefMessage(message)
                NfcCardResult.Success(payload)
            }
        } catch (_error: FormatException) {
            NfcCardResult.InvalidPayload("无法写入 NDEF 数据。")
        } catch (_error: IOException) {
            NfcCardResult.IoError("写入标签失败。")
        } finally {
            closeQuietly(ndef)
        }

    private fun formatBlankTag(
        formatable: NdefFormatable,
        message: NdefMessage,
        payload: ByteArray,
    ): NfcCardResult =
        try {
            formatable.connect()
            formatable.format(message)
            NfcCardResult.Success(payload)
        } catch (_error: FormatException) {
            NfcCardResult.InvalidPayload("无法格式化标签。")
        } catch (_error: IOException) {
            NfcCardResult.IoError("格式化标签失败。")
        } finally {
            closeQuietly(formatable)
        }

    private fun readProjectPayload(message: NdefMessage): NfcCardResult {
        if (message.records.isEmpty()) {
            return NfcCardResult.EmptyTag
        }

        val record =
            message.records.firstOrNull { record ->
                record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    record.type.contentEquals(ProjectMimeTypeBytes)
            } ?: return NfcCardResult.InvalidPayload("标签不是本应用创建的 Card Payload。")

        return if (record.payload.isEmpty()) {
            NfcCardResult.EmptyTag
        } else {
            NfcCardResult.Success(record.payload)
        }
    }

    private fun closeQuietly(tech: AutoCloseable) {
        runCatching { tech.close() }
    }

    companion object {
        const val ProjectMimeType = "application/vnd.app.multicardvault.card"
        private val ProjectMimeTypeBytes = ProjectMimeType.toByteArray(StandardCharsets.US_ASCII)
    }
}
