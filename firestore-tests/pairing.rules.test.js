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

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function getDb(uid, provider = 'password') {
  return testEnv.authenticatedContext(uid, {
    firebase: { sign_in_provider: provider },
  }).firestore();
}

async function seed(fn) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await fn(context.firestore());
  });
}

test('hostCreatesOwnCode', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  const expiresAt = Timestamp.fromMillis(now.toMillis() + 10 * 60 * 1000);
  await assertSucceeds(
    setDoc(doc(hDb, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: now,
      expiresAt: expiresAt,
    })
  );
});

test('hostCreatesCodeForOtherDevice', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  const expiresAt = Timestamp.fromMillis(now.toMillis() + 10 * 60 * 1000);
  await assertFails(
    setDoc(doc(hDb, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'OTHER_DEVICE',
      hostUid: 'H',
      createdAt: now,
      expiresAt: expiresAt,
    })
  );
});

test('codeTtlOver11MinDenied', async () => {
  const hDb = getDb('H');
  const now = Timestamp.now();
  const expiresAt = Timestamp.fromMillis(now.toMillis() + 12 * 60 * 1000);
  await assertFails(
    setDoc(doc(hDb, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: now,
      expiresAt: expiresAt,
    })
  );
});

test('parentGetsCodeByExactId', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 10 * 60 * 1000),
    });
  });
  const pDb = getDb('P');
  await assertSucceeds(getDoc(doc(pDb, 'pairingCodes', '123456')));
});

test('parentListsCodes', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 10 * 60 * 1000),
    });
  });
  const pDb = getDb('P');
  await assertFails(getDocs(collection(pDb, 'pairingCodes')));
});

test('parentCreatesPendingRequest', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 10 * 60 * 1000),
    });
  });
  const pDb = getDb('P');
  await assertSucceeds(
    setDoc(doc(pDb, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'H',
      clientUid: 'P',
      clientName: 'Parent Name',
      clientEmail: 'parent@example.com',
      status: 'pending',
      createdAt: Timestamp.now(),
    })
  );
});

test('requestWithWrongDeviceId', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 10 * 60 * 1000),
    });
  });
  const pDb = getDb('P');
  await assertFails(
    setDoc(doc(pDb, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'WRONG_DEVICE',
      clientUid: 'P',
      clientName: 'Parent Name',
      clientEmail: 'parent@example.com',
      status: 'pending',
      createdAt: Timestamp.now(),
    })
  );
});

test('requestWithExpiredCode', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: Timestamp.fromMillis(Date.now() - 15 * 60 * 1000),
      expiresAt: Timestamp.fromMillis(Date.now() - 5 * 60 * 1000),
    });
  });
  const pDb = getDb('P');
  await assertFails(
    setDoc(doc(pDb, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'H',
      clientUid: 'P',
      clientName: 'Parent Name',
      clientEmail: 'parent@example.com',
      status: 'pending',
      createdAt: Timestamp.now(),
    })
  );
});

test('guestCreatesRequest', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 10 * 60 * 1000),
    });
  });
  const gDb = getDb('G', 'anonymous');
  await assertFails(
    setDoc(doc(gDb, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'H',
      clientUid: 'G',
      clientName: 'Guest',
      clientEmail: null,
      status: 'pending',
      createdAt: Timestamp.now(),
    })
  );
});

test('requestCreatedAsApproved', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 10 * 60 * 1000),
    });
  });
  const pDb = getDb('P');
  await assertFails(
    setDoc(doc(pDb, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'H',
      clientUid: 'P',
      clientName: 'Parent Name',
      clientEmail: 'parent@example.com',
      status: 'approved',
      createdAt: Timestamp.now(),
    })
  );
});

test('hostQueriesOwnPendingRequests', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'H',
      clientUid: 'P',
      clientName: 'Parent',
      clientEmail: 'parent@test.com',
      status: 'pending',
      createdAt: Timestamp.now(),
    });
  });
  const hDb = getDb('H');
  const q = query(
    collection(hDb, 'pairingRequests'),
    where('deviceId', '==', 'H'),
    where('status', '==', 'pending')
  );
  await assertSucceeds(getDocs(q));
});

test('strangerReadsRequest', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'H',
      clientUid: 'P',
      clientName: 'Parent',
      clientEmail: 'parent@test.com',
      status: 'pending',
      createdAt: Timestamp.now(),
    });
  });
  const sDb = getDb('S');
  await assertFails(getDoc(doc(sDb, 'pairingRequests', 'req1')));
});

test('hostApprovesBatch', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingCodes', '123456'), {
      code: '123456',
      deviceId: 'H',
      hostUid: 'H',
      createdAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + 10 * 60 * 1000),
    });
    await setDoc(doc(db, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'H',
      clientUid: 'P',
      clientName: 'Parent',
      clientEmail: 'parent@test.com',
      status: 'pending',
      createdAt: Timestamp.now(),
    });
  });
  const hDb = getDb('H');
  const batch = writeBatch(hDb);
  batch.update(doc(hDb, 'pairingRequests', 'req1'), {
    status: 'approved',
    decidedAt: Timestamp.now(),
  });
  batch.set(doc(hDb, 'devices', 'H', 'supervisors', 'P'), {
    uid: 'P',
    name: 'Parent',
    email: 'parent@test.com',
    requestId: 'req1',
    addedAt: Timestamp.now(),
  });
  batch.delete(doc(hDb, 'pairingCodes', '123456'));
  await assertSucceeds(batch.commit());
});

