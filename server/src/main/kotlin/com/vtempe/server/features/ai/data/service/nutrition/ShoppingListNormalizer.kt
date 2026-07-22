package com.vtempe.server.features.ai.data.service.nutrition

import java.util.Locale

/**
 * Deduplicates and normalizes a flat shopping list produced from meal ingredients.
 *
 * The LLM (and our own ingredient aggregation) tends to emit the same food multiple
 * times with different quantities and word order — e.g. "100г риса" and "Рис 480г".
 * This normalizer:
 *   1. Parses each entry into (name, quantity, unit).
 *   2. Canonicalizes the name (lowercase, strip quantity/unit, normalize Russian grammatical case).
 *   3. Groups by canonical name and sums quantities that share a compatible unit.
 *   4. Formats as "Рис — 580 г" / "Яйца — 11 шт".
 *   5. Splits out spices/condiments into a trailing section (they rarely have meaningful amounts).
 *
 * It is intentionally conservative: when a quantity or unit cannot be parsed the entry is kept
 * as-is (deduplicated by canonical name) so nothing is silently dropped.
 */
object ShoppingListNormalizer {

    private data class Parsed(
        val original: String,
        val canonicalName: String,
        val displayName: String,
        val quantity: Double?,
        val unit: String?
    )

    /** Spice / condiment markers — grouped into a separate section, amounts not summed. */
    private val SPICE_MARKERS = listOf(
        "соль", "перец", "специ", "приправ", "зелен", "укроп", "петрушк", "базилик",
        "уксус", "лимонный сок", "сок лимон", "паприк", "куркум", "корица", "ванил",
        "сода", "разрыхлит", "чеснок", "имбир", "горчиц", "соус",
        "salt", "pepper", "spice", "season", "herb", "vinegar", "lemon juice",
        "paprika", "turmeric", "cinnamon", "vanilla", "baking soda", "garlic", "ginger",
        "mustard", "sauce", "parsley", "dill", "basil"
    )

    // Prep/state descriptors that shouldn't split an ingredient into separate shopping-list
    // lines — "chicken breast", "cooked chicken breast", and "chicken breast, diced" are all
    // the same thing to buy. Stripped before canonicalizing (and from the display name).
    private val DESCRIPTOR_WORDS = listOf(
        "cooked", "raw", "frozen", "canned", "diced", "chopped", "sliced", "shredded",
        "minced", "grated", "boiled", "steamed", "grilled", "roasted", "baked", "peeled",
        "in juice", "in water", "in syrup", "drained", "fresh", "dried", "whole", "ground",
        "варен\\w*", "отварн\\w*", "жарен\\w*", "тушен\\w*", "замороженн\\w*", "консервированн\\w*",
        "нарезанн\\w*", "измельченн\\w*", "тёртый", "тертый", "свеж\\w*", "сушен\\w*", "очищенн\\w*",
    )

    // Units that are interchangeable for summing.
    private val GRAM_UNITS = setOf("г", "гр", "g", "грамм", "грамма", "граммов")
    private val ML_UNITS = setOf("мл", "ml")
    private val KG_UNITS = setOf("кг", "kg")
    private val L_UNITS = setOf("л", "l", "литр")
    private val PIECE_UNITS = setOf("шт", "штук", "штука", "штуки", "pcs", "pc", "piece", "pieces")
    private val SPOON_UNITS = setOf(
        "ст.л", "ст. л", "стл", "tbsp", "tablespoon", "ч.л", "ч. л", "чл", "tsp", "teaspoon"
    )

