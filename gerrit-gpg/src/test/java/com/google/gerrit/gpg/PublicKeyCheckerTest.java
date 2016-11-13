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
import static com.google.gerrit.gpg.testutil.TestKeys.expiredKey;
import static com.google.gerrit.gpg.testutil.TestKeys.keyRevokedByExpiredKeyAfterExpiration;
import static com.google.gerrit.gpg.testutil.TestKeys.keyRevokedByExpiredKeyBeforeExpiration;
import static com.google.gerrit.gpg.testutil.TestKeys.revokedCompromisedKey;
import static com.google.gerrit.gpg.testutil.TestKeys.revokedNoLongerUsedKey;
import static com.google.gerrit.gpg.testutil.TestKeys.selfRevokedKey;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithExpiration;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyA;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyB;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyC;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyD;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyE;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyF;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyG;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyH;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyI;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyJ;
import static org.bouncycastle.bcpg.SignatureSubpacketTags.REVOCATION_KEY;
import static org.bouncycastle.openpgp.PGPSignature.DIRECT_KEY;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.gpg.testutil.TestKey;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
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

public class PublicKeyCheckerTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

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
    assertNoProblems(validKeyWithoutExpiration());
  }

  @Test
  public void keyExpiringInFuture() throws Exception {
    TestKey k = validKeyWithExpiration();

    PublicKeyChecker checker = new PublicKeyChecker().setStore(store);
    assertNoProblems(checker, k);

    checker.setEffectiveTime(parseDate("2015-07-10 12:00:00 -0400"));
    assertNoProblems(checker, k);

    checker.setEffectiveTime(parseDate("2075-07-10 12:00:00 -0400"));
    assertProblems(checker, k, "Key is expired");
  }

  @Test
  public void expiredKeyIsExpired() throws Exception {
    assertProblems(expiredKey(), "Key is expired");
  }

  @Test
  public void selfRevokedKeyIsRevoked() throws Exception {
    assertProblems(selfRevokedKey(), "Key is revoked (key material has been compromised)");
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
    assertNoProblems(checker, ka);
    assertProblems(checker, kb, "Key is expired");
    assertNoProblems(checker, kc);
    assertNoProblems(checker, kd);
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
    assertProblems(checker, ka, "No path to a trusted key", notTrusted(kb), notTrusted(kc));
  }

  @Test
  public void trustCycle() throws Exception {
    // F---G---F, in a cycle.
    TestKey kf = add(keyF());
    TestKey kg = add(keyG());
    save();

    PublicKeyChecker checker = newChecker(10, keyA());
    assertProblems(checker, kf, "No path to a trusted key", notTrusted(kg));
    assertProblems(checker, kg, "No path to a trusted key", notTrusted(kf));
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
    assertNoProblems(checker, ki);
    assertProblems(checker, kh, "No path to a trusted key", notTrusted(ki));
  }

  @Test
  public void revokedKeyDueToCompromise() throws Exception {
    TestKey k = add(revokedCompromisedKey());
    add(validKeyWithoutExpiration());
    save();

    assertProblems(k, "Key is revoked (key material has been compromised):" + " test6 compromised");

    PGPPublicKeyRing kr = removeRevokers(k.getPublicKeyRing());
    store.add(kr);
    save();

    // Key no longer specified as revoker.
    assertNoProblems(kr.getPublicKey());
  }

  @Test
  public void revokedKeyDueToCompromiseRevokesKeyRetroactively() throws Exception {
    TestKey k = add(revokedCompromisedKey());
    add(validKeyWithoutExpiration());
    save();

    String problem = "Key is revoked (key material has been compromised): test6 compromised";
    assertProblems(k, problem);

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    PublicKeyChecker checker =
        new PublicKeyChecker().setStore(store).setEffectiveTime(df.parse("2010-01-01 12:00:00"));
    assertProblems(checker, k, problem);
  }

  @Test
  public void revokedByKeyNotPresentInStore() throws Exception {
    TestKey k = add(revokedCompromisedKey());
    save();

    assertProblems(k, "Key is revoked (key material has been compromised):" + " test6 compromised");
  }

  @Test
  public void revokedKeyDueToNoLongerBeingUsed() throws Exception {
    TestKey k = add(revokedNoLongerUsedKey());
    add(validKeyWithoutExpiration());
    save();

    assertProblems(k, "Key is revoked (retired and no longer valid): test7 not used");
  }

  @Test
  public void revokedKeyDueToNoLongerBeingUsedDoesNotRevokeKeyRetroactively() throws Exception {
    TestKey k = add(revokedNoLongerUsedKey());
    add(validKeyWithoutExpiration());
    save();

    assertProblems(k, "Key is revoked (retired and no longer valid): test7 not used");

    PublicKeyChecker checker =
        new PublicKeyChecker()
            .setStore(store)
            .setEffectiveTime(parseDate("2010-01-01 12:00:00 -0400"));
    assertNoProblems(checker, k);
  }

  @Test
  public void keyRevokedByExpiredKeyAfterExpirationIsNotRevoked() throws Exception {
    TestKey k = add(keyRevokedByExpiredKeyAfterExpiration());
    add(expiredKey());
    save();

    PublicKeyChecker checker = new PublicKeyChecker().setStore(store);
    assertNoProblems(checker, k);
  }

  @Test
  public void keyRevokedByExpiredKeyBeforeExpirationIsRevoked() throws Exception {
    TestKey k = add(keyRevokedByExpiredKeyBeforeExpiration());
    add(expiredKey());
    save();

    PublicKeyChecker checker = new PublicKeyChecker().setStore(store);
    assertProblems(checker, k, "Key is revoked (retired and no longer valid): test9 not used");

    // Set time between key creation and revocation.
    checker.setEffectiveTime(parseDate("2005-08-01 13:00:00 -0400"));
    assertNoProblems(checker, k);
  }

  private PGPPublicKeyRing removeRevokers(PGPPublicKeyRing kr) {
    PGPPublicKey k = kr.getPublicKey();
    @SuppressWarnings("unchecked")
    Iterator<PGPSignature> sigs = k.getSignaturesOfType(DIRECT_KEY);
    while (sigs.hasNext()) {
      PGPSignature sig = sigs.next();
      if (sig.getHashedSubPackets().hasSubpacket(REVOCATION_KEY)) {
        k = PGPPublicKey.removeCertification(k, sig);
      }
    }
    return PGPPublicKeyRing.insertPublicKey(kr, k);
  }

  private PublicKeyChecker newChecker(int maxTrustDepth, TestKey... trusted) {
    Map<Long, Fingerprint> fps = new HashMap<>();
    for (TestKey k : trusted) {
      Fingerprint fp = new Fingerprint(k.getPublicKey().getFingerprint());
      fps.put(fp.getId(), fp);
    }
    return new PublicKeyChecker().enableTrust(maxTrustDepth, fps).setStore(store);
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
      case IO_FAILURE:
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case NO_CHANGE:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case RENAMED:
      default:
        throw new AssertionError(result);
    }
  }

  private void assertProblems(PublicKeyChecker checker, TestKey k, String first, String... rest) {
    CheckResult result = checker.setStore(store).check(k.getPublicKey());
    assertEquals(list(first, rest), result.getProblems());
  }

  private void assertNoProblems(PublicKeyChecker checker, TestKey k) {
    CheckResult result = checker.setStore(store).check(k.getPublicKey());
    assertEquals(Collections.emptyList(), result.getProblems());
  }

  private void assertProblems(TestKey tk, String first, String... rest) {
    assertProblems(tk.getPublicKey(), first, rest);
  }

  private void assertNoProblems(TestKey tk) {
    assertNoProblems(tk.getPublicKey());
  }

  private void assertProblems(PGPPublicKey k, String first, String... rest) {
    CheckResult result = new PublicKeyChecker().setStore(store).check(k);
    assertEquals(list(first, rest), result.getProblems());
  }

  private void assertNoProblems(PGPPublicKey k) {
    CheckResult result = new PublicKeyChecker().setStore(store).check(k);
    assertEquals(Collections.emptyList(), result.getProblems());
  }

  private static String notTrusted(TestKey k) {
    return "Certification by "
        + keyToString(k.getPublicKey())
        + " is valid, but key is not trusted";
  }

  private static Date parseDate(String str) throws Exception {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(str);
  }

  private static List<String> list(String first, String[] rest) {
    List<String> all = new ArrayList<>();
    all.add(first);
    all.addAll(Arrays.asList(rest));
    return all;
  }
}
