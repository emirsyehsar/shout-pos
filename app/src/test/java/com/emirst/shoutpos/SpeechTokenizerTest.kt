package com.emirst.shoutpos

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SpeechTokenizer is pure Kotlin with no Android imports, so the Indonesian
 * rules can be checked here rather than on a device.
 */
class SpeechTokenizerTest {

    private val tokenizer = SpeechTokenizer()

    @Test
    fun `splits on separator into one segment per item`() {
        val segments = tokenizer.tokenize("indomie goreng dua lalu teh botol tiga")

        assertEquals(2, segments.size)
        assertEquals("indomie goreng", segments[0].productPhrase)
        assertEquals(qty(2), segments[0].quantityMilli)
        assertEquals("teh botol", segments[1].productPhrase)
        assertEquals(qty(3), segments[1].quantityMilli)
    }

    @Test
    fun `keeps both readings when a product name ends in a digit`() {
        val segment = tokenizer.tokenize("gudang garam surya 16").single()

        assertEquals("gudang garam surya", segment.productPhrase)
        assertEquals("gudang garam surya 16", segment.fullPhrase)
    }

    @Test
    fun `does not read a quantity out of mie sedap`() {
        val segment = tokenizer.tokenize("mie sedap goreng dua").single()

        assertEquals("mie sedap goreng", segment.productPhrase)
        assertEquals(qty(2), segment.quantityMilli)
    }

    @Test
    fun `handles setengah as a half`() {
        val segment = tokenizer.tokenize("teh botol setengah").single()

        assertEquals("teh botol", segment.productPhrase)
        assertEquals(qty(0.5), segment.quantityMilli)
    }

    @Test
    fun `strips filler words and punctuation`() {
        val segment = tokenizer.tokenize("Tolong, indomie goreng dua ya!").single()

        assertEquals("indomie goreng", segment.productPhrase)
        assertEquals(qty(2), segment.quantityMilli)
    }

    @Test
    fun `keeps unit words that are part of a product name`() {
        val segment = tokenizer.tokenize("teh botol kotak tiga").single()

        assertEquals("teh botol kotak", segment.productPhrase)
        assertEquals(qty(3), segment.quantityMilli)
    }

    @Test
    fun `defaults to one when no quantity is spoken`() {
        val segment = tokenizer.tokenize("teh pucuk").single()

        assertEquals("teh pucuk", segment.productPhrase)
        assertEquals(qty(1), segment.quantityMilli)
    }

    @Test
    fun `accepts digits from the recognizer as well as words`() {
        val segment = tokenizer.tokenize("indomie rendang 4").single()

        assertEquals("indomie rendang", segment.productPhrase)
        assertEquals(qty(4), segment.quantityMilli)
    }

    @Test
    fun `returns nothing for an empty transcript`() {
        assertEquals(emptyList<SpeechSegment>(), tokenizer.tokenize("   "))
    }
}
