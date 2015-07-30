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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
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
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/** Unit tests for {@link GerritPublicKeyChecker}. */
public class GerritPublicKeyCheckerTest {
  @Inject
  private AccountCache accountCache;

  @Inject
  private AccountManager accountManager;

  @Inject
  private GerritPublicKeyChecker checker;

  @Inject
  private IdentifiedUser.GenericFactory userFactory;

  @Inject
  private InMemoryDatabase schemaFactory;

  @Inject
  private SchemaCreator schemaCreator;

  @Inject
  private ThreadLocalRequestContext requestContext;

  private LifecycleManager lifecycle;
  private ReviewDb db;
  private Account.Id userId;
  private IdentifiedUser user;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    injector.injectMembers(this);
    lifecycle.start();

    db = schemaFactory.open();
    schemaCreator.create(db);
    userId =
        accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    Account userAccount = db.accounts().get(userId);
    // Note: does not match any key in TestKey.
    userAccount.setPreferredEmail("user@example.com");
    db.accounts().update(ImmutableList.of(userAccount));
    user = reloadUser();

    requestContext.setContext(new RequestContext() {
      @Override
      public CurrentUser getCurrentUser() {
        return user;
      }

      @Override
      public Provider<ReviewDb> getReviewDbProvider() {
        return Providers.of(db);
      }
    });
  }

  private IdentifiedUser reloadUser() {
    accountCache.evict(userId);
    user = userFactory.create(Providers.of(db), userId);
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
    TestKey key = TestKey.key5();
    assertProblems(
        TestKey.key5(),
        "Key must contain a valid certification for one of the following "
          + "identities:\n"
          + "  gerrit:user\n"
          + "  username:user");

    addExternalId("test", "test", "test5@example.com");
    assertNoProblems(key);
  }

  @Test
  public void defaultGpgCertificationDoesNotMatchEmail() throws Exception {
    addExternalId("test", "test", "nobody@example.com");
    assertProblems(
        TestKey.key5(),
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
    assertNoProblems(TestKey.key5());
  }

  @Test
  public void manualCertificationDoesNotExternalId() throws Exception {
    addExternalId("foo", "otherId", null);
    assertProblems(
        TestKey.key5(),
        "Key must contain a valid certification for one of the following "
          + "identities:\n"
          + "  foo:otherId\n"
          + "  gerrit:user\n"
          + "  username:user");
  }

  @Test
  public void noExternalIds() throws Exception {
    db.accountExternalIds().delete(
        db.accountExternalIds().byAccount(user.getAccountId()));
    reloadUser();
    assertProblems(
        TestKey.key5(),
        "No identities found for user; check"
          + " http://test/#settings/web-identities");
  }

  private void assertNoProblems(TestKey key) throws Exception {
    assertThat(checker.check(key.getPublicKey()).getProblems()).isEmpty();
  }

  private void assertProblems(TestKey key, String... expected)
      throws Exception {
    checkArgument(expected.length > 0);
    assertThat(checker.check(key.getPublicKey()).getProblems())
        .containsExactly((Object[]) expected)
        .inOrder();
  }

  private void addExternalId(String scheme, String id, String email)
      throws Exception {
    AccountExternalId extId = new AccountExternalId(user.getAccountId(),
        new AccountExternalId.Key(scheme, id));
    if (email != null) {
      extId.setEmailAddress(email);
    }
    db.accountExternalIds().insert(Collections.singleton(extId));
    reloadUser();
  }
}
