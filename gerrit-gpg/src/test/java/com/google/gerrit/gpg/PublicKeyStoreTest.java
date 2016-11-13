// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.gpg;

import static com.google.gerrit.gpg.PublicKeyStore.REFS_GPG_KEYS;
import static com.google.gerrit.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.gpg.PublicKeyStore.keyObjectId;
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithExpiration;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithSecondUserId;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithoutExpiration;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.gpg.testutil.TestKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class PublicKeyStoreTest {
  private TestRepository<?> tr;
  private PublicKeyStore store;

  @Before
  public void setUp() throws Exception {
    tr = new TestRepository<>(new InMemoryRepository(new DfsRepositoryDescription("pubkeys")));
    store = new PublicKeyStore(tr.getRepository());
  }

  @Test
  public void testKeyIdToString() throws Exception {
    PGPPublicKey key = validKeyWithoutExpiration().getPublicKey();
    assertEquals("46328A8C", keyIdToString(key.getKeyID()));
  }

  @Test
  public void testKeyToString() throws Exception {
    PGPPublicKey key = validKeyWithoutExpiration().getPublicKey();
    assertEquals(
        "46328A8C Testuser One <test1@example.com>"
            + " (04AE A7ED 2F82 1133 E5B1  28D1 ED06 25DC 4632 8A8C)",
        keyToString(key));
  }

  @Test
  public void testKeyObjectId() throws Exception {
    PGPPublicKey key = validKeyWithoutExpiration().getPublicKey();
    String objId = keyObjectId(key.getKeyID()).name();
    assertEquals("ed0625dc46328a8c000000000000000000000000", objId);
    assertEquals(keyIdToString(key.getKeyID()).toLowerCase(), objId.substring(8, 16));
  }

  @Test
  public void testGet() throws Exception {
    TestKey key1 = validKeyWithoutExpiration();
    tr.branch(REFS_GPG_KEYS)
        .commit()
        .add(keyObjectId(key1.getKeyId()).name(), key1.getPublicKeyArmored())
        .create();
    TestKey key2 = validKeyWithExpiration();
    tr.branch(REFS_GPG_KEYS)
        .commit()
        .add(keyObjectId(key2.getKeyId()).name(), key2.getPublicKeyArmored())
        .create();

    assertKeys(key1.getKeyId(), key1);
    assertKeys(key2.getKeyId(), key2);
  }

  @Test
  public void testGetMultiple() throws Exception {
    TestKey key1 = validKeyWithoutExpiration();
    TestKey key2 = validKeyWithExpiration();
    tr.branch(REFS_GPG_KEYS)
        .commit()
        .add(
            keyObjectId(key1.getKeyId()).name(),
            key1.getPublicKeyArmored()
                // Mismatched for this key ID, but we can still read it out.
                + key2.getPublicKeyArmored())
        .create();
    assertKeys(key1.getKeyId(), key1, key2);
  }

  @Test
  public void save() throws Exception {
    TestKey key1 = validKeyWithoutExpiration();
    TestKey key2 = validKeyWithExpiration();
    store.add(key1.getPublicKeyRing());
    store.add(key2.getPublicKeyRing());

    assertEquals(RefUpdate.Result.NEW, store.save(newCommitBuilder()));

    assertKeys(key1.getKeyId(), key1);
    assertKeys(key2.getKeyId(), key2);
  }

  @Test
  public void saveAppendsToExistingList() throws Exception {
    TestKey key1 = validKeyWithoutExpiration();
    TestKey key2 = validKeyWithExpiration();
    tr.branch(REFS_GPG_KEYS)
        .commit()
        // Mismatched for this key ID, but we can still read it out.
        .add(keyObjectId(key1.getKeyId()).name(), key2.getPublicKeyArmored())
        .create();

    store.add(key1.getPublicKeyRing());
    assertEquals(RefUpdate.Result.FAST_FORWARD, store.save(newCommitBuilder()));

    assertKeys(key1.getKeyId(), key1, key2);

    try (ObjectReader reader = tr.getRepository().newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      NoteMap notes =
          NoteMap.read(
              reader,
              tr.getRevWalk()
                  .parseCommit(tr.getRepository().exactRef(REFS_GPG_KEYS).getObjectId()));
      String contents =
          new String(reader.open(notes.get(keyObjectId(key1.getKeyId()))).getBytes(), UTF_8);
      String header = "-----BEGIN PGP PUBLIC KEY BLOCK-----";
      int i1 = contents.indexOf(header);
      assertTrue(i1 >= 0);
      int i2 = contents.indexOf(header, i1 + header.length());
      assertTrue(i2 >= 0);
    }
  }

  @Test
  public void updateExisting() throws Exception {
    TestKey key5 = validKeyWithSecondUserId();
    PGPPublicKeyRing keyRing = key5.getPublicKeyRing();
    PGPPublicKey key = keyRing.getPublicKey();
    store.add(keyRing);
    assertEquals(RefUpdate.Result.NEW, store.save(newCommitBuilder()));

    assertUserIds(
        store.get(key5.getKeyId()).iterator().next(),
        "Testuser Five <test5@example.com>",
        "foo:myId");

    keyRing = PGPPublicKeyRing.removePublicKey(keyRing, key);
    key = PGPPublicKey.removeCertification(key, "foo:myId");
    keyRing = PGPPublicKeyRing.insertPublicKey(keyRing, key);
    store.add(keyRing);
    assertEquals(RefUpdate.Result.FAST_FORWARD, store.save(newCommitBuilder()));

    Iterator<PGPPublicKeyRing> keyRings = store.get(key.getKeyID()).iterator();
    keyRing = keyRings.next();
    assertFalse(keyRings.hasNext());
    assertUserIds(keyRing, "Testuser Five <test5@example.com>");
  }

  @Test
  public void remove() throws Exception {
    TestKey key1 = validKeyWithoutExpiration();
    store.add(key1.getPublicKeyRing());
    assertEquals(RefUpdate.Result.NEW, store.save(newCommitBuilder()));
    assertKeys(key1.getKeyId(), key1);

    store.remove(key1.getPublicKey().getFingerprint());
    assertEquals(RefUpdate.Result.FAST_FORWARD, store.save(newCommitBuilder()));
    assertKeys(key1.getKeyId());
  }

  @Test
  public void removeNonexisting() throws Exception {
    TestKey key1 = validKeyWithoutExpiration();
    store.add(key1.getPublicKeyRing());
    assertEquals(RefUpdate.Result.NEW, store.save(newCommitBuilder()));

    TestKey key2 = validKeyWithExpiration();
    store.remove(key2.getPublicKey().getFingerprint());
    assertEquals(RefUpdate.Result.NO_CHANGE, store.save(newCommitBuilder()));
    assertKeys(key1.getKeyId(), key1);
  }

  @Test
  public void addThenRemove() throws Exception {
    TestKey key1 = validKeyWithoutExpiration();
    store.add(key1.getPublicKeyRing());
    store.remove(key1.getPublicKey().getFingerprint());
    assertEquals(RefUpdate.Result.NO_CHANGE, store.save(newCommitBuilder()));
    assertKeys(key1.getKeyId());
  }

  private void assertKeys(long keyId, TestKey... expected) throws Exception {
    Set<String> expectedStrings = new TreeSet<>();
    for (TestKey k : expected) {
      expectedStrings.add(keyToString(k.getPublicKey()));
    }
    PGPPublicKeyRingCollection actual = store.get(keyId);
    Set<String> actualStrings = new TreeSet<>();
    for (PGPPublicKeyRing k : actual) {
      actualStrings.add(keyToString(k.getPublicKey()));
    }
    assertEquals(expectedStrings, actualStrings);
  }

  private void assertUserIds(PGPPublicKeyRing keyRing, String... expected) throws Exception {
    List<String> actual = new ArrayList<>();
    @SuppressWarnings("unchecked")
    Iterator<String> userIds =
        store.get(keyRing.getPublicKey().getKeyID()).iterator().next().getPublicKey().getUserIDs();
    while (userIds.hasNext()) {
      actual.add(userIds.next());
    }

    assertEquals(Arrays.asList(expected), actual);
  }

  private CommitBuilder newCommitBuilder() {
    CommitBuilder cb = new CommitBuilder();
    PersonIdent ident = new PersonIdent("A U Thor", "author@example.com");
    cb.setAuthor(ident);
    cb.setCommitter(ident);
    return cb;
  }
}
