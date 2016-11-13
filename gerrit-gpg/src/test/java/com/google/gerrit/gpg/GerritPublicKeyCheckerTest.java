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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.gpg.GerritPublicKeyChecker.toExtIdKey;
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithSecondUserId;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyA;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyB;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyC;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyD;
import static com.google.gerrit.gpg.testutil.TestTrustKeys.keyE;
import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_MAILTO;
import static org.eclipse.jgit.lib.RefUpdate.Result.FAST_FORWARD;
import static org.eclipse.jgit.lib.RefUpdate.Result.FORCED;
import static org.eclipse.jgit.lib.RefUpdate.Result.NEW;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.gerrit.extensions.common.GpgKeyInfo.Status;
import com.google.gerrit.gpg.testutil.TestKey;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.gerrit.testutil.TestNotesMigration;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link GerritPublicKeyChecker}. */
public class GerritPublicKeyCheckerTest {
  @Inject private AccountCache accountCache;

  @Inject private AccountManager accountManager;

  @Inject private GerritPublicKeyChecker.Factory checkerFactory;

  @Inject private IdentifiedUser.GenericFactory userFactory;

  @Inject private InMemoryDatabase schemaFactory;

  @Inject private SchemaCreator schemaCreator;

  @Inject private ThreadLocalRequestContext requestContext;

  private LifecycleManager lifecycle;
  private ReviewDb db;
  private Account.Id userId;
  private IdentifiedUser user;
  private Repository storeRepo;
  private PublicKeyStore store;

  @Before
  public void setUpInjector() throws Exception {
    Config cfg = InMemoryModule.newDefaultConfig();
    cfg.setInt("receive", null, "maxTrustDepth", 2);
    cfg.setStringList(
        "receive",
        null,
        "trustedKey",
        ImmutableList.of(
            Fingerprint.toString(keyB().getPublicKey().getFingerprint()),
            Fingerprint.toString(keyD().getPublicKey().getFingerprint())));
    Injector injector = Guice.createInjector(new InMemoryModule(cfg, new TestNotesMigration()));

    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    injector.injectMembers(this);
    lifecycle.start();

    db = schemaFactory.open();
    schemaCreator.create(db);
    userId = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    Account userAccount = db.accounts().get(userId);
    // Note: does not match any key in TestKeys.
    userAccount.setPreferredEmail("user@example.com");
    db.accounts().update(ImmutableList.of(userAccount));
    user = reloadUser();

    requestContext.setContext(
        new RequestContext() {
          @Override
          public CurrentUser getUser() {
            return user;
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return Providers.of(db);
          }
        });

    storeRepo = new InMemoryRepository(new DfsRepositoryDescription("repo"));
    store = new PublicKeyStore(storeRepo);
  }

  @After
  public void tearDown() throws Exception {
    store.close();
    storeRepo.close();
  }

  private IdentifiedUser addUser(String name) throws Exception {
    AuthRequest req = AuthRequest.forUser(name);
    Account.Id id = accountManager.authenticate(req).getAccountId();
    return userFactory.create(id);
  }

  private IdentifiedUser reloadUser() throws IOException {
    accountCache.evict(userId);
    user = userFactory.create(userId);
    return user;
  }

