import { before, after, beforeEach, test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, getDocs, collection, deleteDoc, updateDoc, writeBatch, serverTimestamp, Timestamp } from 'firebase/firestore';

let env;
const code = 'a'.repeat(32);
const device = '12345678-1234-1234-1234-123456789abc';
const db = uid => env.authenticatedContext(uid).firestore();
const member = (name, inviteCode = code) => ({ name, inviteCode, joinedAt: serverTimestamp() });
const summary = (userId = 'alice') => ({ userId, deviceId: device, day: '2026-10-25', zoneId: 'Asia/Kolkata',
  screenMillis: 3000, shortMillis: 2000, views: 1, collectedAt: Timestamp.fromMillis(Date.now() - 1000), updatedAt: serverTimestamp(), apps: {}, detectorVersion: 1 });
const path = (uid = 'alice') => `families/home/summaries/${uid}_${device}_2026-10-25`;
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-kintime', firestore: { rules: await readFile('firestore.rules', 'utf8') } }); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const admin = context.firestore();
    await setDoc(doc(admin, 'families/home'), { ownerUid: 'alice', name: 'Home', createdAt: Timestamp.now() });
    await setDoc(doc(admin, 'families/home/members/alice'), { name: 'Alice', joinedAt: Timestamp.now(), inviteCode: 'owner' });
    await setDoc(doc(admin, `invites/${code}`), { familyId: 'home', createdBy: 'alice', createdAt: Timestamp.now(), expiresAt: Timestamp.fromMillis(Date.now() + 3600000) });
  });
});
test('creator can atomically create a family and its first membership', async () => {
  const store = db('bob'), batch = writeBatch(store);
  batch.set(doc(store, 'families/new'), { name: 'New family', ownerUid: 'bob', createdAt: serverTimestamp() });
  batch.set(doc(store, 'families/new/members/bob'), member('Bob', 'owner'));
  await assertSucceeds(batch.commit());
});
test('outsiders and unauthenticated users cannot read family stats', async () => {
  await assertFails(getDoc(doc(db('mallory'), 'families/home')));
  await assertFails(getDocs(collection(db('mallory'), 'families/home/summaries')));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'families/home')));
});
test('valid invitation admits only the requesting user', async () => {
  await assertSucceeds(getDoc(doc(db('bob'), `invites/${code}`)));
  await assertSucceeds(setDoc(doc(db('bob'), 'families/home/members/bob'), member('Bob')));
  await assertSucceeds(getDoc(doc(db('bob'), 'families/home')));
  await assertFails(setDoc(doc(db('mallory'), 'families/home/members/victim'), member('Victim')));
});
test('missing and expired invitations cannot grant membership', async () => {
  await assertFails(setDoc(doc(db('bob'), 'families/home/members/bob'), member('Bob', 'b'.repeat(32))));
  await env.withSecurityRulesDisabled(async context => { await updateDoc(doc(context.firestore(), `invites/${code}`), { expiresAt: Timestamp.fromMillis(1) }); });
  await assertFails(setDoc(doc(db('bob'), 'families/home/members/bob'), member('Bob')));
});
test('invitation codes cannot be enumerated, modified, or created by outsiders', async () => {
  await assertFails(getDocs(collection(db('bob'), 'invites')));
  await assertFails(updateDoc(doc(db('alice'), `invites/${code}`), { familyId: 'elsewhere' }));
  await assertFails(setDoc(doc(db('bob'), `invites/${'b'.repeat(32)}`), { familyId: 'home', createdBy: 'bob', createdAt: serverTimestamp(), expiresAt: Timestamp.fromMillis(Date.now() + 1000) }));
});
test('users can check their own membership without enumerating or reading other members', async () => {
  await assertSucceeds(getDoc(doc(db('bob'), 'families/home/members/bob')));
  await assertFails(getDoc(doc(db('bob'), 'families/home/members/alice')));
  await assertFails(getDocs(collection(db('bob'), 'families/home/members')));
});
test('members can update only their own display name', async () => {
  await assertSucceeds(updateDoc(doc(db('alice'), 'families/home/members/alice'), { name: 'New name' }));
  await assertFails(updateDoc(doc(db('alice'), 'families/home/members/alice'), { inviteCode: 'replacement' }));
  await assertFails(setDoc(doc(db('alice'), 'families/home/members/bob'), member('Bob')));
});
test('members can write only their own well formed statistics', async () => {
  await assertSucceeds(setDoc(doc(db('alice'), path()), summary()));
  await assertFails(setDoc(doc(db('alice'), path('bob')), summary('bob')));
  await assertFails(setDoc(doc(db('alice'), path()), { ...summary(), screenMillis: -1 }));
  await assertFails(setDoc(doc(db('alice'), path()), { ...summary(), extraPrivateData: 'unexpected' }));
});
test('family members read summaries but cannot overwrite another person', async () => {
  await setDoc(doc(db('bob'), 'families/home/members/bob'), member('Bob'));
  await setDoc(doc(db('alice'), path()), summary());
  await assertSucceeds(getDoc(doc(db('bob'), path())));
  await assertFails(updateDoc(doc(db('bob'), path()), { views: 99, updatedAt: serverTimestamp() }));
  await assertFails(deleteDoc(doc(db('bob'), path())));
});
test('a departing member loses access and can still remove their own old summaries', async () => {
  await setDoc(doc(db('alice'), path()), summary());
  await deleteDoc(doc(db('alice'), 'families/home/members/alice'));
  await assertFails(getDoc(doc(db('alice'), path())));
  await assertSucceeds(deleteDoc(doc(db('alice'), path())));
  await assertFails(setDoc(doc(db('bob'), 'families/home/members/bob'), member('Bob')));
});
test('removing optional app breakdown preserves the rest of a summary', async () => {
  await setDoc(doc(db('alice'), path()), { ...summary(), apps: { 'com.instagram.android': 1000 } });
  await assertSucceeds(updateDoc(doc(db('alice'), path()), { apps: {} }));
  await assertFails(updateDoc(doc(db('alice'), path()), { apps: {}, views: -10 }));
});
