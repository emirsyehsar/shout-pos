package com.emirst.shoutpos

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Read-only catalog backed by res/raw/sampledata.json — the prototype's stand-in
 * for a database. Loaded once, held in memory, never written to.
 *
 * Lookup is fuzzy by design. An exact string comparison fails the moment the
 * recognizer hears "the botol" for "teh botol", which is precisely the case
 * this prototype exists to measure.
 */
class SpeechModel(private val appContext: Context) {

    private val products: List<ProductData> by lazy { loadProducts() }

    fun allProducts(): List<ProductData> = products

    fun findProduct(phrase: String): ProductData? = findBestMatch(phrase)?.product

    /**
     * Best catalog entry for a spoken phrase, or null when nothing scores above
     * [SpeechConstants.MATCH_THRESHOLD].
     */
    fun findBestMatch(phrase: String): ProductMatch? {
        val spokenTokens = phrase.split(WHITESPACE).filter { it.isNotBlank() }
        if (spokenTokens.isEmpty()) return null

        return products
            .map { product ->
                ProductMatch(product, score(spokenTokens, product.name.split(WHITESPACE)))
            }
            .filter { it.score >= SpeechConstants.MATCH_THRESHOLD }
            .maxByOrNull { it.score }
    }

    /**
     * Ratio of tokens shared with the candidate, divided by the longer of the
     * two token lists. Dividing by the longer list is what keeps "teh botol"
     * from matching "teh botol kotak" as strongly as it matches "teh botol" —
     * an unmatched extra token in the candidate costs score.
     */
    private fun score(spokenTokens: List<String>, candidateTokens: List<String>): Double {
        val unclaimed = candidateTokens.toMutableList()
        var matched = 0
        for (token in spokenTokens) {
            val hit = unclaimed.firstOrNull { tokensMatch(token, it) } ?: continue
            unclaimed.remove(hit)
            matched++
        }
        return matched.toDouble() / maxOf(spokenTokens.size, candidateTokens.size)
    }

    /** Exact match, or close enough that a recognizer slip is the likely cause. */
    private fun tokensMatch(spoken: String, candidate: String): Boolean {
        if (spoken == candidate) return true
        val tolerance = if (minOf(spoken.length, candidate.length) <= SHORT_TOKEN) 1 else 2
        return editDistance(spoken, candidate) <= tolerance
    }

    private fun editDistance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val substitution = previous[j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    private fun loadProducts(): List<ProductData> = try {
        val json = appContext.resources.openRawResource(R.raw.sampledata)
            .bufferedReader()
            .use { it.readText() }
        val array = JSONObject(json).getJSONArray(FIELD_PRODUCTS)
        (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            ProductData(
                id = entry.getString(FIELD_ID),
                name = entry.getString(FIELD_NAME).lowercase(),
                price = entry.getInt(FIELD_PRICE)
            )
        }.also {
            Log.i(SpeechConstants.LOG_TAG, "Catalog loaded: ${it.size} products")
        }
    } catch (error: Exception) {
        // A malformed asset must not take the app down; an empty catalog simply
        // matches nothing, which the UI already reports.
        Log.e(SpeechConstants.LOG_TAG, "Catalog load failed: ${error.javaClass.simpleName}")
        emptyList()
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val SHORT_TOKEN = 4
        const val FIELD_PRODUCTS = "products"
        const val FIELD_ID = "id"
        const val FIELD_NAME = "name"
        const val FIELD_PRICE = "price"
    }
}
