package com.vtempe.server.features.ai.domain.model

enum class TrainingMode(val wireValue: String) {
    AUTO("auto"),
    GYM("gym"),
    HOME("home"),
    OUTDOOR("outdoor"),
    MIXED("mixed");

    companion object {
        fun fromWire(raw: String?): TrainingMode {
            val normalized = raw
                ?.trim()
                ?.lowercase()
                ?.replace(' ', '_')
                ?.replace('-', '_')
                ?.takeIf { it.isNotEmpty() }
                ?: return AUTO

            return when (normalized) {
                AUTO.wireValue -> AUTO
                GYM.wireValue, "fitness_gym", "gym_only", "зал", "в_зале" -> GYM
                HOME.wireValue, "home_bodyweight", "home_equipment", "дом", "дома" -> HOME
                OUTDOOR.wireValue, "street", "outside", "улица", "на_улице" -> OUTDOOR
                MIXED.wireValue, "hybrid", "комбинированный", "смешанный" -> MIXED
                else -> AUTO
            }
        }
    }
}
