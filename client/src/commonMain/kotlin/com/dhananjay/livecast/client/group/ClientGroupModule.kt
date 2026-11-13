package com.dhananjay.livecast.client.group

import org.koin.core.module.Module
import org.koin.dsl.module

expect val groupPlatformModule: Module

val clientGroupModule: Module = module {
    includes(groupPlatformModule)
}
