package com.vtempe.shared.domain.nutrition

/** No meal plan can be built without the user's forbidden foods. Surfaced as an error instead
 *  of a plan that silently ignores allergies or diet. */
class NoSafeMealPlanException(message: String) : IllegalStateException(message)
