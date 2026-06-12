package app.multicardvault.nfc

import android.nfc.Tag

interface NfcRepository {
    fun readPayload(tag: Tag): NfcCardResult

    fun writePayload(
        tag: Tag,
        payload: ByteArray,
    ): NfcCardResult
}
