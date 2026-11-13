import { test, before, after, beforeEach } from 'node:test';
import fs from 'node:fs';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  collectionGroup,
  query,
  where,
  writeBatch,
  Timestamp,
} from 'firebase/firestore';

let testEnv;

before(async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST
    ? process.env.FIRESTORE_EMULATOR_HOST.split(':')[0]
    : '127.0.0.1';
  const port = process.env.FIRESTORE_EMULATOR_HOST
    ? Number(process.env.FIRESTORE_EMULATOR_HOST.split(':')[1])
    : 8080;

  testEnv = await initializeTestEnvironment({
    projectId: 'demo-livecast',
    firestore: {
      host,
      port,
      rules: fs.readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

async function seed(fn) {
  let result;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    result = await fn(context.firestore());
  });
  return result;
}

const USERS = {
  O: { uid: 'O', email: 'o@x.com', email_verified: true, firebase: { sign_in_provider: 'password' } },
  S: { uid: 'S', email: 's@x.com', email_verified: true, firebase: { sign_in_provider: 'password' } },
  H: { uid: 'H', email: 'h@x.com', email_verified: true, firebase: { sign_in_provider: 'password' } },
  H2: { uid: 'H2', email: 'h2@x.com', email_verified: true, firebase: { sign_in_provider: 'password' } },
  X: { uid: 'X', email: 'x@x.com', email_verified: true, firebase: { sign_in_provider: 'password' } },
  U: { uid: 'U', email: 'u@x.com', email_verified: false, firebase: { sign_in_provider: 'password' } },
  G: { uid: 'G', email: null, email_verified: false, firebase: { sign_in_provider: 'anonymous' } },
};

function getDb(userKey) {
  const user = USERS[userKey];
  if (!user) throw new Error(`Unknown test user ${userKey}`);
  const claims = {
    firebase: user.firebase,
  };
  if (user.email !== null) {
    claims.email = user.email;
    claims.email_verified = user.email_verified;
  }
  return testEnv.authenticatedContext(user.uid, claims).firestore();
}

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
    await seed(async (db) => {
      const now = Timestamp.now();
      // groups/g1
      await setDoc(doc(db, 'groups', 'g1'), {
        name: 'Group 1',
        ownerUid: 'O',
        createdAt: now,
      });
      // groups/g1/members/o@x.com
      await setDoc(doc(db, 'groups', 'g1', 'members', 'o@x.com'), {
        email: 'o@x.com',
        role: 'owner',
        name: 'Owner',
        addedAt: now,
      });
      // groups/g1/members/s@x.com
      await setDoc(doc(db, 'groups', 'g1', 'members', 's@x.com'), {
        email: 's@x.com',
        role: 'supervisor',
        name: 'Supervisor',
        addedAt: now,
      });
      // groups/g1/members/h@x.com
      await setDoc(doc(db, 'groups', 'g1', 'members', 'h@x.com'), {
        email: 'h@x.com',
        role: 'device',
        name: 'Host 1',
        addedAt: now,
      });
      // groups/g1/members/h2@x.com
      await setDoc(doc(db, 'groups', 'g1', 'members', 'h2@x.com'), {
        email: 'h2@x.com',
        role: 'device',
        name: 'Host 2',
        addedAt: now,
      });
      // groups/g1/members/u@x.com
      await setDoc(doc(db, 'groups', 'g1', 'members', 'u@x.com'), {
        email: 'u@x.com',
        role: 'supervisor',
        name: 'Unverified Supervisor',
        addedAt: now,
      });
      // devices/H
      await setDoc(doc(db, 'devices', 'H'), {
        groupId: 'g1',
        hostUid: 'H',
        displayName: 'Pixel 8',
        model: 'Pixel 8',
        online: true,
        lastSeen: now,
        enrolledAt: now,
      });
    });
  }
});

