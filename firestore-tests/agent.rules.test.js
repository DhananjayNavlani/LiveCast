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
  query,
  where,
  orderBy,
  limit,
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
  O2: { uid: 'O2', email: 'o2@x.com', email_verified: true, firebase: { sign_in_provider: 'password' } },
  S2: { uid: 'S2', email: 's2@x.com', email_verified: true, firebase: { sign_in_provider: 'password' } },
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
      // devices/H
      await setDoc(doc(db, 'devices', 'H'), {
        groupId: 'g1',
        hostUid: 'H',
        displayName: 'Pixel 8',
        model: 'Pixel 8',
        enrolledAt: now,
        accessibility: 'on',
        notifications: 'on',
        statusAt: now,
      });
    });
  }
});

test('deviceCreateWithoutPresenceAllowed', async () => {
  const h2Db = getDb('H2');
  const now = Timestamp.now();
  await assertSucceeds(
    setDoc(doc(h2Db, 'devices', 'H2'), {
      groupId: 'g1',
      hostUid: 'H2',
      displayName: 'Pixel 7',
      model: 'Pixel 7',
      enrolledAt: now,
    })
  );
});

test('deviceCreateWithOnlineFieldDenied', async () => {
  const h2Db = getDb('H2');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(h2Db, 'devices', 'H2'), {
      groupId: 'g1',
      hostUid: 'H2',
      displayName: 'Pixel 7',
      model: 'Pixel 7',
      enrolledAt: now,
      online: true,
    })
  );
});

test('selfReportStatusAllowed', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertSucceeds(
    updateDoc(doc(hDb, 'devices', 'H'), {
      accessibility: 'off',
      notifications: 'on',
      statusAt: now,
    })
  );
});

test('selfReportBadToggleDenied', async () => {
  const hDb = getDb('H');
  await assertFails(
    updateDoc(doc(hDb, 'devices', 'H'), {
      accessibility: 'maybe',
    })
  );
});

test('selfReportGroupChangeDenied', async () => {
  const hDb = getDb('H');
  await assertFails(
    updateDoc(doc(hDb, 'devices', 'H'), {
      groupId: 'g2',
    })
  );
});

test('supervisorCannotSelfReport', async () => {
  const sDb = getDb('S');
  const now = Timestamp.now();
  await assertFails(
    updateDoc(doc(sDb, 'devices', 'H'), {
      accessibility: 'off',
      notifications: 'on',
      statusAt: now,
    })
  );
});

test('checkReplyAllowed', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertSucceeds(
    updateDoc(doc(hDb, 'devices', 'H'), {
      lastCheckId: 'chk-1',
      lastCheckAt: now,
      accessibility: 'on',
      notifications: 'on',
      statusAt: now,
    })
  );
});

test('pushTokenOwnReadWriteAllowed', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertSucceeds(
    setDoc(doc(hDb, 'devices', 'H', 'private', 'push'), {
      fcmToken: 'fcm-123',
      updatedAt: now,
    })
  );
  await assertSucceeds(getDoc(doc(hDb, 'devices', 'H', 'private', 'push')));
});

test('pushTokenSupervisorReadDenied', async () => {
  const now = Timestamp.now();
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H', 'private', 'push'), {
      fcmToken: 'fcm-123',
      updatedAt: now,
    });
  });
  const sDb = getDb('S');
  await assertFails(getDoc(doc(sDb, 'devices', 'H', 'private', 'push')));
});

test('pushTokenOtherUidWriteDenied', async () => {
  const now = Timestamp.now();
  const sDb = getDb('S');
  await assertFails(
    setDoc(doc(sDb, 'devices', 'H', 'private', 'push'), {
      fcmToken: 'fcm-evil',
      updatedAt: now,
    })
  );
  const xDb = getDb('X');
  await assertFails(
    setDoc(doc(xDb, 'devices', 'H', 'private', 'push'), {
      fcmToken: 'fcm-evil',
      updatedAt: now,
    })
  );
});

test('appEventCreateAllowed', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertSucceeds(
    setDoc(doc(collection(hDb, 'devices', 'H', 'appEvents')), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: now,
      detectedLate: false,
      groupId: 'g1',
    })
  );
});

