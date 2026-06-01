package com.vtempe.ui.util

import kotlinx.datetime.DayOfWeek

/** "Mon" / "Tue" / … — key used in NutritionPlan.mealsByDay */
internal fun DayOfWeek.toShortKey(): String = when (this) {
    DayOfWeek.MONDAY    -> "Mon"
    DayOfWeek.TUESDAY   -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY  -> "Thu"
    DayOfWeek.FRIDAY    -> "Fri"
    DayOfWeek.SATURDAY  -> "Sat"
    DayOfWeek.SUNDAY    -> "Sun"
}

/** 0 = Mon … 6 = Sun — index into weeklyVolumes list */
internal fun DayOfWeek.toWeekIndex(): Int = when (this) {
    DayOfWeek.MONDAY    -> 0
    DayOfWeek.TUESDAY   -> 1
    DayOfWeek.WEDNESDAY -> 2
    DayOfWeek.THURSDAY  -> 3
    DayOfWeek.FRIDAY    -> 4
    DayOfWeek.SATURDAY  -> 5
    DayOfWeek.SUNDAY    -> 6
}