// Groups tests
test('verifiedUserCreatesGroupWithOwnerMember', async () => {
  const xDb = getDb('X');
  const batch = writeBatch(xDb);
  const now = Timestamp.now();
  batch.set(doc(xDb, 'groups', 'g2'), {
    name: 'Group 2',
    ownerUid: 'X',
    createdAt: now,
  });
  batch.set(doc(xDb, 'groups', 'g2', 'members', 'x@x.com'), {
    email: 'x@x.com',
    role: 'owner',
    name: 'User X',
    addedAt: now,
  });
  await assertSucceeds(batch.commit());
});

test('createGroupWithoutOwnerMember', async () => {
  const xDb = getDb('X');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(xDb, 'groups', 'g2'), {
      name: 'Group 2',
      ownerUid: 'X',
      createdAt: now,
    })
  );
});

test('createGroupWithOtherOwnerUid', async () => {
  const xDb = getDb('X');
  const batch = writeBatch(xDb);
  const now = Timestamp.now();
  batch.set(doc(xDb, 'groups', 'g2'), {
    name: 'Group 2',
    ownerUid: 'O',
    createdAt: now,
  });
  batch.set(doc(xDb, 'groups', 'g2', 'members', 'x@x.com'), {
    email: 'x@x.com',
    role: 'owner',
    name: 'User X',
    addedAt: now,
  });
  await assertFails(batch.commit());
});

test('unverifiedCreatesGroup', async () => {
  const uDb = getDb('U');
  const batch = writeBatch(uDb);
  const now = Timestamp.now();
  batch.set(doc(uDb, 'groups', 'g2'), {
    name: 'Group 2',
    ownerUid: 'U',
    createdAt: now,
  });
  batch.set(doc(uDb, 'groups', 'g2', 'members', 'u@x.com'), {
    email: 'u@x.com',
    role: 'owner',
    name: 'User U',
    addedAt: now,
  });
  await assertFails(batch.commit());
});

test('guestCreatesGroup', async () => {
  const gDb = getDb('G');
  const batch = writeBatch(gDb);
  const now = Timestamp.now();
  batch.set(doc(gDb, 'groups', 'g2'), {
    name: 'Group 2',
    ownerUid: 'G',
    createdAt: now,
  });
  batch.set(doc(gDb, 'groups', 'g2', 'members', 'g@x.com'), {
    email: 'g@x.com',
    role: 'owner',
    name: 'Guest',
    addedAt: now,
  });
  await assertFails(batch.commit());
});

test('strangerSelfAddsAsOwnerOfExistingGroup', async () => {
  const xDb = getDb('X');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(xDb, 'groups', 'g1', 'members', 'x@x.com'), {
      email: 'x@x.com',
      role: 'owner',
      name: 'User X',
      addedAt: now,
    })
  );
});

test('memberReadsGroup', async () => {
  const sDb = getDb('S');
  await assertSucceeds(getDoc(doc(sDb, 'groups', 'g1')));
});

test('strangerReadsGroup', async () => {
  const xDb = getDb('X');
  await assertFails(getDoc(doc(xDb, 'groups', 'g1')));
});

test('ownerRenamesGroup', async () => {
  const oDb = getDb('O');
  await assertSucceeds(
    updateDoc(doc(oDb, 'groups', 'g1'), {
      name: 'Renamed Group',
    })
  );
});

test('ownerChangesOwnerUid', async () => {
  const oDb = getDb('O');
  await assertFails(
    updateDoc(doc(oDb, 'groups', 'g1'), {
      ownerUid: 'S',
    })
  );
});

test('supervisorUpdatesGroup', async () => {
  const sDb = getDb('S');
  await assertFails(
    updateDoc(doc(sDb, 'groups', 'g1'), {
      name: 'Supervisor Renamed',
    })
  );
});

test('ownerDeletesGroup', async () => {
  const oDb = getDb('O');
  await assertFails(deleteDoc(doc(oDb, 'groups', 'g1')));
});

// Members tests
test('ownerAddsMember', async () => {
  const oDb = getDb('O');
  const now = Timestamp.now();
  await assertSucceeds(
    setDoc(doc(oDb, 'groups', 'g1', 'members', 'new@x.com'), {
      email: 'new@x.com',
      role: 'supervisor',
      name: 'New Member',
      addedAt: now,
    })
  );
});

