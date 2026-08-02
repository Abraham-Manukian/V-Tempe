package com.vtempe.server.features.ai.data.llm

class StubLLMClient(private val message: String) : LLMClient {
    override suspend fun generateJson(prompt: String, schema: com.vtempe.server.features.ai.data.llm.schema.ResponseSchema?): String {
        val lower = prompt.lowercase()
        return when {
            "generate a weekly training plan" in lower -> trainingStub()
            "generate a weekly menu" in lower -> nutritionStub()
            "provide 5-7 concise personalized sleep tips" in lower -> sleepStub()
            "return strict json" in lower && "trainingplan" in lower -> chatStub()
            else -> chatStub()
        }
    }

    private fun trainingStub(): String =
        """{"weekIndex":0,"workouts":[{"id":"stub_w_0","date":"2024-01-01","sets":[{"exerciseId":"squat","reps":8,"weightKg":40.0,"rpe":7.5},{"exerciseId":"bench","reps":10,"weightKg":30.0,"rpe":7.0}]}]}"""

    private fun nutritionStub(): String =
        """{"weekIndex":0,"mealsByDay":{"Mon":[{"name":"Oatmeal","ingredients":["oats","milk","banana"],"kcal":500,"macros":{"proteinGrams":30,"fatGrams":15,"carbsGrams":60,"kcal":500}}]}}"""

    private fun sleepStub(): String =
        """{"messages":["Stick to a consistent bedtime.","Limit caffeine after lunch."],"disclaimer":"Coaching tips only."}"""

    private fun chatStub(): String =
        """{"reply":"${message.replace("\"", "\\\"")}","trainingPlan":null,"nutritionPlan":null,"sleepAdvice":null}"""
}

