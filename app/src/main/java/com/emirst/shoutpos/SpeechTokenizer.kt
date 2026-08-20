package com.emirst.shoutpos

/**
 * Cleans a raw transcript, splits it into tokens, and groups those tokens into
 * one segment per spoken item.
 *
 * Expected pattern per segment: `[product name] [quantity] [separator]`, e.g.
 * "indomie goreng dua lalu teh botol tiga".
 *
 * Deliberately knows nothing about the catalog. It reports what it heard;
 * deciding which product that is belongs to SpeechModel.
 */
class SpeechTokenizer {

    fun tokenize(rawTranscript: String): List<SpeechSegment> {
        val tokens = clean(rawTranscript)
        if (tokens.isEmpty()) return emptyList()
        return splitBySeparator(tokens).mapNotNull { toSegment(it) }
    }

    /**
     * Lowercases, drops punctuation, and removes filler words.
     *
     * Note what is *not* removed: unit words. "botol" and "kotak" look like
     * units but are load-bearing parts of "teh botol", "teh kotak" and
     * "teh botol kotak". Stripping units here would make those three products
     * indistinguishable.
     */
    private fun clean(rawTranscript: String): List<String> =
        rawTranscript
            .lowercase()
            .replace(NON_WORD, " ")
            .split(WHITESPACE)
            .filter { it.isNotBlank() && it !in FILLER_WORDS }

    private fun splitBySeparator(tokens: List<String>): List<List<String>> {
        val segments = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (token in tokens) {
            if (token in SEPARATORS) {
                if (current.isNotEmpty()) segments.add(current)
                current = mutableListOf()
            } else {
                current.add(token)
            }
        }
        if (current.isNotEmpty()) segments.add(current)
        return segments
    }

    /**
     * Splits a segment into product phrase and quantity.
     *
     * Only a *trailing* token is considered for quantity. The catalog contains
     * "gudang garam surya 16" and "gudang garam surya 12", so a digit in the
     * middle of a phrase is part of the name, not a count. Even a trailing digit
     * is ambiguous for those two, which is why [SpeechSegment.fullPhrase] preserves the
     * alternative reading.
     */
    private fun toSegment(tokens: List<String>): SpeechSegment? {
        if (tokens.isEmpty()) return null
        val fullPhrase = tokens.joinToString(" ")

        val trailingQuantity = quantityOf(tokens.last())
        val hasQuantity = trailingQuantity != null && tokens.size > 1

        val productTokens = if (hasQuantity) tokens.dropLast(1) else tokens
        return SpeechSegment(
            tokens = tokens,
            productPhrase = productTokens.joinToString(" "),
            quantityMilli = trailingQuantity ?: SpeechConstants.QUANTITY_SCALE,
            fullPhrase = fullPhrase
        )
    }

    /** Scaled quantity for a single token, or null when it is not a number. */
    private fun quantityOf(token: String): Int? {
        token.toIntOrNull()?.let { return it * SpeechConstants.QUANTITY_SCALE }
        FRACTION_WORDS[token]?.let { return it }
        NUMBER_WORDS[token]?.let { return it * SpeechConstants.QUANTITY_SCALE }
        if (token in SE_PREFIX_FORMS) return SpeechConstants.QUANTITY_SCALE
        return null
    }

    private companion object {
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        val WHITESPACE = Regex("\\s+")

        /** FR-4.1 default separator set. */
        val SEPARATORS = setOf("lalu", "terus", "terakhir")

        /** Conversational padding with no structural meaning. */
        val FILLER_WORDS = setOf(
            "tolong", "mau", "beli", "minta", "kasih", "ya", "dong",
            "sama", "dan", "juga", "aja", "saja", "nya"
        )

        /** Prototype range is 1–10; the belas/puluh/ratus grammar is out of scope. */
        val NUMBER_WORDS = mapOf(
            "satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4, "lima" to 5,
            "enam" to 6, "tujuh" to 7, "delapan" to 8, "sembilan" to 9, "sepuluh" to 10
        )

        val FRACTION_WORDS = mapOf(
            "setengah" to SpeechConstants.QUANTITY_SCALE / 2,
            "seperempat" to SpeechConstants.QUANTITY_SCALE / 4
        )

        /**
         * The `se-` prefix fuses "one" into the unit, so these all mean 1.
         *
         * Listed explicitly rather than matched by prefix: "sedap" begins with
         * "se" and is part of "mie sedap", so a general rule would read a
         * quantity out of a product name.
         */
        val SE_PREFIX_FORMS = setOf(
            "sebungkus", "sebotol", "sekaleng", "sekotak", "sebiji", "sebuah",
            "sebatang", "sedus", "sepak", "sekilo", "seons", "serenteng"
        )
    }
}
