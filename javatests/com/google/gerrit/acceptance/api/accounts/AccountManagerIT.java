// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class AccountManagerIT extends AbstractDaemonTest {
  @Inject private AccountManager accountManager;
  @Inject private ExternalIds externalIds;
  @Inject private Sequences seq;
  @Inject @ServerInitiated private AccountsUpdate accountsUpdate;
  @Inject private ExternalIdNotes.Factory extIdNotesFactory;

  @Test
  public void authenticateNewAccountWithEmail() throws Exception {
    String email = "foo@example.com";
    ExternalId.Key mailtoExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_MAILTO, email);
    assertNoSuchExternalIds(mailtoExtIdKey);

    AuthRequest who = AuthRequest.forEmail(email);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForNewAccount(authResult, mailtoExtIdKey);
    assertExternalId(mailtoExtIdKey, email);
  }

  @Test
  public void authenticateNewAccountWithUsername() throws Exception {
    String username = "foo";
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    ExternalId.Key usernameExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_USERNAME, username);
    assertNoSuchExternalIds(gerritExtIdKey, usernameExtIdKey);

    AuthRequest who = AuthRequest.forUser(username);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForNewAccount(authResult, gerritExtIdKey);
    assertExternalIdsWithoutEmail(gerritExtIdKey, usernameExtIdKey);
  }

  @Test
  public void authenticateNewAccountWithUsernameAndEmail() throws Exception {
    String username = "foo";
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    ExternalId.Key usernameExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_USERNAME, username);
    assertNoSuchExternalIds(gerritExtIdKey, usernameExtIdKey);

    AuthRequest who = AuthRequest.forUser(username);
    String email = "foo@example.com";
    who.setEmailAddress(email);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForNewAccount(authResult, gerritExtIdKey);
    assertExternalId(gerritExtIdKey, email);
    assertExternalIdsWithoutEmail(usernameExtIdKey);
  }

  @Test
  public void authenticateNewAccountWithExternalUser() throws Exception {
    String username = "foo";
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    ExternalId.Key usernameExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_USERNAME, username);
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    assertNoSuchExternalIds(externalExtIdKey, usernameExtIdKey, gerritExtIdKey);

    AuthRequest who = AuthRequest.forExternalUser(username);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForNewAccount(authResult, externalExtIdKey);
    assertExternalIdsWithoutEmail(externalExtIdKey, usernameExtIdKey);
    assertNoSuchExternalIds(gerritExtIdKey);
  }

  @Test
  public void authenticateNewAccountWithExternalUserAndEmail() throws Exception {
    String username = "foo";
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    ExternalId.Key usernameExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_USERNAME, username);
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    assertNoSuchExternalIds(externalExtIdKey, usernameExtIdKey, gerritExtIdKey);

    AuthRequest who = AuthRequest.forExternalUser(username);
    String email = "foo@example.com";
    who.setEmailAddress(email);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForNewAccount(authResult, externalExtIdKey);
    assertExternalId(externalExtIdKey, email);
    assertExternalIdsWithoutEmail(usernameExtIdKey);
    assertNoSuchExternalIds(gerritExtIdKey);
  }

  @Test
  public void authenticateWithEmail() throws Exception {
    String email = "foo@example.com";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key mailtoExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_MAILTO, email);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.create(mailtoExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forEmail(email);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForExistingAccount(authResult, accountId, mailtoExtIdKey);
  }

  @Test
  public void authenticateWithUsername() throws Exception {
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForExistingAccount(authResult, accountId, gerritExtIdKey);
  }

  @Test
  public void authenticateWithExternalUser() throws Exception {
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.create(externalExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forExternalUser(username);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForExistingAccount(authResult, accountId, externalExtIdKey);
  }

  @Test
  public void authenticateWithUsernameAndUpdateEmail() throws Exception {
    String username = "foo";
    String email = "foo@example.com";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u ->
            u.setPreferredEmail(email)
                .addExternalId(ExternalId.createWithEmail(gerritExtIdKey, accountId, email)));

    AuthRequest who = AuthRequest.forUser(username);
    String newEmail = "bar@example.com";
    who.setEmailAddress(newEmail);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForExistingAccount(authResult, accountId, gerritExtIdKey);

    Optional<ExternalId> gerritExtId = externalIds.get(gerritExtIdKey);
    assertThat(gerritExtId).isPresent();
    assertThat(gerritExtId.get().email()).isEqualTo(newEmail);

    Optional<AccountState> accountState = accounts.get(accountId);
    assertThat(accountState).isPresent();
    assertThat(accountState.get().getAccount().getPreferredEmail()).isEqualTo(newEmail);
  }

  @Test
  public void authenticateWhenUsernameExtIdAlreadyExists() throws Exception {
    String username = "foo";
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    ExternalId.Key usernameExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_USERNAME, username);
    assertNoSuchExternalIds(gerritExtIdKey, usernameExtIdKey);

    // Create account with SCHEME_USERNAME external ID, but no SCHEME_GERRIT external ID.
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.setFullName("Foo").addExternalId(ExternalId.create(usernameExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    AuthResult authResult = accountManager.authenticate(who);

    // Expect that the missing SCHEME_GERRIT external ID was created.
    assertAuthResultForExistingAccount(authResult, accountId, gerritExtIdKey);
    assertExternalIdsWithoutEmail(gerritExtIdKey, usernameExtIdKey);
  }

  @Test
  public void cannotAuthenticateWithOrphanedExtId() throws Exception {
    String username = "foo";
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    assertNoSuchExternalIds(gerritExtIdKey);

    // Create orphaned SCHEME_GERRIT external ID.
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId gerritExtId = ExternalId.create(gerritExtIdKey, accountId);
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = extIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(gerritExtId);
      extIdNotes.commit(md);
    }

    AuthRequest who = AuthRequest.forUser(username);
    exception.expect(AccountException.class);
    exception.expectMessage("Authentication error, account not found");
    accountManager.authenticate(who);
  }

  @Test
  public void cannotAuthenticateWithInactiveAccount() throws Exception {
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.setActive(false).addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    exception.expect(AccountException.class);
    exception.expectMessage("Authentication error, account inactive");
    accountManager.authenticate(who);
  }

  @Test
  public void cannotActivateAccountOnAuthenticationWhenAutoUpdateAccountActiveStatusIsDisabled()
      throws Exception {
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.setActive(false).addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    who.setActive(true);
    who.setAuthProvidesAccountActiveStatus(true);
    exception.expect(AccountException.class);
    exception.expectMessage("Authentication error, account inactive");
    accountManager.authenticate(who);
  }

  @Test
  @GerritConfig(name = "auth.autoUpdateAccountActiveStatus", value = "true")
  public void activateAccountOnAuthenticationWhenAutoUpdateAccountActiveStatusIsEnabled()
      throws Exception {
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.setActive(false).addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    who.setActive(true);
    who.setAuthProvidesAccountActiveStatus(true);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForExistingAccount(authResult, accountId, gerritExtIdKey);
    Optional<AccountState> accountState = accounts.get(accountId);
    assertThat(accountState).isPresent();
    assertThat(accountState.get().getAccount().isActive()).isTrue();
  }

  @Test
  public void cannotDeactivateAccountOnAuthenticationWhenAutoUpdateAccountActiveStatusIsDisabled()
      throws Exception {
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    who.setActive(false);
    who.setAuthProvidesAccountActiveStatus(true);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForExistingAccount(authResult, accountId, gerritExtIdKey);
    Optional<AccountState> accountState = accounts.get(accountId);
    assertThat(accountState).isPresent();
    assertThat(accountState.get().getAccount().isActive()).isTrue();
  }

  @Test
  @GerritConfig(name = "auth.autoUpdateAccountActiveStatus", value = "true")
  public void deactivateAccountOnAuthenticationWhenAutoUpdateAccountActiveStatusIsEnabled()
      throws Exception {
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    who.setActive(false);
    who.setAuthProvidesAccountActiveStatus(true);
    try {
      accountManager.authenticate(who);
      fail("Expected AccountException");
    } catch (AccountException e) {
      assertThat(e).hasMessageThat().isEqualTo("Authentication error, account inactive");
    }

    Optional<AccountState> accountState = accounts.get(accountId);
    assertThat(accountState).isPresent();
    assertThat(accountState.get().getAccount().isActive()).isFalse();
  }

  @Test
  public void cannotAuthenticateNewAccountWithEmailThatIsAlreadyUsed() throws Exception {
    String email = "foo@example.com";

    // Create an account with an SCHEME_EXTERNAL external ID that occupies the email.
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.createWithEmail(externalExtIdKey, accountId, email)));

    // Try to authenticate with this email to create a new account with a SCHEME_MAILTO external ID.
    // Expect that this fails because the email is already assigned to the other account.
    AuthRequest who = AuthRequest.forEmail(email);
    exception.expect(AccountException.class);
    exception.expectMessage("Email 'foo@example.com' in use by another account");
    accountManager.authenticate(who);
  }

  @Test
  public void cannotAuthenticateNewAccountWithUsernameAndEmailThatIsAlreadyUsed() throws Exception {
    String email = "foo@example.com";

    // Create an account with an SCHEME_EXTERNAL external ID that occupies the email.
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.createWithEmail(externalExtIdKey, accountId, email)));

    // Try to authenticate with a new username and claim the same email.
    // Expect that this fails because the email is already assigned to the other account.
    AuthRequest who = AuthRequest.forUser("bar");
    who.setEmailAddress(email);
    exception.expect(AccountException.class);
    exception.expectMessage("Email 'foo@example.com' in use by another account");
    accountManager.authenticate(who);
  }

  @Test
  public void cannotUpdateToEmailThatIsAlreadyUsed() throws Exception {
    String email = "foo@example.com";
    String newEmail = "bar@example.com";

    // Create an account with a SCHEME_GERRIT external ID and an email.
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u ->
            u.setPreferredEmail(email)
                .addExternalId(ExternalId.createWithEmail(gerritExtIdKey, accountId, email)));

    // Create another account with an SCHEME_EXTERNAL external ID that occupies the new email.
    Account.Id accountId2 = new Account.Id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, "bar");
    accountsUpdate.insert(
        "Create Test Account",
        accountId2,
        u -> u.addExternalId(ExternalId.createWithEmail(externalExtIdKey, accountId2, newEmail)));

    // Try to authenticate and update the email for the first account.
    // Expect that this fails because the new email is already assigned to the other account.
    AuthRequest who = AuthRequest.forUser(username);
    who.setEmailAddress(newEmail);
    try {
      accountManager.authenticate(who);
      fail("Expected AccountException");
    } catch (AccountException e) {
      assertThat(e).hasMessageThat().isEqualTo("Email 'bar@example.com' in use by another account");
    }

    // Verify that the email in the external ID was not updated.
    Optional<ExternalId> gerritExtId = externalIds.get(gerritExtIdKey);
    assertThat(gerritExtId).isPresent();
    assertThat(gerritExtId.get().email()).isEqualTo(email);

    // Verify that the preferred email was not updated.
    Optional<AccountState> accountState = accounts.get(accountId);
    assertThat(accountState).isPresent();
    assertThat(accountState.get().getAccount().getPreferredEmail()).isEqualTo(email);
  }

  @Test
  public void linkNewExternalId() throws Exception {
    // Create an account with a SCHEME_GERRIT external ID and no email
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    // Check that email is not used yet.
    String email = "foo@example.com";
    ExternalId.Key mailtoExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_MAILTO, email);
    assertNoSuchExternalIds(mailtoExtIdKey);

    // Link the email to the account.
    // Expect that a MAILTO external ID is created.
    AuthRequest who = AuthRequest.forEmail(email);
    AuthResult authResult = accountManager.link(accountId, who);
    assertAuthResultForExistingAccount(authResult, accountId, mailtoExtIdKey);
    assertExternalId(mailtoExtIdKey, accountId, email);
  }

  @Test
  public void updateExternalIdOnLink() throws Exception {
    // Create an account with a SCHEME_GERRIT external ID and no email
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u ->
            u.addExternalId(
                ExternalId.createWithEmail(externalExtIdKey, accountId, "old@example.com")));

    // Link the email to the existing SCHEME_EXTERNAL external ID, but with a new email.
    // Expect that the email of the existing external ID is updated.
    AuthRequest who = AuthRequest.forExternalUser(username);
    String newEmail = "new@example.com";
    who.setEmailAddress(newEmail);
    AuthResult authResult = accountManager.link(accountId, who);
    assertAuthResultForExistingAccount(authResult, accountId, externalExtIdKey);
    assertExternalId(externalExtIdKey, accountId, newEmail);
  }

  @Test
  public void cannotLinkExternalIdThatIsAlreadyUsed() throws Exception {
    // Create an account with a SCHEME_EXTERNAL external ID
    String username1 = "foo";
    Account.Id accountId1 = new Account.Id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey1 = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username1);
    accountsUpdate.insert(
        "Create Test Account",
        accountId1,
        u -> u.addExternalId(ExternalId.create(externalExtIdKey1, accountId1)));

    // Create another account with a SCHEME_EXTERNAL external ID
    String username2 = "bar";
    Account.Id accountId2 = new Account.Id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey2 = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username2);
    accountsUpdate.insert(
        "Create Test Account",
        accountId2,
        u -> u.addExternalId(ExternalId.create(externalExtIdKey2, accountId2)));

    // Try to link external ID of the first account to the second account.
    // Expect that this fails because the external ID is already assigned to the first account.
    AuthRequest who = AuthRequest.forExternalUser(username1);
    exception.expect(AccountException.class);
    exception.expectMessage("Identity 'external:foo' in use by another account");
    accountManager.link(accountId2, who);
  }

  @Test
  public void cannotLinkEmailThatIsAlreadyUsed() throws Exception {
    String email = "foo@example.com";

    // Create an account with an SCHEME_EXTERNAL external ID that occupies the email.
    String username = "foo";
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.createWithEmail(externalExtIdKey, accountId, email)));

    // Create another account with a SCHEME_GERRIT external ID and no email
    String username2 = "foo";
    Account.Id accountId2 = new Account.Id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username2);
    accountsUpdate.insert(
        "Create Test Account",
        accountId2,
        u -> u.addExternalId(ExternalId.create(gerritExtIdKey, accountId2)));

    // Try to link the email to the second account (via a new MAILTO external ID) and expect that
    // this fails because the email is already assigned to the first account.
    AuthRequest who = AuthRequest.forEmail(email);
    exception.expect(AccountException.class);
    exception.expectMessage("Email 'foo@example.com' in use by another account");
    accountManager.link(accountId, who);
  }

  private void assertNoSuchExternalIds(ExternalId.Key... extIdKeys) throws Exception {
    for (ExternalId.Key extIdKey : extIdKeys) {
      assertThat(externalIds.get(extIdKey)).named(extIdKey.get()).isEmpty();
    }
  }

  private void assertExternalIdsWithoutEmail(ExternalId.Key... extIdKeys) throws Exception {
    for (ExternalId.Key extIdKey : extIdKeys) {
      assertExternalId(extIdKey, null);
    }
  }

  private void assertExternalId(ExternalId.Key extIdKey, @Nullable String expectedEmail)
      throws Exception {
    assertExternalId(extIdKey, null, expectedEmail);
  }

  private void assertExternalId(
      ExternalId.Key extIdKey,
      @Nullable Account.Id expectedAccountId,
      @Nullable String expectedEmail)
      throws Exception {
    Optional<ExternalId> extId = externalIds.get(extIdKey);
    assertThat(extId).named(extIdKey.get()).isPresent();
    if (expectedAccountId != null) {
      assertThat(extId.get().accountId())
          .named("account ID of " + extIdKey.get())
          .isEqualTo(expectedAccountId);
    }
    assertThat(extId.get().email()).named("email of " + extIdKey.get()).isEqualTo(expectedEmail);
  }

  private void assertAuthResultForNewAccount(
      AuthResult authResult, ExternalId.Key expectedExtIdKey) {
    assertThat(authResult.getAccountId()).isNotNull();
    assertThat(authResult.getExternalId()).isEqualTo(expectedExtIdKey);
    assertThat(authResult.isNew()).isTrue();
  }

  private void assertAuthResultForExistingAccount(
      AuthResult authResult, Account.Id expectedAccountId, ExternalId.Key expectedExtIdKey) {
    assertThat(authResult.getAccountId()).isEqualTo(expectedAccountId);
    assertThat(authResult.getExternalId()).isEqualTo(expectedExtIdKey);
    assertThat(authResult.isNew()).isFalse();
  }
}
