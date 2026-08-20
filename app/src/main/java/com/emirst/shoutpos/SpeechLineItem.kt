package com.emirst.shoutpos

/**
 * One resolved line of a bill: what was heard, what it matched, how many.
 *
 * [product] is null when nothing in the catalog matched. An unmatched segment
 * is kept rather than dropped — a line the shopkeeper can see and correct is
 * far more useful than an item that silently vanished.
 */
data class SpeechLineItem(
    val spokenPhrase: String,
    val product: ProductData?,
    val quantityMilli: Int
) {

    /**
     * Whole Rupiah. Integer arithmetic throughout: the quantity is scaled by
     * [SpeechConstants.QUANTITY_SCALE] rather than held as a float, because
     * float quantities multiplied by Rupiah prices produce off-by-one totals
     * that are visible to the user and destroy trust in a calculator.
     */
    val totalPrice: Int
        get() = product?.let { it.price * quantityMilli / SpeechConstants.QUANTITY_SCALE } ?: 0

    val isMatched: Boolean get() = product != null
}
