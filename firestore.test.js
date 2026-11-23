const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
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

test("Unauthenticated users cannot read or write songs or comments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("songs").doc("song_1").get());
  await assertFails(
    unauthDb.collection("songs").doc("song_1").collection("comments").get()
  );
  await assertFails(
    unauthDb.collection("songs").doc("song_1").set({
      title: "Kesariya",
      artist: "Arijit Singh",
      likeCount: 1,
      commentCount: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Signed-in user can create song stats and increment/decrement likes & comments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const songRef = aliceDb.collection("songs").doc("song_1");

  await assertSucceeds(
    songRef.set({
      title: "Apna Bana Le",
      artist: "Arijit Singh",
      likeCount: 1,
      commentCount: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Increment commentCount by 1
  await assertSucceeds(
    songRef.update({
      commentCount: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Decrement likeCount by 1
  await assertSucceeds(
    songRef.update({
      likeCount: 0,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject arbitrary jump in likeCount
  await assertFails(
    songRef.update({
      likeCount: 999,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject ghost field on song stats
  await assertFails(
    songRef.update({
      likeCount: 1,
      isVerified: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject listing entire songs collection
  await assertFails(aliceDb.collection("songs").get());
});

test("Signed-in user can create and list comments, and delete only own comments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const commentsCol = aliceDb.collection("songs").doc("song_1").collection("comments");

  await assertSucceeds(
    commentsCol.doc("c_1").set({
      text: "Amazing track!",
      name: "Alice",
      uid: ALICE_UID,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Query newest first, limit 100
  await assertSucceeds(commentsCol.orderBy("createdAt", "desc").limit(100).get());

  // Reject spoofed uid
  await assertFails(
    commentsCol.doc("c_spoof").set({
      text: "Spoofed!",
      name: "Alice",
      uid: BOB_UID,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Reject comment > 300 chars
  await assertFails(
    commentsCol.doc("c_long").set({
      text: "a".repeat(301),
      name: "Alice",
      uid: ALICE_UID,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp
  await assertFails(
    commentsCol.doc("c_future").set({
      text: "Future",
      name: "Alice",
      uid: ALICE_UID,
      createdAt: new Date(Date.now() + 60000),
    })
  );

  // Reject updating comments
  await assertFails(commentsCol.doc("c_1").update({ text: "Edited" }));

  // Bob cannot delete Alice's comment
  await assertFails(
    bobDb.collection("songs").doc("song_1").collection("comments").doc("c_1").delete()
  );

  // Alice can delete her own comment
  await assertSucceeds(commentsCol.doc("c_1").delete());
});