    private val QUANTITY_REGEX = Regex(
        """(\d+[.,]?\d*)\s*(кг|kg|мл|ml|литр|л|l|грамм\w*|гр|г|g|шт\w*|pcs|pieces|piece|pc|ст\.?\s?л|ч\.?\s?л|tbsp|tablespoon|tsp|teaspoon|стакан\w*|cup\w*)?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Normalizes [rawItems] into a deduplicated, formatted shopping list.
     * Spices/condiments are placed at the end (grouped, after a blank separator if any food exists).
     */
    fun normalize(rawItems: List<String>): List<String> {
        val parsed = rawItems
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::parseEntry)

        val (spices, foods) = parsed.partition { isSpice(it.canonicalName) }

        val foodLines = groupAndFormat(foods)
        val spiceLines = groupAndFormat(spices)

        return foodLines + spiceLines
    }

    private fun groupAndFormat(items: List<Parsed>): List<String> {
        val grouped = LinkedHashMap<String, MutableList<Parsed>>()
        items.forEach { p ->
            grouped.getOrPut(p.canonicalName) { mutableListOf() }.add(p)
        }
        return grouped.values.map { group -> formatGroup(group) }
    }

    private fun formatGroup(group: List<Parsed>): String {
        val displayName = group.first().displayName
        // Bucket quantities by normalized unit family so we can sum compatible ones.
        val byUnit = LinkedHashMap<String, Double>()
        var hasUnparsed = false
        group.forEach { p ->
            val q = p.quantity
            val u = p.unit?.let(::normalizeUnit)
            if (q != null && u != null) {
                byUnit[u] = (byUnit[u] ?: 0.0) + q
            } else if (q == null && u == null) {
                hasUnparsed = true
            } else if (q != null) {
                // quantity without a recognizable unit — track under a blank bucket
                byUnit[""] = (byUnit[""] ?: 0.0) + q
            }
        }

        if (byUnit.isEmpty()) return displayName

        val parts = byUnit.entries.map { (unit, qty) ->
            val qtyStr = formatQuantity(qty)
            if (unit.isBlank()) qtyStr else "$qtyStr $unit"
        }
        val suffix = if (hasUnparsed) parts + "+" else parts
        return "$displayName — ${suffix.joinToString(" + ")}"
    }

    private fun normalizeUnit(unit: String): String {
        val u = unit.lowercase(Locale.ROOT).replace(".", "").replace(" ", "").trim()
        return when (u) {
            in GRAM_UNITS -> "г"
            in KG_UNITS -> "кг"
            in ML_UNITS -> "мл"
            in L_UNITS -> "л"
            in PIECE_UNITS -> "шт"
            in SPOON_UNITS -> "ст.л"
            else -> unit.trim()
        }
    }

    private fun formatQuantity(q: Double): String =
        if (q == q.toLong().toDouble()) q.toLong().toString()
        else String.format(Locale.US, "%.1f", q)

    private fun parseEntry(raw: String): Parsed {
        val match = QUANTITY_REGEX.find(raw)
        var quantity: Double? = null
        var unit: String? = null
        var nameWithoutQty = raw

        if (match != null && match.value.isNotBlank()) {
            quantity = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            unit = match.groupValues[2].takeIf { it.isNotBlank() }
            nameWithoutQty = raw.removeRange(match.range).trim()
        }

        val displayName = cleanName(nameWithoutQty).ifBlank { cleanName(raw) }
        return Parsed(
            original = raw,
            canonicalName = canonicalName(displayName),
            displayName = displayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
            quantity = quantity,
            unit = unit
        )
    }

    private val DESCRIPTOR_REGEX = Regex(
        """\b(${DESCRIPTOR_WORDS.joinToString("|")})\b""",
        RegexOption.IGNORE_CASE
    )

    private fun cleanName(raw: String): String =
        raw
            // Parenthetical notes ("(frozen)", "(in juice)") and trailing comma-clauses
            // ("chicken breast, diced") carry no shopping-list-relevant information.
            .replace(Regex("""\([^)]*\)"""), " ")
            .substringBefore(',')
            .replace(Regex("""[\d.,]+"""), " ")
            .replace(Regex("""[—\-–]+"""), " ")
            .replace(DESCRIPTOR_REGEX, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Canonical key for grouping. Lowercases and strips common Russian grammatical endings so
     * "риса"/"рис"/"рисом" collapse to the same key. Heuristic and intentionally light-touch.
     */
    private fun canonicalName(name: String): String {
        var n = name.lowercase(Locale.ROOT).trim()
        // Drop trailing genitive/instrumental endings for common Russian nouns.
        val endings = listOf("ами", "ями", "ов", "ев", "ей", "ам", "ям", "ом", "ем", "ой", "ы", "и", "а", "я", "у", "е")
        for (e in endings) {
            if (n.length - e.length >= 3 && n.endsWith(e)) {
                n = n.dropLast(e.length)
                break
            }
        }
        return n
    }

    private fun isSpice(canonicalName: String): Boolean =
        SPICE_MARKERS.any { canonicalName.contains(it.take(maxOf(3, it.length - 2))) }
}
