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

package com.google.gerrit.acceptance.server.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestAccount;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountResolver.Result;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class AccountResolverIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setEnum("accounts", null, "visibility", AccountVisibility.SAME_GROUP);
    return cfg;
  }

  @Inject @ServerInitiated private Provider<AccountsUpdate> accountsUpdateProvider;
  @Inject private AccountOperations accountOperations;
  @Inject private AccountResolver accountResolver;
  @Inject private Provider<CurrentUser> self;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private Sequences sequences;

  @Test
  public void bySelf() throws Exception {
    assertThat(resolve("Self")).isEmpty();
    accountOperations.newAccount().fullname("self").create();

    Result result = resolveAsResult("self");
    assertThat(result.asIdSet()).containsExactly(admin.id());
    assertThat(result.isSelf()).isTrue();
    assertThat(result.asUniqueUser()).isSameInstanceAs(self.get());

    result = resolveAsResult("me");
    assertThat(result.asIdSet()).containsExactly(admin.id());
    assertThat(result.isSelf()).isTrue();
    assertThat(result.asUniqueUser()).isSameInstanceAs(self.get());

    requestScopeOperations.setApiUserAnonymous();
    checkBySelfFails();

    requestScopeOperations.setApiUserInternal();
    checkBySelfFails();
  }

  private void checkBySelfFails() throws Exception {
    Result result = resolveAsResult("self");
    assertThat(result.asIdSet()).isEmpty();
    assertThat(result.isSelf()).isTrue();
    try {
      result.asUnique();
      assert_().fail("expected UnresolvableAccountException");
    } catch (UnresolvableAccountException e) {
      assertThat(e).hasMessageThat().isEqualTo("Resolving account 'self' requires login");
      assertThat(e.isSelf()).isTrue();
    }

    result = resolveAsResult("me");
    assertThat(result.asIdSet()).isEmpty();
    assertThat(result.isSelf()).isTrue();
    try {
      result.asUnique();
      assert_().fail("expected UnresolvableAccountException");
    } catch (UnresolvableAccountException e) {
      assertThat(e).hasMessageThat().isEqualTo("Resolving account 'me' requires login");
      assertThat(e.isSelf()).isTrue();
    }
  }

  @Test
  public void bySelfInactive() throws Exception {
    gApi.accounts().id(user.id().get()).setActive(false);

    requestScopeOperations.setApiUser(user.id());
    assertThat(gApi.accounts().id("self").getActive()).isFalse();

    Result result = resolveAsResult("self");
    assertThat(result.asIdSet()).containsExactly(user.id());
    assertThat(result.isSelf()).isTrue();
    assertThat(result.asUniqueUser()).isSameInstanceAs(self.get());
  }

  @Test
  public void byExactAccountId() throws Exception {
    Account.Id existingId = accountOperations.newAccount().create();
    Account.Id idWithExistingIdAsFullname =
        accountOperations.newAccount().fullname(existingId.toString()).create();

    Account.Id nonexistentId = Account.id(sequences.nextAccountId());
    accountOperations.newAccount().fullname(nonexistentId.toString()).create();

    assertThat(resolve(existingId)).containsExactly(existingId);
    assertThat(resolve(nonexistentId)).isEmpty();

    assertThat(resolveByNameOrEmail(existingId)).containsExactly(idWithExistingIdAsFullname);
  }

  @Test
  public void byParenthesizedAccountId() throws Exception {
    Account.Id existingId = accountOperations.newAccount().fullname("Test User").create();
    accountOperations.newAccount().fullname(existingId.toString()).create();

    Account.Id nonexistentId = Account.id(sequences.nextAccountId());
    accountOperations.newAccount().fullname("Any Name (" + nonexistentId + ")").create();
    accountOperations.newAccount().fullname(nonexistentId.toString()).create();

    String existingInput = "Any Name (" + existingId + ")";
    assertThat(resolve(existingInput)).containsExactly(existingId);
    assertThat(resolve("Any Name (" + nonexistentId + ")")).isEmpty();

    assertThat(resolveByNameOrEmail(existingInput)).isEmpty();
  }

  @Test
  public void byUsername() throws Exception {
    String existingUsername = "myusername";
    Account.Id idWithUsername = accountOperations.newAccount().username(existingUsername).create();
    Account.Id idWithExistingUsernameAsFullname =
        accountOperations.newAccount().fullname(existingUsername).create();

    String nonexistentUsername = "anotherusername";
    Account.Id idWithFullname = accountOperations.newAccount().fullname("anotherusername").create();

    assertThat(resolve(existingUsername)).containsExactly(idWithUsername);

    // Doesn't short-circuit just because the input looks like a valid username.
    assertThat(ExternalId.isValidUsername(nonexistentUsername)).isTrue();
    assertThat(resolve(nonexistentUsername)).containsExactly(idWithFullname);

    assertThat(resolveByNameOrEmail(existingUsername))
        .containsExactly(idWithExistingUsernameAsFullname);
  }

  @Test
  public void byNameAndEmail() throws Exception {
    String email = name("user@example.com");
    Account.Id idWithEmail = accountOperations.newAccount().preferredEmail(email).create();
    accountOperations.newAccount().fullname(email).create();

    String input = "First Last <" + email + ">";
    assertThat(resolve(input)).containsExactly(idWithEmail);
    assertThat(resolveByNameOrEmail(input)).containsExactly(idWithEmail);
  }

  @Test
  public void byNameAndEmailPrefersAccountsWithMatchingFullName() throws Exception {
    String email = name("user@example.com");
    Account.Id id1 = accountOperations.newAccount().fullname("Aaa Bbb").create();
    setPreferredEmailBypassingUniquenessCheck(id1, email);
    Account.Id id2 = accountOperations.newAccount().fullname("Ccc Ddd").create();
    setPreferredEmailBypassingUniquenessCheck(id2, email);

    String input = "First Last <" + email + ">";
    assertThat(resolve(input)).containsExactly(id1, id2);
    assertThat(resolveByNameOrEmail(input)).containsExactly(id1, id2);

    Account.Id id3 = accountOperations.newAccount().fullname("First Last").create();
    setPreferredEmailBypassingUniquenessCheck(id3, email);
    assertThat(resolve(input)).containsExactly(id3);
    assertThat(resolveByNameOrEmail(input)).containsExactly(id3);

    Account.Id id4 = accountOperations.newAccount().fullname("First Last").create();
    setPreferredEmailBypassingUniquenessCheck(id4, email);
    assertThat(resolve(input)).containsExactly(id3, id4);
    assertThat(resolveByNameOrEmail(input)).containsExactly(id3, id4);
  }

  @Test
  public void byEmail() throws Exception {
    String email = name("user@example.com");
    Account.Id idWithEmail = accountOperations.newAccount().preferredEmail(email).create();
    accountOperations.newAccount().fullname(email).create();

    assertThat(resolve(email)).containsExactly(idWithEmail);
    assertThat(resolveByNameOrEmail(email)).containsExactly(idWithEmail);
  }

  // Can't test for ByRealm because DefaultRealm with the default (OPENID) auth type doesn't support
  // email expansion, so anything that would return a non-null value from DefaultRealm#lookup would
  // just be an email address, handled by other tests. This could be avoided if we inject some sort
  // of custom test realm instance, but the ugliness is not worth it for this small bit of test
  // coverage.

  @Test
  public void byFullName() throws Exception {
    Account.Id id1 = accountOperations.newAccount().fullname("Somebodys Name").create();
    accountOperations.newAccount().fullname("A totally different name").create();
    String input = "Somebodys name";
    assertThat(resolve(input)).containsExactly(id1);
    assertThat(resolveByNameOrEmail(input)).containsExactly(id1);
  }

  @Test
  public void byDefaultSearch() throws Exception {
    Account.Id id1 = accountOperations.newAccount().fullname("John Doe").create();
    Account.Id id2 = accountOperations.newAccount().fullname("Jane Doe").create();
    assertThat(resolve("doe")).containsExactly(id1, id2);
    assertThat(resolveByNameOrEmail("doe")).containsExactly(id1, id2);
  }

  @Test
  public void onlyExactIdReturnsInactiveAccounts() throws Exception {
    TestAccount account =
        accountOperations
            .account(
                accountOperations
                    .newAccount()
                    .fullname("Inactiveuser Name")
                    .preferredEmail("inactiveuser@example.com")
                    .username("inactiveusername")
                    .create())
            .get();
    Account.Id id = account.accountId();
    String nameEmail = account.fullname().get() + " <" + account.preferredEmail().get() + ">";
    ImmutableList<String> inputs =
        ImmutableList.of(
            account.fullname().get() + " (" + account.accountId() + ")",
            account.fullname().get(),
            account.preferredEmail().get(),
            nameEmail,
            Splitter.on(' ').splitToList(account.fullname().get()).get(0));

    assertThat(resolve(account.accountId())).containsExactly(id);
    for (String input : inputs) {
      assertThat(resolve(input)).named("results for %s (active)", input).containsExactly(id);
    }

    gApi.accounts().id(id.get()).setActive(false);
    assertThat(resolve(account.accountId())).containsExactly(id);
    for (String input : inputs) {
      Result result = accountResolver.resolve(input);
      assertThat(result.asIdSet()).named("results for %s (inactive)", input).isEmpty();
      try {
        result.asUnique();
        assert_().fail("expected UnresolvableAccountException");
      } catch (UnresolvableAccountException e) {
        assertThat(e)
            .hasMessageThat()
            .isEqualTo(
                "Account '"
                    + input
                    + "' only matches inactive accounts. To use an inactive account, retry"
                    + " with one of the following exact account IDs:\n"
                    + id
                    + ": "
                    + nameEmail);
      }
      assertThat(resolveByNameOrEmail(input))
          .named("results by name or email for %s (inactive)", input)
          .isEmpty();
    }
  }

  @Test
  public void filterVisibility() throws Exception {
    Account.Id id1 =
        accountOperations
            .newAccount()
            .fullname("John Doe")
            .preferredEmail("johndoe@example.com")
            .create();
    Account.Id id2 =
        accountOperations
            .newAccount()
            .fullname("Jane Doe")
            .preferredEmail("janedoe@example.com")
            .create();

    // Admin can see all accounts. Use a variety of searches, including with/without
    // callerMayAssumeCandidatesAreVisible.
    assertThat(resolve(id1)).containsExactly(id1);
    assertThat(resolve("John Doe")).containsExactly(id1);
    assertThat(resolve("johndoe@example.com")).containsExactly(id1);
    assertThat(resolve(id2)).containsExactly(id2);
    assertThat(resolve("Jane Doe")).containsExactly(id2);
    assertThat(resolve("janedoe@example.com")).containsExactly(id2);
    assertThat(resolve("doe")).containsExactly(id1, id2);

    // id2 can't see id1, and vice versa.
    requestScopeOperations.setApiUser(id1);
    assertThat(resolve(id1)).containsExactly(id1);
    assertThat(resolve("John Doe")).containsExactly(id1);
    assertThat(resolve("johndoe@example.com")).containsExactly(id1);
    assertThat(resolve(id2)).isEmpty();
    assertThat(resolve("Jane Doe")).isEmpty();
    assertThat(resolve("janedoe@example.com")).isEmpty();
    assertThat(resolve("doe")).containsExactly(id1);

    requestScopeOperations.setApiUser(id2);
    assertThat(resolve(id1)).isEmpty();
    assertThat(resolve("John Doe")).isEmpty();
    assertThat(resolve("johndoe@example.com")).isEmpty();
    assertThat(resolve(id2)).containsExactly(id2);
    assertThat(resolve("Jane Doe")).containsExactly(id2);
    assertThat(resolve("janedoe@example.com")).containsExactly(id2);
    assertThat(resolve("doe")).containsExactly(id2);
  }

  private ImmutableSet<Account.Id> resolve(Object input) throws Exception {
    return resolveAsResult(input).asIdSet();
  }

  private Result resolveAsResult(Object input) throws Exception {
    return accountResolver.resolve(input.toString());
  }

  @SuppressWarnings("deprecation")
  private ImmutableSet<Account.Id> resolveByNameOrEmail(Object input) throws Exception {
    return accountResolver.resolveByNameOrEmail(input.toString()).asIdSet();
  }

  private void setPreferredEmailBypassingUniquenessCheck(Account.Id id, String email)
      throws Exception {
    Optional<AccountState> result =
        accountsUpdateProvider
            .get()
            .update("Force set preferred email", id, (s, u) -> u.setPreferredEmail(email));
    assertThat(result.map(a -> a.getAccount().getPreferredEmail())).hasValue(email);
  }
}
