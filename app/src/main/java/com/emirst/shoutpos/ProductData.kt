package com.emirst.shoutpos

/**
 * One catalog entry, exactly as it appears in res/raw/sampledata.json.
 *
 * [price] is whole Rupiah. Indonesian currency has no sub-unit in practice, so
 * there is no reason for this to be anything but an integer.
 */
data class ProductData(
    val id: String,
    val name: String,
    val price: Int
)
