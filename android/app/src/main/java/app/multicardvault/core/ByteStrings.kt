package app.multicardvault.core

private val hexDigits = "0123456789abcdef".toCharArray()

fun ByteArray.toStableHex(): String {
    val output = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        output[index * 2] = hexDigits[value ushr 4]
        output[index * 2 + 1] = hexDigits[value and 0x0f]
    }
    return output.concatToString()
}
