// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.request;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.CurrentUser.PropertyKey;
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

@UseSsh
public class RequestScopeOperationsImplTest extends AbstractDaemonTest {
  private static final AtomicInteger changeCounter = new AtomicInteger();

  @Inject private AccountOperations accountOperations;
  @Inject private Provider<CurrentUser> userProvider;
  @Inject private RequestScopeOperationsImpl requestScopeOperations;
  @Inject private Sequences sequences;

  @Test
  public void setApiUserToExistingUserById() throws Exception {
    fastCheckCurrentUser(admin.id());
    AcceptanceTestRequestScope.Context oldCtx = requestScopeOperations.setApiUser(user.id());
    assertThat(oldCtx.getUser().getAccountId()).isEqualTo(admin.id());
    checkCurrentUser(user.id());
  }

  @Test
  public void setApiUserToExistingUserByTestAccount() throws Exception {
    fastCheckCurrentUser(admin.id());
    TestAccount testAccount =
        accountOperations.account(accountOperations.newAccount().username("tester").create()).get();
    AcceptanceTestRequestScope.Context oldCtx = requestScopeOperations.setApiUser(testAccount);
    assertThat(oldCtx.getUser().getAccountId()).isEqualTo(admin.id());
    checkCurrentUser(testAccount.accountId());
  }

  @Test
  public void setApiUserToNonExistingUser() throws Exception {
    fastCheckCurrentUser(admin.id());
    try {
      requestScopeOperations.setApiUser(Account.id(sequences.nextAccountId()));
      assert_().fail("expected RuntimeException");
    } catch (RuntimeException e) {
      // Expected.
    }
    checkCurrentUser(admin.id());
  }

  @Test
  public void resetCurrentApiUserClearsCachedState() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    PropertyKey<String> key = PropertyKey.create();
    atrScope.get().getUser().put(key, "foo");
    assertThat(atrScope.get().getUser().get(key)).hasValue("foo");

    AcceptanceTestRequestScope.Context oldCtx = requestScopeOperations.resetCurrentApiUser();
    checkCurrentUser(user.id());
    assertThat(atrScope.get().getUser().get(key)).isEmpty();
    assertThat(oldCtx.getUser().get(key)).hasValue("foo");
  }

  @Test
  public void setApiUserAnonymousSetsAnonymousUser() throws Exception {
    fastCheckCurrentUser(admin.id());
    requestScopeOperations.setApiUserAnonymous();
    assertThat(userProvider.get()).isInstanceOf(AnonymousUser.class);
  }

  private void fastCheckCurrentUser(Account.Id expected) {
    // Check current user quickly, since the full check requires creating changes and is quite slow.
    assertThat(userProvider.get().isIdentifiedUser())
        .named("user from provider is an IdentifiedUser")
        .isTrue();
    assertThat(userProvider.get().getAccountId()).named("user from provider").isEqualTo(expected);
  }

  private void checkCurrentUser(Account.Id expected) throws Exception {
    // Test all supported ways that an acceptance test might query the active user.
    fastCheckCurrentUser(expected);
    assertThat(gApi.accounts().self().get()._accountId)
        .named("user from GerritApi")
        .isEqualTo(expected.get());
    AcceptanceTestRequestScope.Context ctx = atrScope.get();
    assertThat(ctx.getUser().isIdentifiedUser())
        .named("user from AcceptanceTestRequestScope.Context is an IdentifiedUser")
        .isTrue();
    assertThat(ctx.getUser().getAccountId())
        .named("user from AcceptanceTestRequestScope.Context")
        .isEqualTo(expected);
    checkSshUser(expected);
  }

  private void checkSshUser(Account.Id expected) throws Exception {
    // No "gerrit whoami" command, so the simplest way to check who the user is over SSH is to query
    // for owner:self.
    ChangeInput cin = new ChangeInput();
    cin.project = project.get();
    cin.branch = "master";
    cin.subject = "Test change " + changeCounter.incrementAndGet();
    String changeId = gApi.changes().create(cin).get().changeId;
    assertThat(gApi.changes().id(changeId).get().owner._accountId).isEqualTo(expected.get());
    String queryResults =
        atrScope.get().getSession().exec("gerrit query owner:self change:" + changeId);
    assertThat(findDistinct(queryResults, "I[0-9a-f]{40}"))
        .named("Change-Ids in query results:\n%s", queryResults)
        .containsExactly(changeId);
  }

  private static ImmutableSet<String> findDistinct(String input, String pattern) {
    Matcher m = Pattern.compile(pattern).matcher(input);
    ImmutableSet.Builder<String> b = ImmutableSet.builder();
    while (m.find()) {
      b.add(m.group(0));
    }
    return b.build();
  }
}
