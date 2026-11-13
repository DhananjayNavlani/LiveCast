package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.auth.AuthState
import com.dhananjay.livecast.auth.SessionProvider
import com.dhananjay.livecast.auth.User
import com.dhananjay.livecast.firestore.grpc.DocumentStore
import com.dhananjay.livecast.firestore.grpc.FirestoreAlreadyExistsException
import com.dhananjay.livecast.firestore.grpc.FirestoreDoc
import com.dhananjay.livecast.firestore.grpc.FirestorePermissionDeniedException
import com.dhananjay.livecast.firestore.grpc.FirestoreQuery
import com.dhananjay.livecast.firestore.grpc.FirestoreWrite
import com.dhananjay.livecast.protocol.Group
import com.dhananjay.livecast.protocol.GroupFields
import com.dhananjay.livecast.protocol.GroupMembership
import com.dhananjay.livecast.protocol.GroupRole
import com.dhananjay.livecast.protocol.HostDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeDocumentStore : DocumentStore {
    val committedWrites = mutableListOf<List<FirestoreWrite>>()
    val queriedQueries = mutableListOf<FirestoreQuery>()
    val docs = mutableMapOf<String, FirestoreDoc>()
    var onCommit: ((List<FirestoreWrite>) -> Unit)? = null
    var onGet: ((String) -> FirestoreDoc?)? = null
    val queryFlows = mutableMapOf<FirestoreQuery, MutableSharedFlow<List<FirestoreDoc>>>()
    var customListenQuery: ((FirestoreQuery) -> Flow<List<FirestoreDoc>>)? = null

    override suspend fun get(path: String): FirestoreDoc? {
        onGet?.let { return it(path) }
        return docs[path]
    }

    override fun listenDocument(path: String): Flow<FirestoreDoc?> {
        return flowOf(docs[path])
    }

    override fun listenQuery(query: FirestoreQuery): Flow<List<FirestoreDoc>> {
        queriedQueries.add(query)
        customListenQuery?.let { return it(query) }
        return queryFlows.getOrPut(query) { MutableSharedFlow(replay = 1) }
    }

    override suspend fun createWithAutoId(collectionPath: String, fields: Map<String, Any?>): String = "auto-id"

    override suspend fun commit(writes: List<FirestoreWrite>) {
        onCommit?.invoke(writes)
        committedWrites.add(writes)
    }
}

