package com.emirst.shoutpos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Final price arithmetic. Prices are the real ones from sampledata.json, so a
 * failure here reads as a wrong bill rather than a wrong number.
 */
class SpeechLineItemTest {

    private val indomieGoreng = ProductData("p01", "indomie goreng", 3500)
    private val tehBotol = ProductData("p12", "teh botol", 5000)
    private val suryaSixteen = ProductData("p09", "gudang garam surya 16", 37000)

    @Test
    fun `two packets cost twice the unit price`() {
        val item = SpeechLineItem("indomie goreng", indomieGoreng, qty(2))

        assertEquals(7_000, item.totalPrice)
    }

    @Test
    fun `a single item costs the unit price`() {
        val item = SpeechLineItem("teh botol", tehBotol, qty(1))

        assertEquals(5_000, item.totalPrice)
    }

    @Test
    fun `setengah costs half the unit price`() {
        val item = SpeechLineItem("teh botol", tehBotol, qty(0.5))

        assertEquals(2_500, item.totalPrice)
    }

    @Test
    fun `seperempat costs a quarter of the unit price`() {
        val item = SpeechLineItem("teh botol", tehBotol, qty(0.25))

        assertEquals(1_250, item.totalPrice)
    }

    @Test
    fun `the most expensive product at the top of the prototype range still totals exactly`() {
        val item = SpeechLineItem("gudang garam surya 16", suryaSixteen, qty(10))

        assertEquals(370_000, item.totalPrice)
    }

    @Test
    fun `an odd price times a half stays a whole Rupiah`() {
        val racikNasiGoreng = ProductData("p18", "racik nasi goreng", 2_500)
        val item = SpeechLineItem("racik nasi goreng", racikNasiGoreng, qty(0.5))

        assertEquals(1_250, item.totalPrice)
    }

    @Test
    fun `an unmatched line has no total`() {
        val item = SpeechLineItem("barang tidak dikenal", product = null, quantityMilli = qty(3))

        assertEquals(0, item.totalPrice)
        assertFalse(item.isMatched)
    }

    @Test
    fun `a matched line reports itself as matched`() {
        val item = SpeechLineItem("indomie goreng", indomieGoreng, qty(1))

        assertTrue(item.isMatched)
    }
}