test('appEventWrongGroupDenied', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(collection(hDb, 'devices', 'H', 'appEvents')), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: now,
      detectedLate: false,
      groupId: 'g2',
    })
  );
});

test('appEventUnenrolledDenied', async () => {
  await seed(async (db) => {
    await updateDoc(doc(db, 'devices', 'H'), { groupId: null });
  });
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(collection(hDb, 'devices', 'H', 'appEvents')), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: now,
      detectedLate: false,
      groupId: 'g1',
    })
  );
});

test('appEventFutureAtDenied', async () => {
  const hDb = getDb('H');
  const futureAt = Timestamp.fromMillis(Date.now() + 10 * 60 * 1000);
  await assertFails(
    setDoc(doc(collection(hDb, 'devices', 'H', 'appEvents')), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: futureAt,
      detectedLate: false,
      groupId: 'g1',
    })
  );
});

test('appEventExtraKeyDenied', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  await assertFails(
    setDoc(doc(collection(hDb, 'devices', 'H', 'appEvents')), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: now,
      detectedLate: false,
      groupId: 'g1',
      extra: 'bad',
    })
  );
});

test('appEventSupervisorListAllowed', async () => {
  const now = Timestamp.now();
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H', 'appEvents', 'e1'), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: now,
      detectedLate: false,
      groupId: 'g1',
    });
  });
  const sDb = getDb('S');
  await assertSucceeds(
    getDocs(
      query(
        collection(sDb, 'devices', 'H', 'appEvents'),
        where('groupId', '==', 'g1'),
        orderBy('at', 'desc'),
        limit(50)
      )
    )
  );
});

test('appEventDeviceRoleReadDenied', async () => {
  const now = Timestamp.now();
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H', 'appEvents', 'e1'), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: now,
      detectedLate: false,
      groupId: 'g1',
    });
  });
  const hDb = getDb('H');
  await assertFails(getDoc(doc(hDb, 'devices', 'H', 'appEvents', 'e1')));
  await assertFails(
    getDocs(
      query(
        collection(hDb, 'devices', 'H', 'appEvents'),
        where('groupId', '==', 'g1'),
        orderBy('at', 'desc'),
        limit(50)
      )
    )
  );
});

test('appEventOldGroupReadDenied', async () => {
  const now = Timestamp.now();
  await seed(async (db) => {
    // Group g2 setup
    await setDoc(doc(db, 'groups', 'g2'), {
      name: 'Group 2',
      ownerUid: 'O2',
      createdAt: now,
    });
    await setDoc(doc(db, 'groups', 'g2', 'members', 's2@x.com'), {
      email: 's2@x.com',
      role: 'supervisor',
      name: 'Supervisor 2',
      addedAt: now,
    });
    // Event written under g1
    await setDoc(doc(db, 'devices', 'H', 'appEvents', 'e1'), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: now,
      detectedLate: false,
      groupId: 'g1',
    });
    // Device H re-enrolled into g2
    await updateDoc(doc(db, 'devices', 'H'), {
      groupId: 'g2',
    });
  });
  const s2Db = getDb('S2');
  await assertFails(getDoc(doc(s2Db, 'devices', 'H', 'appEvents', 'e1')));
});

test('appEventUpdateDenied', async () => {
  const now = Timestamp.now();
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H', 'appEvents', 'e1'), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: now,
      detectedLate: false,
      groupId: 'g1',
    });
  });
  const hDb = getDb('H');
  await assertFails(updateDoc(doc(hDb, 'devices', 'H', 'appEvents', 'e1'), { label: 'Updated' }));
  const sDb = getDb('S');
  await assertFails(updateDoc(doc(sDb, 'devices', 'H', 'appEvents', 'e1'), { label: 'Updated' }));
});

test('appEventDeleteDenied', async () => {
  const now = Timestamp.now();
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H', 'appEvents', 'e1'), {
      type: 'installed',
      packageName: 'com.example',
      label: 'Example',
      at: now,
      detectedLate: false,
      groupId: 'g1',
    });
  });
  const hDb = getDb('H');
  await assertFails(deleteDoc(doc(hDb, 'devices', 'H', 'appEvents', 'e1')));
  const sDb = getDb('S');
  await assertFails(deleteDoc(doc(sDb, 'devices', 'H', 'appEvents', 'e1')));
});
