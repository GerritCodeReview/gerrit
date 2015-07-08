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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyObjectId;
import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyToString;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.RefNames;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

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
    PGPPublicKey key = TestKey.KEY1.getPublicKey();
    assertThat(keyIdToString(key.getKeyID())).isEqualTo("46328A8C");
  }

  @Test
  public void testKeyToString() throws Exception {
    PGPPublicKey key = TestKey.KEY1.getPublicKey();
    assertThat(keyToString(key))
        .isEqualTo("46328A8C Testuser One <test1@example.com>"
          + " (04AE A7ED 2F82 1133 E5B1  28D1 ED06 25DC 4632 8A8C)");
  }

  @Test
  public void testKeyObjectId() throws Exception {
    PGPPublicKey key = TestKey.KEY1.getPublicKey();
    String objId = keyObjectId(key.getKeyID()).name();
    assertThat(objId).isEqualTo("ed0625dc46328a8c000000000000000000000000");
    assertThat(objId.substring(8, 16))
        .isEqualTo(keyIdToString(key.getKeyID()).toLowerCase());
  }

  @Test
  public void testGet() throws Exception {
    PGPPublicKey key1 = TestKey.KEY1.getPublicKey();
    tr.branch(RefNames.REFS_GPG_KEYS)
        .commit()
        .add(keyObjectId(key1.getKeyID()).name(),
          TestKey.KEY1.getPublicKeyArmored())
        .create();
    PGPPublicKey key2 = TestKey.KEY2.getPublicKey();
    tr.branch(RefNames.REFS_GPG_KEYS)
        .commit()
        .add(keyObjectId(key2.getKeyID()).name(),
          TestKey.KEY2.getPublicKeyArmored())
        .create();

    assertKeys(key1.getKeyID(), key1);
    assertKeys(key2.getKeyID(), key2);
  }

  @Test
  public void testGetMultiple() throws Exception {
    PGPPublicKey key1 = TestKey.KEY1.getPublicKey();
    PGPPublicKey key2 = TestKey.KEY2.getPublicKey();
    tr.branch(RefNames.REFS_GPG_KEYS)
        .commit()
        .add(keyObjectId(key1.getKeyID()).name(),
            TestKey.KEY1.getPublicKeyArmored()
              // Mismatched for this key ID, but we can still read it out.
              + TestKey.KEY2.getPublicKeyArmored())
        .create();
    assertKeys(key1.getKeyID(), key1, key2);
  }

  private void assertKeys(long keyId, PGPPublicKey... expected)
      throws Exception {
    final Function<PGPPublicKey, String> keyToString =
        new Function<PGPPublicKey, String>() {
          @Override
          public String apply(PGPPublicKey in) {
            return keyToString(in);
          }
        };
    Function<PGPPublicKeyRing, String> keyRingToString =
        new Function<PGPPublicKeyRing, String>() {
          @Override
          public String apply(PGPPublicKeyRing in) {
            return keyToString.apply(in.getPublicKey());
          }
        };
    assertThat(Iterables.transform(store.get(keyId), keyRingToString))
        .containsExactlyElementsIn(
          FluentIterable.of(expected).transform(keyToString));
  }
}