class FakeSessionProvider(
    override var currentUser: User? = null
) : SessionProvider {
    private val _authState = MutableStateFlow<AuthState>(
        currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun getIdToken(forceRefresh: Boolean): String? = "fake-token"
}

class DocumentStoreGroupRepositoriesTest {

    private val idRegex = Regex("^[A-Za-z0-9]{20}$")

    @Test
    fun createGroupCommitsGroupAndOwnerInOneBatch() = runTest {
        val store = FakeDocumentStore()
        val user = User(id = "u1", email = "o@x.com", displayName = "Owner", emailVerified = true)
        val session = FakeSessionProvider(user)
        val repo = DocumentStoreGroupRepository(store, session)

        val result = repo.createGroup("Family")
        assertTrue(result.isSuccess)
        val groupId = result.getOrThrow()
        assertTrue(idRegex.matches(groupId), "Expected 20-char alphanumeric id, got $groupId")

        assertEquals(1, store.committedWrites.size)
        val batch = store.committedWrites[0]
        assertEquals(2, batch.size)

        val groupWrite = batch[0]
        assertIs<FirestoreWrite.Set>(groupWrite)
        assertEquals("groups/$groupId", groupWrite.path)
        assertTrue(groupWrite.mustNotExist)
        assertEquals("u1", groupWrite.fields[GroupFields.OWNER_UID])
        assertEquals("Family", groupWrite.fields[GroupFields.NAME])
        assertIs<Instant>(groupWrite.fields[GroupFields.CREATED_AT])

        val memberWrite = batch[1]
        assertIs<FirestoreWrite.Set>(memberWrite)
        assertEquals("groups/$groupId/members/o@x.com", memberWrite.path)
        assertTrue(memberWrite.mustNotExist)
        assertEquals("o@x.com", memberWrite.fields[GroupFields.EMAIL])
        assertEquals("owner", memberWrite.fields[GroupFields.ROLE])
        assertEquals("Owner", memberWrite.fields[GroupFields.NAME])
        assertIs<Instant>(memberWrite.fields[GroupFields.ADDED_AT])
    }

    @Test
    fun createGroupRequiresVerifiedEmail() = runTest {
        val store = FakeDocumentStore()
        val user = User(id = "u1", email = "o@x.com", displayName = "Owner", emailVerified = false)
        val session = FakeSessionProvider(user)
        val repo = DocumentStoreGroupRepository(store, session)

        val result = repo.createGroup("Family")
        assertTrue(result.isFailure)
        assertIs<GroupError.EmailNotVerified>(result.exceptionOrNull())
        assertEquals(0, store.committedWrites.size)
    }

    @Test
    fun createGroupRejectsBlankName() = runTest {
        val store = FakeDocumentStore()
        val user = User(id = "u1", email = "o@x.com", displayName = "Owner", emailVerified = true)
        val session = FakeSessionProvider(user)
        val repo = DocumentStoreGroupRepository(store, session)

        val result = repo.createGroup("   ")
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(0, store.committedWrites.size)
    }

    @Test
    fun membershipsQueryCollectionGroupByLowercaseEmail() = runTest {
        val store = FakeDocumentStore()
        val user = User(id = "u1", email = "O@X.com", displayName = "Owner", emailVerified = true)
        val session = FakeSessionProvider(user)
        val repo = DocumentStoreGroupRepository(store, session)

        store.docs["groups/g1"] = FirestoreDoc(
            path = "groups/g1",
            id = "g1",
            fields = mapOf(
                GroupFields.NAME to "Family",
                GroupFields.OWNER_UID to "u1",
                GroupFields.CREATED_AT to Instant.ofEpochMilli(500L)
            )
        )

        val flow = repo.observeMyMemberships()
        assertEquals(1, store.queriedQueries.size)
        val query = store.queriedQueries[0]
        assertEquals("", query.parentPath)
        assertEquals("members", query.collectionId)
        assertTrue(query.allDescendants)
        assertEquals(listOf(GroupFields.EMAIL to "o@x.com"), query.whereEquals)

        val queryFlow = store.queryFlows[query]!!
        queryFlow.emit(
            listOf(
                FirestoreDoc(
                    path = "projects/p/databases/(default)/documents/groups/g1/members/o@x.com",
                    id = "o@x.com",
                    fields = mapOf(
                        GroupFields.EMAIL to "o@x.com",
                        GroupFields.ROLE to "owner",
                        GroupFields.ADDED_AT to Instant.ofEpochMilli(1000L)
                    )
                )
            )
        )

        val memberships = flow.first()
        assertEquals(1, memberships.size)
        val m = memberships[0]
        assertEquals(Group("g1", "Family", "u1", 500L), m.group)
        assertEquals(GroupRole.OWNER, m.myRole)
    }

    @Test
    fun membershipSkipsUnreadableGroup() = runTest {
        val store = FakeDocumentStore()
        val user = User(id = "u1", email = "o@x.com", displayName = "Owner", emailVerified = true)
        val session = FakeSessionProvider(user)
        val repo = DocumentStoreGroupRepository(store, session)

        store.onGet = { path ->
            if (path == "groups/g_error") throw IllegalStateException("Cannot read group")
            null
        }

        val flow = repo.observeMyMemberships()
        val query = store.queriedQueries[0]
        val queryFlow = store.queryFlows[query]!!
        queryFlow.emit(
            listOf(
                FirestoreDoc(
                    path = "projects/p/databases/(default)/documents/groups/g_error/members/o@x.com",
                    id = "o@x.com",
                    fields = mapOf(
                        GroupFields.EMAIL to "o@x.com",
                        GroupFields.ROLE to "owner",
                        GroupFields.ADDED_AT to Instant.ofEpochMilli(1000L)
                    )
                ),
                FirestoreDoc(
                    path = "projects/p/databases/(default)/documents/groups/g_missing/members/o@x.com",
                    id = "o@x.com",
                    fields = mapOf(
                        GroupFields.EMAIL to "o@x.com",
                        GroupFields.ROLE to "owner",
                        GroupFields.ADDED_AT to Instant.ofEpochMilli(1000L)
                    )
                )
            )
        )

        val memberships = flow.first()
        assertEquals(emptyList(), memberships)
    }

    @Test
    fun addMemberUsesLowercaseIdAndMustNotExist() = runTest {
        val store = FakeDocumentStore()
        val repo = DocumentStoreGroupMemberRepository(store)

        val result = repo.addMember("g1", " Kid@Example.COM ", GroupRole.DEVICE, "Kid")
        assertTrue(result.isSuccess)

        assertEquals(1, store.committedWrites.size)
        val batch = store.committedWrites[0]
        assertEquals(1, batch.size)
        val write = batch[0]
        assertIs<FirestoreWrite.Set>(write)
        assertEquals("groups/g1/members/kid@example.com", write.path)
        assertTrue(write.mustNotExist)
        assertEquals("kid@example.com", write.fields[GroupFields.EMAIL])
        assertEquals("device", write.fields[GroupFields.ROLE])
        assertEquals("Kid", write.fields[GroupFields.NAME])
        assertIs<Instant>(write.fields[GroupFields.ADDED_AT])
    }

    @Test
    fun addExistingMemberFailsAlreadyMember() = runTest {
        val store = FakeDocumentStore()
        store.onCommit = {
            throw FirestoreAlreadyExistsException("groups/g1/members/kid@x.com")
        }
        val repo = DocumentStoreGroupMemberRepository(store)

        val result = repo.addMember("g1", "kid@x.com", GroupRole.SUPERVISOR, null)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<GroupError.AlreadyMember>(error)
        assertEquals("kid@x.com is already in this group", error.message)
    }

    @Test
    fun changeRoleUpdatesOnlyRole() = runTest {
        val store = FakeDocumentStore()
        val repo = DocumentStoreGroupMemberRepository(store)

        val result = repo.changeRole("g1", "kid@x.com", GroupRole.SUPERVISOR)
        assertTrue(result.isSuccess)

        assertEquals(1, store.committedWrites.size)
        val batch = store.committedWrites[0]
        assertEquals(1, batch.size)
        val write = batch[0]
        assertIs<FirestoreWrite.Update>(write)
        assertEquals("groups/g1/members/kid@x.com", write.path)
        assertEquals(mapOf(GroupFields.ROLE to "supervisor"), write.fields)
    }

    @Test
    fun observeDevicesQueriesEachGroup() = runTest {
        val store = FakeDocumentStore()
        val repo = DocumentStoreGroupDeviceRepository(store)

        val flow = repo.observeDevices(listOf("g1", "g2"))
        assertEquals(2, store.queriedQueries.size)

        val q1 = store.queriedQueries.first { it.whereEquals == listOf(GroupFields.GROUP_ID to "g1") }
        val q2 = store.queriedQueries.first { it.whereEquals == listOf(GroupFields.GROUP_ID to "g2") }

        store.queryFlows[q1]!!.emit(
            listOf(
                FirestoreDoc(
                    path = "devices/d2",
                    id = "d2",
                    fields = mapOf(
                        GroupFields.DISPLAY_NAME to "Zeta",
                        GroupFields.MODEL to "Pixel 7",
                        GroupFields.GROUP_ID to "g1"
                    )
                )
            )
        )
        store.queryFlows[q2]!!.emit(
            listOf(
                FirestoreDoc(
                    path = "devices/d1",
                    id = "d1",
                    fields = mapOf(
                        GroupFields.DISPLAY_NAME to "Alpha",
                        GroupFields.MODEL to "Pixel 8",
                        GroupFields.GROUP_ID to "g2"
                    )
                )
            )
        )

        val devices = flow.first()
        assertEquals(2, devices.size)
        assertEquals("Alpha", devices[0].displayName)
        assertEquals("Zeta", devices[1].displayName)
    }

    @Test
    fun observeDevicesEmptyIdsNoQuery() = runTest {
        val store = FakeDocumentStore()
        val repo = DocumentStoreGroupDeviceRepository(store)

        val flow = repo.observeDevices(emptyList())
        val devices = flow.first()
        assertEquals(emptyList(), devices)
        assertEquals(0, store.queriedQueries.size)
    }

    @Test
    fun removeFromGroupUpdatesOnlyGroupId() = runTest {
        val store = FakeDocumentStore()
        val repo = DocumentStoreGroupDeviceRepository(store)

        val result = repo.removeFromGroup("d1")
        assertTrue(result.isSuccess)

        assertEquals(1, store.committedWrites.size)
        val batch = store.committedWrites[0]
        assertEquals(1, batch.size)
        val write = batch[0]
        assertIs<FirestoreWrite.Update>(write)
        assertEquals("devices/d1", write.path)
        assertEquals(mapOf(GroupFields.GROUP_ID to null), write.fields)
    }

    @Test
    fun permissionDeniedMapsToAccessLost() = runTest {
        val store = FakeDocumentStore()
        store.customListenQuery = {
            flow {
                throw FirestorePermissionDeniedException("devices")
            }
        }
        val repo = DocumentStoreGroupDeviceRepository(store)

        val error = assertFailsWith<GroupError.AccessLost> {
            repo.observeDevices(listOf("g1")).first()
        }
        assertEquals("You no longer have access", error.message)
    }
}
