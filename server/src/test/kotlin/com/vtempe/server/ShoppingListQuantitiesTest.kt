package com.vtempe.server

import com.vtempe.server.features.ai.data.service.normalizeBundle
import com.vtempe.server.features.ai.data.service.normalizeNutritionPlan
import com.vtempe.server.features.ai.data.service.nutrition.ShoppingListNormalizer
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import com.vtempe.server.shared.dto.profile.AiProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** VT-AUD-030 (one source of quantities, idempotent) and VT-AUD-031 (tsp ≠ tbsp). */
class ShoppingListQuantitiesTest {

    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val profile = AiProfile(age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN", experienceLevel = 2)

    private fun plan(shoppingList: List<String>) = AiNutritionResponse(
        weekIndex = 0,
        mealsByDay = days.associateWith { day ->
            (1..3).map { i -> AiMeal("$day meal $i", listOf("100g rice", "vegetables $day $i"), 700, Macros(50, 20, 80, 700)) }
        },
        shoppingList = shoppingList,
    )

    @Test
    fun `quantities are summed once, however often the plan is normalized`() {
        val section = normalizeNutritionPlan(plan(listOf("2100g rice")), Locale.ENGLISH, profile)
        val bundle = normalizeBundle(AiBootstrapResponse(nutritionPlan = section), Locale.ENGLISH, profile).nutritionPlan!!
        val again = normalizeNutritionPlan(bundle, Locale.ENGLISH, profile)
        for (p in listOf(section, bundle, again)) {
            assertEquals("Rice — 2100 г", p.shoppingList.first { it.startsWith("Rice") })
        }
    }

    @Test
    fun `an empty or partial model shopping list does not change the totals`() {
        for (list in listOf(emptyList(), listOf("rice"), listOf("500g rice", "salt"))) {
            val normalized = normalizeNutritionPlan(plan(list), Locale.ENGLISH, profile)
            assertEquals("Rice — 2100 г", normalized.shoppingList.first { it.startsWith("Rice") }, list.toString())
        }
    }

    @Test
    fun `teaspoons and tablespoons stay distinct`() {
        assertEquals(listOf("Olive oil — 1 ст.л + 1 ч.л"), ShoppingListNormalizer.normalize(listOf("1 tbsp olive oil", "1 tsp olive oil")))
        assertEquals(listOf("Olive oil — 3 ч.л"), ShoppingListNormalizer.normalize(listOf("1 tsp olive oil", "2 teaspoons olive oil")))
        val ru = ShoppingListNormalizer.normalize(listOf("Оливковое масло 1 ст.л", "Оливковое масло 2 ч.л"))
        assertTrue(ru.single().contains("1 ст.л") && ru.single().contains("2 ч.л"), ru.toString())
        assertEquals(listOf("Rice — 1.5 кг + 200 г"), ShoppingListNormalizer.normalize(listOf("1.5 kg rice", "200 g rice")))
    }
}
