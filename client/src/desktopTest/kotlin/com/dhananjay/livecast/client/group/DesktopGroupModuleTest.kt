package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.authModule
import com.dhananjay.livecast.firestore.grpc.FirestoreConfigSource
import com.dhananjay.livecast.firestore.grpc.FirestoreConnectionConfig
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs

class DesktopGroupModuleTest {

    @Test
    fun resolvesUnsupportedWhenConfigAbsent() {
        val fakeConfigModule = module {
            single<FirestoreConfigSource> {
                object : FirestoreConfigSource {
                    override fun load(): FirestoreConnectionConfig? = null
                }
            }
        }

        val app = koinApplication {
            allowOverride(true)
            modules(authModule, clientGroupModule, fakeConfigModule)
        }

        val groupRepo = app.koin.get<GroupRepository>()
        val memberRepo = app.koin.get<GroupMemberRepository>()
        val deviceRepo = app.koin.get<GroupDeviceRepository>()
        val appEventRepo = app.koin.get<AppEventRepository>()

        assertIs<UnsupportedGroupRepository>(groupRepo)
        assertIs<UnsupportedGroupMemberRepository>(memberRepo)
        assertIs<UnsupportedGroupDeviceRepository>(deviceRepo)
        assertIs<UnsupportedAppEventRepository>(appEventRepo)

        app.close()
    }
}
