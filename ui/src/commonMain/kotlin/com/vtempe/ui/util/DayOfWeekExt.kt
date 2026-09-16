package com.vtempe.ui.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

private val shortDayKeys = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "Mon" / "Tue" / … — key used in NutritionPlan.mealsByDay */
internal fun DayOfWeek.toShortKey(): String = shortDayKeys[toWeekIndex()]

/** 0 = Mon … 6 = Sun — index into weeklyVolumes list */
internal fun DayOfWeek.toWeekIndex(): Int = isoDayNumber - 1