test('supervisorAddsMember', async () => {
  const sDb = getDb('S');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(sDb, 'groups', 'g1', 'members', 'new@x.com'), {
      email: 'new@x.com',
      role: 'device',
      name: 'New Member',
      addedAt: now,
    })
  );
});

test('memberDocIdMustMatchEmail', async () => {
  const oDb = getDb('O');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(oDb, 'groups', 'g1', 'members', 'a@x.com'), {
      email: 'b@x.com',
      role: 'device',
      name: 'Mismatch',
      addedAt: now,
    })
  );
});

test('upperCaseMemberIdDenied', async () => {
  const oDb = getDb('O');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(oDb, 'groups', 'g1', 'members', 'Kid@x.com'), {
      email: 'Kid@x.com',
      role: 'device',
      name: 'Uppercase',
      addedAt: now,
    })
  );
});

test('invalidRoleDenied', async () => {
  const oDb = getDb('O');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(oDb, 'groups', 'g1', 'members', 'admin@x.com'), {
      email: 'admin@x.com',
      role: 'admin',
      name: 'Admin',
      addedAt: now,
    })
  );
});

test('ownerChangesSupervisorRole', async () => {
  const oDb = getDb('O');
  await assertSucceeds(
    updateDoc(doc(oDb, 'groups', 'g1', 'members', 's@x.com'), {
      role: 'owner',
    })
  );
});

test('ownerRemovesSupervisor', async () => {
  const oDb = getDb('O');
  await assertSucceeds(deleteDoc(doc(oDb, 'groups', 'g1', 'members', 's@x.com')));
});

test('ownerRemovesSelf', async () => {
  const oDb = getDb('O');
  await assertFails(deleteDoc(doc(oDb, 'groups', 'g1', 'members', 'o@x.com')));
});

test('ownerDemotesSelf', async () => {
  const oDb = getDb('O');
  await assertFails(
    updateDoc(doc(oDb, 'groups', 'g1', 'members', 'o@x.com'), {
      role: 'supervisor',
    })
  );
});

test('memberListsGroupMembers', async () => {
  const sDb = getDb('S');
  await assertSucceeds(getDocs(collection(sDb, 'groups', 'g1', 'members')));
});

test('strangerListsMembers', async () => {
  const xDb = getDb('X');
  await assertFails(getDocs(collection(xDb, 'groups', 'g1', 'members')));
});

test('unverifiedListsMembers', async () => {
  const uDb = getDb('U');
  await assertFails(getDocs(collection(uDb, 'groups', 'g1', 'members')));
});

test('enrolledHostReadsMembersAfterDeviceMembershipRemoved', async () => {
  await seed(async (db) => {
    await deleteDoc(doc(db, 'groups', 'g1', 'members', 'h@x.com'));
  });
  const hDb = getDb('H');
  await assertSucceeds(getDocs(collection(hDb, 'groups', 'g1', 'members')));
});

// Collection group tests
test('collectionGroupSelfLookup', async () => {
  const hDb = getDb('H');
  await assertSucceeds(
    getDocs(query(collectionGroup(hDb, 'members'), where('email', '==', 'h@x.com')))
  );
});

test('collectionGroupOtherEmail', async () => {
  const hDb = getDb('H');
  await assertFails(
    getDocs(query(collectionGroup(hDb, 'members'), where('email', '==', 's@x.com')))
  );
});

test('collectionGroupUnverified', async () => {
  const uDb = getDb('U');
  await assertFails(
    getDocs(query(collectionGroup(uDb, 'members'), where('email', '==', 'u@x.com')))
  );
});

test('mixedCaseTokenEmailMatches', async () => {
  const mixedDb = testEnv.authenticatedContext('H_MIXED', {
    email: 'H@X.com',
    email_verified: true,
    firebase: { sign_in_provider: 'password' },
  }).firestore();
  await assertSucceeds(
    getDocs(query(collectionGroup(mixedDb, 'members'), where('email', '==', 'h@x.com')))
  );
  await assertSucceeds(
    getDoc(doc(mixedDb, 'groups', 'g1', 'members', 'h@x.com'))
  );
});

