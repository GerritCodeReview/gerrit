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

import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyA;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyB;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyC;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyD;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyE;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyF;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyG;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyH;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyI;
import static com.google.gerrit.gpg.testutil.TestTrustKey.keyJ;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.gpg.testutil.TestKey;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PublicKeyCheckerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private InMemoryRepository repo;
  private PublicKeyStore store;

  @Before
  public void setUp() {
    repo = new InMemoryRepository(new DfsRepositoryDescription("repo"));
    store = new PublicKeyStore(repo);
  }

  @After
  public void tearDown() {
    if (store != null) {
      store.close();
      store = null;
    }
    if (repo != null) {
      repo.close();
      repo = null;
    }
  }

  @Test
  public void validKey() throws Exception {
    assertProblems(TestKey.key1());
  }

  @Test
  public void keyExpiringInFuture() throws Exception {
    assertProblems(TestKey.key2());
  }

  @Test
  public void expiredKey() throws Exception {
    assertProblems(TestKey.key3(), "Key is expired");
  }

  @Test
  public void selfRevokedKey() throws Exception {
    assertProblems(TestKey.key4(), "Key is revoked");
  }

  // Test keys specific to this test are at the bottom of this class. Each test
  // has a diagram of the trust network, where:
  //  - The notation M---N indicates N trusts M.
  //  - An 'x' indicates the key is expired.

  @Test
  public void trustValidPathLength2() throws Exception {
    // A---Bx
    //  \
    //   \---C---D
    //        \
    //         \---Ex
    //
    // D and E trust C to be a valid introducer of depth 2.
    TestKey ka = add(keyA());
    TestKey kb = add(keyB());
    TestKey kc = add(keyC());
    TestKey kd = add(keyD());
    TestKey ke = add(keyE());
    save();

    PublicKeyChecker checker = newChecker(2, kb, kd);
    assertProblems(checker, ka);
    assertProblems(checker, kb, "Key is expired");
    assertProblems(checker, kc);
    assertProblems(checker, kd);
    assertProblems(checker, ke, "Key is expired", "No path to a trusted key");
  }

  @Test
  public void trustValidPathLength1() throws Exception {
    // A---Bx
    //  \
    //   \---C---D
    //        \
    //         \---Ex
    //
    // D and E trust C to be a valid introducer of depth 2.
    TestKey ka = add(keyA());
    TestKey kb = add(keyB());
    TestKey kc = add(keyC());
    TestKey kd = add(keyD());
    add(keyE());
    save();

    PublicKeyChecker checker = newChecker(1, kd);
    assertProblems(checker, ka,
        "No path to a trusted key", notTrusted(kb), notTrusted(kc));
  }

  @Test
  public void trustCycle() throws Exception {
    // F---G---F, in a cycle.
    TestKey kf = add(keyF());
    TestKey kg = add(keyG());
    save();

    PublicKeyChecker checker = newChecker(10, keyA());
    assertProblems(checker, kf,
        "No path to a trusted key", notTrusted(kg));
    assertProblems(checker, kg,
        "No path to a trusted key", notTrusted(kf));
  }

  @Test
  public void trustInsufficientDepthInSignature() throws Exception {
    // H---I---J, but J is only trusted to length 1.
    TestKey kh = add(keyH());
    TestKey ki = add(keyI());
    add(keyJ());
    save();

    PublicKeyChecker checker = newChecker(10, keyJ());

    // J trusts I to a depth of 1, so I itself is valid, but I's certification
    // of K is not valid.
    assertProblems(checker, ki);
    assertProblems(checker, kh,
        "No path to a trusted key", notTrusted(ki));
  }

  private PublicKeyChecker newChecker(int maxTrustDepth, TestKey... trusted) {
    List<Fingerprint> fps = new ArrayList<>(trusted.length);
    for (TestKey k : trusted) {
      fps.add(new Fingerprint(k.getPublicKey().getFingerprint()));
    }
    return new PublicKeyChecker(maxTrustDepth, fps);
  }

  private TestKey add(TestKey k) {
    store.add(k.getPublicKeyRing());
    return k;
  }

  private void save() throws Exception {
    PersonIdent ident = new PersonIdent("A U Thor", "author@example.com");
    CommitBuilder cb = new CommitBuilder();
    cb.setAuthor(ident);
    cb.setCommitter(ident);
    RefUpdate.Result result = store.save(cb);
    switch (result) {
      case NEW:
      case FAST_FORWARD:
      case FORCED:
        break;
      default:
        throw new AssertionError(result);
    }
  }

  private void assertProblems(PublicKeyChecker checker, TestKey k,
      String... expected) {
    CheckResult result = checker.check(k.getPublicKey(), store);
    assertEquals(Arrays.asList(expected), result.getProblems());
  }

  private void assertProblems(TestKey tk, String... expected) throws Exception {
    CheckResult result = new PublicKeyChecker().check(tk.getPublicKey(), store);
    assertEquals(Arrays.asList(expected), result.getProblems());
  }

  private static String notTrusted(TestKey k) {
    return "Certification by " + keyToString(k.getPublicKey())
        + " is valid, but key is not trusted";
  }
}