test('supervisorWithoutApprovedRequest', async () => {
  const hDb = getDb('H');
  await assertFails(
    setDoc(doc(hDb, 'devices', 'H', 'supervisors', 'P'), {
      uid: 'P',
      name: 'Parent',
      email: 'parent@test.com',
      requestId: 'nonexistent_req',
      addedAt: Timestamp.now(),
    })
  );
});

test('parentApprovesOwnRequest', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'H',
      clientUid: 'P',
      clientName: 'Parent',
      clientEmail: 'parent@test.com',
      status: 'pending',
      createdAt: Timestamp.now(),
    });
  });
  const pDb = getDb('P');
  await assertFails(
    updateDoc(doc(pDb, 'pairingRequests', 'req1'), {
      status: 'approved',
      decidedAt: Timestamp.now(),
    })
  );
});

test('approvedToDenied', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'pairingRequests', 'req1'), {
      code: '123456',
      deviceId: 'H',
      clientUid: 'P',
      clientName: 'Parent',
      clientEmail: 'parent@test.com',
      status: 'approved',
      createdAt: Timestamp.now(),
      decidedAt: Timestamp.now(),
    });
  });
  const hDb = getDb('H');
  await assertFails(
    updateDoc(doc(hDb, 'pairingRequests', 'req1'), {
      status: 'denied',
      decidedAt: Timestamp.now(),
    })
  );
});

test('supervisorRecreateDenied', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H', 'supervisors', 'P'), {
      uid: 'P',
      name: 'Parent',
      email: 'parent@test.com',
      requestId: 'req1',
      addedAt: Timestamp.now(),
    });
    await setDoc(doc(db, 'pairingRequests', 'req2'), {
      code: '654321',
      deviceId: 'H',
      clientUid: 'P',
      clientName: 'Parent',
      clientEmail: 'parent@test.com',
      status: 'approved',
      createdAt: Timestamp.now(),
      decidedAt: Timestamp.now(),
    });
  });
  const hDb = getDb('H');
  await assertFails(
    setDoc(doc(hDb, 'devices', 'H', 'supervisors', 'P'), {
      uid: 'P',
      name: 'Parent',
      email: 'parent@test.com',
      requestId: 'req2',
      addedAt: Timestamp.now(),
    })
  );
});

test('hostDeletesSupervisor', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H', 'supervisors', 'P'), {
      uid: 'P',
      name: 'Parent',
      email: 'parent@test.com',
      requestId: 'req1',
      addedAt: Timestamp.now(),
    });
  });
  const hDb = getDb('H');
  await assertFails(deleteDoc(doc(hDb, 'devices', 'H', 'supervisors', 'P')));
});

test('supervisorDeletesSelf', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H', 'supervisors', 'P'), {
      uid: 'P',
      name: 'Parent',
      email: 'parent@test.com',
      requestId: 'req1',
      addedAt: Timestamp.now(),
    });
  });
  const pDb = getDb('P');
  await assertSucceeds(deleteDoc(doc(pDb, 'devices', 'H', 'supervisors', 'P')));
});

test('supervisorReadsDevice', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H'), {
      hostUid: 'H',
      displayName: 'Host Phone',
      model: 'Pixel 8',
      createdAt: Timestamp.now(),
    });
    await setDoc(doc(db, 'devices', 'H', 'supervisors', 'P'), {
      uid: 'P',
      name: 'Parent',
      email: 'parent@test.com',
      requestId: 'req1',
      addedAt: Timestamp.now(),
    });
  });
  const pDb = getDb('P');
  await assertSucceeds(getDoc(doc(pDb, 'devices', 'H')));
});

test('strangerReadsDevice', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H'), {
      hostUid: 'H',
      displayName: 'Host Phone',
      model: 'Pixel 8',
      createdAt: Timestamp.now(),
    });
  });
  const sDb = getDb('S');
  await assertFails(getDoc(doc(sDb, 'devices', 'H')));
});

test('collectionGroupQueryOwnUid', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H1', 'supervisors', 'P'), {
      uid: 'P',
      name: 'Parent',
      email: 'parent@test.com',
      requestId: 'req1',
      addedAt: Timestamp.now(),
    });
    await setDoc(doc(db, 'devices', 'H2', 'supervisors', 'P'), {
      uid: 'P',
      name: 'Parent',
      email: 'parent@test.com',
      requestId: 'req2',
      addedAt: Timestamp.now(),
    });
  });
  const pDb = getDb('P');
  const q = query(collectionGroup(pDb, 'supervisors'), where('uid', '==', 'P'));
  await assertSucceeds(getDocs(q));
});

test('collectionGroupQueryOtherUid', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'devices', 'H', 'supervisors', 'OTHER'), {
      uid: 'OTHER',
      name: 'Other',
      email: 'other@test.com',
      requestId: 'req1',
      addedAt: Timestamp.now(),
    });
  });
  const pDb = getDb('P');
  const q = query(collectionGroup(pDb, 'supervisors'), where('uid', '==', 'OTHER'));
  await assertFails(getDocs(q));
});
