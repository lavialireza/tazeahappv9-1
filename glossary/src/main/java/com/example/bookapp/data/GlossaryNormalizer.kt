package com.example.bookapp.data

/**
 * Canonical Persian/Arabic text normalization shared by Admin and Viewer.
 * It is intentionally independent from Android UI and the application module.
 */
object GlossaryNormalizer {
    fun normalize(value: String): String {
        val out = StringBuilder(value.length)
        var pendingSpace = false
        value.forEach { raw ->
            val mapped = mapCharacter(raw) ?: return@forEach
            if (mapped.isWhitespace()) {
                pendingSpace = out.isNotEmpty()
            } else {
                if (pendingSpace) out.append(' ')
                pendingSpace = false
                out.append(mapped)
            }
        }
        return out.toString().trim()
    }

    /** Character mapping used by offset-preserving text matching. Null means ignore. */
    fun mapCharacter(raw: Char): Char? {
        return when (raw) {
            'ي', 'ى' -> 'ی'
            'ك' -> 'ک'
            'ۀ', 'ة' -> 'ه'
            '\u0640' -> null // Arabic tatweel
            '\u200c', '\u200d', '\u00a0' -> ' '
            in '\u0610'..'\u061a', in '\u064b'..'\u065f', '\u0670', in '\u06d6'..'\u06ed' -> null // Arabic diacritics
            else -> when (Character.getType(raw)) {
                Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
                Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
                Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                Character.OTHER_PUNCTUATION.toInt(), Character.MATH_SYMBOL.toInt(),
                Character.CURRENCY_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(),
                Character.OTHER_SYMBOL.toInt() -> ' '
                else -> if (raw.isWhitespace()) ' ' else raw.lowercaseChar()
            }
        }
    }
}
