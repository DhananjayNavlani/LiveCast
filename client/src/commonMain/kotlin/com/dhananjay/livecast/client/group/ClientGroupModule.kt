package com.dhananjay.livecast.client.group

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect val groupPlatformModule: Module

val clientGroupModule: Module = module {
    includes(groupPlatformModule)
    viewModel { MyDevicesViewModel(get(), get(), get(), get()) }
    viewModel { GroupManagementViewModel(get(), get(), get()) }
    viewModel { VerifyEmailViewModel(get(), get()) }
    viewModel { (deviceId: String, groupId: String) -> DeviceDetailViewModel(deviceId, groupId, get()) }
}
