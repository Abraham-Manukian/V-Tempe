package com.vtempe.shared.data.di

import org.koin.core.Koin

/**
 * Для iOS: в Kotlin/Native не всегда удобно/доступно доставать текущий Koin из GlobalContext.
 * Поэтому сохраняем ссылку на Koin при инициализации (см. app-ios).
 */
object KoinProvider {
    var koin: Koin? = null
}


