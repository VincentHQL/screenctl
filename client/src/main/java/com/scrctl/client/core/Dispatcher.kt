package com.scrctl.client.core

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val scrctlDispatcher: ScrctlDispatchers)

enum class ScrctlDispatchers {
    Main,
    IO,
}