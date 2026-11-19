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

test("Unauthenticated user cannot read stream_configs or stream_notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("stream_configs").get());
  await assertFails(unauthDb.collection("stream_notes").get());
});

test("Authenticated user can create and query their own stream_configs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("stream_configs").doc("cfg_1").set({
      userId: ALICE_UID,
      title: "Evening Gaming Live",
      description: "720p 30fps YouTube stream",
      rtmpUrl: "rtmp://a.rtmp.youtube.com/live2",
      streamKey: "xxxx-xxxx-xxxx-xxxx",
      privacyStatus: "public",
      resolution: "1280x720",
      fps: 30,
      bitrateKbps: 6000,
      cameraEnabled: true,
      micEnabled: true,
      gameAudioEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("stream_configs").where("userId", "==", ALICE_UID).get()
  );
});

test("Authenticated user cannot read another user's stream_configs or query without filter", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("stream_configs").doc("bob_cfg").set({
      userId: BOB_UID,
      title: "Bob Stream",
      description: "Bob desc",
      rtmpUrl: "rtmp://a.rtmp.youtube.com/live2",
      streamKey: "bob-key",
      privacyStatus: "unlisted",
      resolution: "1280x720",
      fps: 30,
      bitrateKbps: 6000,
      cameraEnabled: false,
      micEnabled: true,
      gameAudioEnabled: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("stream_configs").doc("bob_cfg").get());
  await assertFails(aliceDb.collection("stream_configs").get());
});

test("Shadow update with ghost field on stream_notes is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("stream_notes").doc("note_1").set({
      userId: ALICE_UID,
      title: "Warmup Checklist",
      content: "Check mic and game audio levels before starting live.",
      isPinned: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("stream_notes").doc("note_1").update({
      title: "Updated",
      isVerified: true,
      updatedAt: new Date(),
    })
  );
});
