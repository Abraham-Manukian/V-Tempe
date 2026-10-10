package com.vtempe.ui.presenter

import com.vtempe.shared.domain.nutrition.NoSafeMealPlanException
import com.vtempe.shared.domain.training.NoSafeTrainingPlanException

/**
 * The plan was refused because the user's allergies, diet or injuries leave no safe meal or
 * exercise — not a network or server problem. Screens say so and point to the profile instead of
 * suggesting a retry would help.
 */
internal fun Throwable.isPlanRestrictionConflict(): Boolean =
    this is NoSafeMealPlanException || this is NoSafeTrainingPlanException
