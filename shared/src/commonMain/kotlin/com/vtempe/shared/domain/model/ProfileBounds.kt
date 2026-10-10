package com.vtempe.shared.domain.model

/**
 * Body measurements the app can compute plans for (VT-AUD-035). These are wide technical
 * plausibility limits — a value outside them is a typo or corrupt data, not a person — and NOT
 * clinical eligibility rules. The server enforces the same limits on every AI request.
 */
object ProfileBounds {
    val AGE_YEARS = 10..110
    val HEIGHT_CM = 50..272
    val WEIGHT_KG = 20.0..400.0

    enum class Field { AGE, HEIGHT, WEIGHT }

    /** Fields that are missing, not finite or outside the supported range. */
    fun invalidFields(age: Int?, heightCm: Int?, weightKg: Double?): Set<Field> = buildSet {
        if (age == null || age !in AGE_YEARS) add(Field.AGE)
        if (heightCm == null || heightCm !in HEIGHT_CM) add(Field.HEIGHT)
        if (weightKg == null || !weightKg.isFinite() || weightKg !in WEIGHT_KG) add(Field.WEIGHT)
    }

    fun isSupported(profile: Profile): Boolean =
        invalidFields(profile.age, profile.heightCm, profile.weightKg).isEmpty()

    /** Parses a weight typed with either decimal separator ("78.5" or "78,5"). */
    fun parseWeight(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()
}

/**
 * The profile with measurements pulled into the supported range, for calculations only. A
 * profile stored before validation existed (or restored from sync) must not crash or poison the
 * offline plan; it is never written back.
 */
fun Profile.clampedForCalculations(): Profile = copy(
    age = age.coerceIn(ProfileBounds.AGE_YEARS),
    heightCm = heightCm.coerceIn(ProfileBounds.HEIGHT_CM),
    weightKg = if (weightKg.isFinite()) weightKg.coerceIn(ProfileBounds.WEIGHT_KG) else ProfileBounds.WEIGHT_KG.start,
)