// Devices tests
test('deviceMemberEnrolls', async () => {
  const h2Db = getDb('H2');
  const now = Timestamp.now();
  await assertSucceeds(
    setDoc(doc(h2Db, 'devices', 'H2'), {
      groupId: 'g1',
      hostUid: 'H2',
      displayName: 'Pixel 7',
      model: 'Pixel 7',
      online: true,
      lastSeen: now,
      enrolledAt: now,
    })
  );
});

test('enrollWithoutDeviceMembership', async () => {
  const xDb = getDb('X');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(xDb, 'devices', 'X'), {
      groupId: 'g1',
      hostUid: 'X',
      displayName: 'X Phone',
      model: 'Pixel X',
      online: true,
      lastSeen: now,
      enrolledAt: now,
    })
  );
});

test('supervisorEnrollsAsDevice', async () => {
  const sDb = getDb('S');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(sDb, 'devices', 'S'), {
      groupId: 'g1',
      hostUid: 'S',
      displayName: 'S Phone',
      model: 'Pixel S',
      online: true,
      lastSeen: now,
      enrolledAt: now,
    })
  );
});

test('enrollOtherUidDoc', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(hDb, 'devices', 'H2'), {
      groupId: 'g1',
      hostUid: 'H2',
      displayName: 'H2 Phone',
      model: 'Pixel H2',
      online: true,
      lastSeen: now,
      enrolledAt: now,
    })
  );
});

test('unverifiedDeviceEnrolls', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'groups', 'g1', 'members', 'u2@x.com'), {
      email: 'u2@x.com',
      role: 'device',
      name: 'Unverified Host',
      addedAt: Timestamp.now(),
    });
  });
  const u2Db = testEnv.authenticatedContext('U2', {
    email: 'u2@x.com',
    email_verified: false,
    firebase: { sign_in_provider: 'password' },
  }).firestore();
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(u2Db, 'devices', 'U2'), {
      groupId: 'g1',
      hostUid: 'U2',
      displayName: 'U2 Phone',
      model: 'Pixel U2',
      online: true,
      lastSeen: now,
      enrolledAt: now,
    })
  );
});

test('enrollWithExtraField', async () => {
  const h2Db = getDb('H2');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(h2Db, 'devices', 'H2'), {
      groupId: 'g1',
      hostUid: 'H2',
      displayName: 'Pixel 7',
      model: 'Pixel 7',
      online: true,
      lastSeen: now,
      enrolledAt: now,
      ownerUid: 'O',
    })
  );
});

test('legacyDeviceDocEnrolls', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H2'), {
      hostUid: 'H2',
      displayName: 'Pixel 7',
      model: 'Pixel 7',
      createdAt: Timestamp.now(),
      lastSeenEpochMs: Date.now(),
    });
  });
  const h2Db = getDb('H2');
  const now = Timestamp.now();
  await assertSucceeds(
    setDoc(
      doc(h2Db, 'devices', 'H2'),
      {
        groupId: 'g1',
        hostUid: 'H2',
        displayName: 'Pixel 7',
        model: 'Pixel 7',
        online: true,
        lastSeen: now,
        enrolledAt: now,
      },
      { merge: true }
    )
  );
});

test('hostHeartbeat', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertSucceeds(
    updateDoc(doc(hDb, 'devices', 'H'), {
      online: true,
      lastSeen: now,
    })
  );
});

test('hostChangesGroupId', async () => {
  const hDb = getDb('H');
  await assertFails(
    updateDoc(doc(hDb, 'devices', 'H'), {
      groupId: 'g2',
    })
  );
});

test('hostClearsGroupId', async () => {
  const hDb = getDb('H');
  await assertFails(
    updateDoc(doc(hDb, 'devices', 'H'), {
      groupId: null,
    })
  );
});

test('hostUpdatesHostUid', async () => {
  const hDb = getDb('H');
  await assertFails(
    updateDoc(doc(hDb, 'devices', 'H'), {
      hostUid: 'OTHER',
    })
  );
});

