package com.vtempe.shared.domain.nutrition

/** Keyword dictionaries of the food-restriction contract. Keep in sync with the server's
 *  AiNutritionSafetyPolicy.kt — `testdata/food-restriction-cases.tsv` fails on drift. */
internal object FoodKeywords {
    val allergyWords = setOf("allergy", "allergic", "intolerance", "intolerant", "allergia", "аллерг", "неперенос")

    val dietaryStopWords = setOf(
        "allergy", "allergies", "allergic", "intolerance", "intolerant", "avoid", "without", "exclude",
        "allergen", "sensitivity", "аллергия", "аллергии", "непереносимость", "исключить", "избегать",
        "без", "на", "к", "и"
    )

    /** Words in an allergy/health line that name a category. */
    val allergyLineTags: Map<FoodAllergen, Set<String>> = mapOf(
        FoodAllergen.Peanut to setOf("peanut", "арахис"),
        FoodAllergen.TreeNut to setOf("nut", "орех", "миндал", "кешью", "hazelnut", "walnut"),
        FoodAllergen.Gluten to setOf("gluten", "глютен", "целиак", "wheat", "пшен"),
        FoodAllergen.Soy to setOf("soy", "soya", "соя"),
        FoodAllergen.Egg to setOf("egg", "яйц"),
        FoodAllergen.Fish to setOf("fish", "рыб", "лосос", "тунец", "треск"),
        FoodAllergen.Shellfish to setOf("shellfish", "shrimp", "prawn", "кревет", "моллюск", "мид"),
        FoodAllergen.Sesame to setOf("sesame", "кунжут", "тахин"),
        FoodAllergen.Dairy to setOf("dairy", "milk allergy", "молоч", "казеин", "сыворот"),
        FoodAllergen.Lactose to setOf("lactose", "лактоз"),
        FoodAllergen.Meat to setOf("meat", "beef", "pork", "lamb", "мяс", "говя", "свин", "барани", "телят"),
        FoodAllergen.Poultry to setOf("poultry", "chicken", "turkey", "duck", "птиц", "куриц", "индейк", "утк"),
    )

    /** Words in a food text that put it in a category. */
    val foodTags: Map<FoodAllergen, Set<String>> = mapOf(
        FoodAllergen.Dairy to setOf(
            "milk", "dairy", "cream", "cheese", "yogurt", "kefir", "curd", "casein", "whey",
            "молок", "сливк", "сыр", "йогурт", "кефир", "творог", "сметан", "казеин", "сыворот"
        ),
        FoodAllergen.Lactose to setOf("lactose", "milk", "cream", "whey", "casein", "йогурт", "кефир", "творог", "молок", "лактоз"),
        FoodAllergen.Peanut to setOf("peanut", "арахис", "groundnut"),
        FoodAllergen.TreeNut to setOf("almond", "walnut", "cashew", "hazelnut", "pecan", "nut", "орех", "миндал"),
        FoodAllergen.Gluten to setOf("gluten", "wheat", "barley", "rye", "pasta", "bread", "flour", "глютен", "пшен", "рож", "ячмен", "мук", "хлеб", "макарон"),
        FoodAllergen.Soy to setOf("soy", "soya", "tofu", "соя", "тофу"),
        FoodAllergen.Egg to setOf("egg", "eggs", "яйц"),
        FoodAllergen.Fish to setOf("fish", "salmon", "tuna", "cod", "рыб", "лосос", "тун", "треск"),
        FoodAllergen.Shellfish to setOf("shrimp", "prawn", "mussel", "clam", "lobster", "oyster", "кревет", "мид", "устриц", "омар", "ракообраз"),
        FoodAllergen.Sesame to setOf("sesame", "tahini", "кунжут", "тахин"),
        FoodAllergen.Meat to setOf("meat", "beef", "pork", "lamb", "veal", "мяс", "говя", "свин", "барани", "телят"),
        FoodAllergen.Poultry to setOf("poultry", "chicken", "turkey", "duck", "птиц", "куриц", "курин", "индейк", "утк"),
        // "мед" alone deliberately excluded — it substring-matches "медленно" (slowly).
        FoodAllergen.Honey to setOf("honey", "мёд", "медов"),
    )

    val veganForbidden = setOf(
        FoodAllergen.Dairy, FoodAllergen.Egg, FoodAllergen.Fish, FoodAllergen.Shellfish,
        FoodAllergen.Meat, FoodAllergen.Poultry, FoodAllergen.Honey
    )
    val vegetarianForbidden = setOf(FoodAllergen.Fish, FoodAllergen.Shellfish, FoodAllergen.Meat, FoodAllergen.Poultry)

    val lactoseFreeMarkers = setOf("lactose-free", "lactose free", "безлактоз", "без лактоз")

    /** The word right before a dairy keyword that makes it a plant alternative ("oat milk"). */
    val plantDairyPrefixes = listOf(
        "almond", "soy", "oat", "coconut", "rice", "plant", "vegan", "cashew",
        "кокосов", "растительн", "овсян", "миндальн", "рисов", "соев", "орехов"
    )
    val dairyFreeLabels = setOf("dairy-free", "milk-free", "non-dairy")

    /** Safe words that merely contain a forbidden keyword ("buckwheat" ⊃ "wheat"). */
    val safeCompounds: Map<String, Set<String>> = mapOf(
        "wheat" to setOf("buckwheat"),
        "egg" to setOf("eggplant"),
        "nut" to setOf("nutmeg", "butternut"),
        "рож" to setOf("морож", "дрожж"),
    )
}
