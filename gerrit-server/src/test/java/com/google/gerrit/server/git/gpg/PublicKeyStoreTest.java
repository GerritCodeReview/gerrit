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

package com.google.gerrit.server.git.gpg;

import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyObjectId;
import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyToString;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.reviewdb.client.RefNames;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;

public class PublicKeyStoreTest {
  private TestRepository<?> tr;
  private PublicKeyStore store;

  @Before
  public void setUp() throws Exception {
    tr = new TestRepository<>(new InMemoryRepository(
        new DfsRepositoryDescription("pubkeys")));
    store = new PublicKeyStore(tr.getRepository());
  }

  @Test
  public void testKeyIdToString() throws Exception {
    PGPPublicKey key = TestKey.key1().getPublicKey();
    assertEquals("46328A8C", keyIdToString(key.getKeyID()));
  }

  @Test
  public void testKeyToString() throws Exception {
    PGPPublicKey key = TestKey.key1().getPublicKey();
    assertEquals("46328A8C Testuser One <test1@example.com>"
          + " (04AE A7ED 2F82 1133 E5B1  28D1 ED06 25DC 4632 8A8C)",
        keyToString(key));
  }

  @Test
  public void testKeyObjectId() throws Exception {
    PGPPublicKey key = TestKey.key1().getPublicKey();
    String objId = keyObjectId(key.getKeyID()).name();
    assertEquals("ed0625dc46328a8c000000000000000000000000", objId);
    assertEquals(keyIdToString(key.getKeyID()).toLowerCase(),
        objId.substring(8, 16));
  }

  @Test
  public void testGet() throws Exception {
    TestKey key1 = TestKey.key1();
    tr.branch(RefNames.REFS_GPG_KEYS)
        .commit()
        .add(keyObjectId(key1.getKeyId()).name(),
          key1.getPublicKeyArmored())
        .create();
    TestKey key2 = TestKey.key2();
    tr.branch(RefNames.REFS_GPG_KEYS)
        .commit()
        .add(keyObjectId(key2.getKeyId()).name(),
          key2.getPublicKeyArmored())
        .create();

    assertKeys(key1.getKeyId(), key1);
    assertKeys(key2.getKeyId(), key2);
  }

  @Test
  public void testGetMultiple() throws Exception {
    TestKey key1 = TestKey.key1();
    TestKey key2 = TestKey.key2();
    tr.branch(RefNames.REFS_GPG_KEYS)
        .commit()
        .add(keyObjectId(key1.getKeyId()).name(),
            key1.getPublicKeyArmored()
              // Mismatched for this key ID, but we can still read it out.
              + key2.getPublicKeyArmored())
        .create();
    assertKeys(key1.getKeyId(), key1, key2);
  }

  private void assertKeys(long keyId, TestKey... expected)
      throws Exception {
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
}
