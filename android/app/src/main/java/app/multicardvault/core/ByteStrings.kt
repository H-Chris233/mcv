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

fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "hex string must have an even length" }
    return ByteArray(length / 2) { index ->
        val high = hexValue(this[index * 2])
        val low = hexValue(this[index * 2 + 1])
        ((high shl 4) or low).toByte()
    }
}

private fun hexValue(char: Char): Int = when (char) {
    in '0'..'9' -> char - '0'
    in 'a'..'f' -> char - 'a' + 10
    in 'A'..'F' -> char - 'A' + 10
    else -> error("invalid hex character")
}