  @After
  public void tearDownInjector() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    if (db != null) {
      db.close();
    }
    InMemoryDatabase.drop(schemaFactory);
  }

  @Test
  public void defaultGpgCertificationMatchesEmail() throws Exception {
    TestKey key = validKeyWithSecondUserId();
    PublicKeyChecker checker = checkerFactory.create(user, store).disableTrust();
    assertProblems(
        checker.check(key.getPublicKey()),
        Status.BAD,
        "Key must contain a valid certification for one of the following "
            + "identities:\n"
            + "  gerrit:user\n"
            + "  username:user");

    addExternalId("test", "test", "test5@example.com");
    checker = checkerFactory.create(user, store).disableTrust();
    assertNoProblems(checker.check(key.getPublicKey()));
  }

  @Test
  public void defaultGpgCertificationDoesNotMatchEmail() throws Exception {
    addExternalId("test", "test", "nobody@example.com");
    PublicKeyChecker checker = checkerFactory.create(user, store).disableTrust();
    assertProblems(
        checker.check(validKeyWithSecondUserId().getPublicKey()),
        Status.BAD,
        "Key must contain a valid certification for one of the following "
            + "identities:\n"
            + "  gerrit:user\n"
            + "  nobody@example.com\n"
            + "  test:test\n"
            + "  username:user");
  }

  @Test
  public void manualCertificationMatchesExternalId() throws Exception {
    addExternalId("foo", "myId", null);
    PublicKeyChecker checker = checkerFactory.create(user, store).disableTrust();
    assertNoProblems(checker.check(validKeyWithSecondUserId().getPublicKey()));
  }

  @Test
  public void manualCertificationDoesNotMatchExternalId() throws Exception {
    addExternalId("foo", "otherId", null);
    PublicKeyChecker checker = checkerFactory.create(user, store).disableTrust();
    assertProblems(
        checker.check(validKeyWithSecondUserId().getPublicKey()),
        Status.BAD,
        "Key must contain a valid certification for one of the following "
            + "identities:\n"
            + "  foo:otherId\n"
            + "  gerrit:user\n"
            + "  username:user");
  }

  @Test
  public void noExternalIds() throws Exception {
    db.accountExternalIds().delete(db.accountExternalIds().byAccount(user.getAccountId()));
    reloadUser();

    TestKey key = validKeyWithSecondUserId();
    PublicKeyChecker checker = checkerFactory.create(user, store).disableTrust();
    assertProblems(
        checker.check(key.getPublicKey()),
        Status.BAD,
        "No identities found for user; check" + " http://test/#/settings/web-identities");

    checker = checkerFactory.create().setStore(store).disableTrust();
    assertProblems(
        checker.check(key.getPublicKey()), Status.BAD, "Key is not associated with any users");

    db.accountExternalIds()
        .insert(
            Collections.singleton(
                new AccountExternalId(user.getAccountId(), toExtIdKey(key.getPublicKey()))));
    reloadUser();
    assertProblems(checker.check(key.getPublicKey()), Status.BAD, "No identities found for user");
  }

  @Test
  public void checkValidTrustChainAndCorrectExternalIds() throws Exception {
    // A---Bx
    //  \
    //   \---C---D
    //        \
    //         \---Ex
    //
    // The server ultimately trusts B and D.
    // D and E trust C to be a valid introducer of depth 2.
    IdentifiedUser userB = addUser("userB");
    TestKey keyA = add(keyA(), user);
    TestKey keyB = add(keyB(), userB);
    add(keyC(), addUser("userC"));
    add(keyD(), addUser("userD"));
    add(keyE(), addUser("userE"));

    // Checker for A, checking A.
    PublicKeyChecker checkerA = checkerFactory.create(user, store);
    assertNoProblems(checkerA.check(keyA.getPublicKey()));

    // Checker for B, checking B. Trust chain and IDs are correct, so the only
    // problem is with the key itself.
    PublicKeyChecker checkerB = checkerFactory.create(userB, store);
    assertProblems(checkerB.check(keyB.getPublicKey()), Status.BAD, "Key is expired");
  }

  @Test
  public void checkWithValidKeyButWrongExpectedUserInChecker() throws Exception {
    // A---Bx
    //  \
    //   \---C---D
    //        \
    //         \---Ex
    //
    // The server ultimately trusts B and D.
    // D and E trust C to be a valid introducer of depth 2.
    IdentifiedUser userB = addUser("userB");
    TestKey keyA = add(keyA(), user);
    TestKey keyB = add(keyB(), userB);
    add(keyC(), addUser("userC"));
    add(keyD(), addUser("userD"));
    add(keyE(), addUser("userE"));

    // Checker for A, checking B.
    PublicKeyChecker checkerA = checkerFactory.create(user, store);
    assertProblems(
        checkerA.check(keyB.getPublicKey()),
        Status.BAD,
        "Key is expired",
        "Key must contain a valid certification for one of the following"
            + " identities:\n"
            + "  gerrit:user\n"
            + "  mailto:testa@example.com\n"
            + "  testa@example.com\n"
            + "  username:user");

    // Checker for B, checking A.
    PublicKeyChecker checkerB = checkerFactory.create(userB, store);
    assertProblems(
        checkerB.check(keyA.getPublicKey()),
        Status.BAD,
        "Key must contain a valid certification for one of the following"
            + " identities:\n"
            + "  gerrit:userB\n"
            + "  mailto:testb@example.com\n"
            + "  testb@example.com\n"
            + "  username:userB");
  }

  @Test
  public void checkTrustChainWithExpiredKey() throws Exception {
    // A---Bx
    //
    // The server ultimately trusts B.
    TestKey keyA = add(keyA(), user);
    TestKey keyB = add(keyB(), addUser("userB"));

    PublicKeyChecker checker = checkerFactory.create(user, store);
    assertProblems(
        checker.check(keyA.getPublicKey()),
        Status.OK,
        "No path to a trusted key",
        "Certification by "
            + keyToString(keyB.getPublicKey())
            + " is valid, but key is not trusted",
        "Key D24FE467 used for certification is not in store");
  }

  @Test
  public void checkTrustChainUsingCheckerWithoutExpectedKey() throws Exception {
    // A---Bx
    //  \
    //   \---C---D
    //        \
    //         \---Ex
    //
    // The server ultimately trusts B and D.
    // D and E trust C to be a valid introducer of depth 2.
    TestKey keyA = add(keyA(), user);
    TestKey keyB = add(keyB(), addUser("userB"));
    TestKey keyC = add(keyC(), addUser("userC"));
    TestKey keyD = add(keyD(), addUser("userD"));
    TestKey keyE = add(keyE(), addUser("userE"));

    // This checker can check any key, so the only problems come from issues
    // with the keys themselves, not having invalid user IDs.
    PublicKeyChecker checker = checkerFactory.create().setStore(store);
    assertNoProblems(checker.check(keyA.getPublicKey()));
    assertProblems(checker.check(keyB.getPublicKey()), Status.BAD, "Key is expired");
    assertNoProblems(checker.check(keyC.getPublicKey()));
    assertNoProblems(checker.check(keyD.getPublicKey()));
    assertProblems(
        checker.check(keyE.getPublicKey()),
        Status.BAD,
        "Key is expired",
        "No path to a trusted key");
  }

  @Test
  public void keyLaterInTrustChainMissingUserId() throws Exception {
    // A---Bx
    //  \
    //   \---C
    //
    // The server ultimately trusts B.
    // C signed A's key but is not in the store.
    TestKey keyA = add(keyA(), user);

    PGPPublicKeyRing keyRingB = keyB().getPublicKeyRing();
    PGPPublicKey keyB = keyRingB.getPublicKey();
    keyB = PGPPublicKey.removeCertification(keyB, (String) keyB.getUserIDs().next());
    keyRingB = PGPPublicKeyRing.insertPublicKey(keyRingB, keyB);
    add(keyRingB, addUser("userB"));

    PublicKeyChecker checkerA = checkerFactory.create(user, store);
    assertProblems(
        checkerA.check(keyA.getPublicKey()),
        Status.OK,
        "No path to a trusted key",
        "Certification by " + keyToString(keyB) + " is valid, but key is not trusted",
        "Key D24FE467 used for certification is not in store");
  }

  private void add(PGPPublicKeyRing kr, IdentifiedUser user) throws Exception {
    Account.Id id = user.getAccountId();
    List<AccountExternalId> newExtIds = new ArrayList<>(2);
    newExtIds.add(new AccountExternalId(id, toExtIdKey(kr.getPublicKey())));

    @SuppressWarnings("unchecked")
    String userId = (String) Iterators.getOnlyElement(kr.getPublicKey().getUserIDs(), null);
    if (userId != null) {
      String email = PushCertificateIdent.parse(userId).getEmailAddress();
      assertThat(email).contains("@");
      AccountExternalId mailto =
          new AccountExternalId(id, new AccountExternalId.Key(SCHEME_MAILTO, email));
      mailto.setEmailAddress(email);
      newExtIds.add(mailto);
    }

    store.add(kr);
    PersonIdent ident = new PersonIdent("A U Thor", "author@example.com");
    CommitBuilder cb = new CommitBuilder();
    cb.setAuthor(ident);
    cb.setCommitter(ident);
    assertThat(store.save(cb)).isAnyOf(NEW, FAST_FORWARD, FORCED);

    db.accountExternalIds().insert(newExtIds);
    accountCache.evict(user.getAccountId());
  }

  private TestKey add(TestKey k, IdentifiedUser user) throws Exception {
    add(k.getPublicKeyRing(), user);
    return k;
  }

  private void assertProblems(
      CheckResult result, Status expectedStatus, String first, String... rest) throws Exception {
    List<String> expectedProblems = new ArrayList<>();
    expectedProblems.add(first);
    expectedProblems.addAll(Arrays.asList(rest));
    assertThat(result.getStatus()).isEqualTo(expectedStatus);
    assertThat(result.getProblems()).containsExactlyElementsIn(expectedProblems).inOrder();
  }

  private void assertNoProblems(CheckResult result) {
    assertThat(result.getStatus()).isEqualTo(Status.TRUSTED);
    assertThat(result.getProblems()).isEmpty();
  }

  private void addExternalId(String scheme, String id, String email) throws Exception {
    AccountExternalId extId =
        new AccountExternalId(user.getAccountId(), new AccountExternalId.Key(scheme, id));
    if (email != null) {
      extId.setEmailAddress(email);
    }
    db.accountExternalIds().insert(Collections.singleton(extId));
    reloadUser();
  }
}