test('hostHeartbeatAfterRemoval', async () => {
  await seed(async (db) => {
    await updateDoc(doc(db, 'devices', 'H'), { groupId: null });
  });
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertSucceeds(
    updateDoc(doc(hDb, 'devices', 'H'), {
      online: true,
      lastSeen: now,
    })
  );
  // Verify groupId is still null
  const snapshot = await seed(async (db) => {
    return await getDoc(doc(db, 'devices', 'H'));
  });
  if (snapshot.data().groupId !== null) {
    throw new Error('Expected groupId to still be null');
  }
});

test('hostReenrollsAfterRemoval', async () => {
  await seed(async (db) => {
    await updateDoc(doc(db, 'devices', 'H'), { groupId: null });
  });
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertSucceeds(
    updateDoc(doc(hDb, 'devices', 'H'), {
      groupId: 'g1',
      hostUid: 'H',
      displayName: 'Pixel 8',
      model: 'Pixel 8',
      online: true,
      lastSeen: now,
      enrolledAt: now,
    })
  );
});

test('supervisorGetsGroupDevice', async () => {
  const sDb = getDb('S');
  await assertSucceeds(getDoc(doc(sDb, 'devices', 'H')));
});

test('supervisorQueriesGroupDevices', async () => {
  const sDb = getDb('S');
  await assertSucceeds(
    getDocs(query(collection(sDb, 'devices'), where('groupId', '==', 'g1')))
  );
});

test('ownerQueriesGroupDevices', async () => {
  const oDb = getDb('O');
  await assertSucceeds(
    getDocs(query(collection(oDb, 'devices'), where('groupId', '==', 'g1')))
  );
});

test('strangerGetsDevice', async () => {
  const xDb = getDb('X');
  await assertFails(getDoc(doc(xDb, 'devices', 'H')));
});

test('strangerQueriesGroupDevices', async () => {
  const xDb = getDb('X');
  await assertFails(
    getDocs(query(collection(xDb, 'devices'), where('groupId', '==', 'g1')))
  );
});

test('deviceMemberReadsOtherDevice', async () => {
  const h2Db = getDb('H2');
  await assertFails(getDoc(doc(h2Db, 'devices', 'H')));
});

test('hostReadsOwnDevice', async () => {
  const hDb = getDb('H');
  await assertSucceeds(getDoc(doc(hDb, 'devices', 'H')));
});

test('unverifiedSupervisorQueriesDevices', async () => {
  const uDb = getDb('U');
  await assertFails(
    getDocs(query(collection(uDb, 'devices'), where('groupId', '==', 'g1')))
  );
});

test('removedSupervisorLosesDeviceAccess', async () => {
  await seed(async (db) => {
    await deleteDoc(doc(db, 'groups', 'g1', 'members', 's@x.com'));
  });
  const sDb = getDb('S');
  await assertFails(getDoc(doc(sDb, 'devices', 'H')));
});

test('ownerRemovesDevice', async () => {
  const oDb = getDb('O');
  await assertSucceeds(
    updateDoc(doc(oDb, 'devices', 'H'), {
      groupId: null,
    })
  );
});

test('ownerRemovesAndRenames', async () => {
  const oDb = getDb('O');
  await assertFails(
    updateDoc(doc(oDb, 'devices', 'H'), {
      groupId: null,
      displayName: 'Renamed',
    })
  );
});

test('supervisorRemovesDevice', async () => {
  const sDb = getDb('S');
  await assertFails(
    updateDoc(doc(sDb, 'devices', 'H'), {
      groupId: null,
    })
  );
});

test('anyoneDeletesDevice', async () => {
  const oDb = getDb('O');
  const hDb = getDb('H');
  await assertFails(deleteDoc(doc(oDb, 'devices', 'H')));
  await assertFails(deleteDoc(doc(hDb, 'devices', 'H')));
});

// Legacy tests
test('pairingCodesDenied', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(hDb, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: now,
      expiresAt: now,
    })
  );
});
