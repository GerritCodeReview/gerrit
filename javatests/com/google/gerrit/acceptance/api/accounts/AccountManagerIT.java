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

import static com.google.common.truth.OptionalSubject.optionals;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
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
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;
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
    Account.Id accountId = Account.id(seq.nextAccountId());
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
    Account.Id accountId = Account.id(seq.nextAccountId());
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
    Account.Id accountId = Account.id(seq.nextAccountId());
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
    Account.Id accountId = Account.id(seq.nextAccountId());
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
    assertThat(accountState.get().account().preferredEmail()).isEqualTo(newEmail);
  }

  @Test
  public void authenticateWithUsernameAndUpdateDisplayName() throws Exception {
    String username = "foo";
    String email = "foo@example.com";
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u ->
            u.setFullName("Initial Name")
                .setPreferredEmail(email)
                .addExternalId(ExternalId.createWithEmail(gerritExtIdKey, accountId, email)));

    AuthRequest who = AuthRequest.forUser(username);
    String newName = "Updated Name";
    who.setDisplayName(newName);
    AuthResult authResult = accountManager.authenticate(who);
    assertAuthResultForExistingAccount(authResult, accountId, gerritExtIdKey);

    Optional<AccountState> accountState = accounts.get(accountId);
    assertThat(accountState).isPresent();
    assertThat(accountState.get().account().fullName()).isEqualTo(newName);
  }

  @Test
  public void cannotAuthenticateWithOrphanedExtId() throws Exception {
    String username = "foo";
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    assertNoSuchExternalIds(gerritExtIdKey);

    // Create orphaned SCHEME_GERRIT external ID.
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId gerritExtId = ExternalId.create(gerritExtIdKey, accountId);
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = extIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(gerritExtId);
      extIdNotes.commit(md);
    }

    AuthRequest who = AuthRequest.forUser(username);
    AccountException thrown =
        assertThrows(AccountException.class, () -> accountManager.authenticate(who));
    assertThat(thrown).hasMessageThat().contains("Authentication error, account not found");
  }

  @Test
  public void cannotAuthenticateWithInactiveAccount() throws Exception {
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.setActive(false).addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    AccountException thrown =
        assertThrows(AccountException.class, () -> accountManager.authenticate(who));
    assertThat(thrown).hasMessageThat().contains("Authentication error, account inactive");
  }

  @Test
  public void cannotActivateAccountOnAuthenticationWhenAutoUpdateAccountActiveStatusIsDisabled()
      throws Exception {
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.setActive(false).addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    who.setActive(true);
    who.setAuthProvidesAccountActiveStatus(true);
    AccountException thrown =
        assertThrows(AccountException.class, () -> accountManager.authenticate(who));
    assertThat(thrown).hasMessageThat().contains("Authentication error, account inactive");
  }

  @Test
  @GerritConfig(name = "auth.autoUpdateAccountActiveStatus", value = "true")
  public void activateAccountOnAuthenticationWhenAutoUpdateAccountActiveStatusIsEnabled()
      throws Exception {
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
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
    assertThat(accountState.get().account().isActive()).isTrue();
  }

  @Test
  public void cannotDeactivateAccountOnAuthenticationWhenAutoUpdateAccountActiveStatusIsDisabled()
      throws Exception {
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
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
    assertThat(accountState.get().account().isActive()).isTrue();
  }

  @Test
  @GerritConfig(name = "auth.autoUpdateAccountActiveStatus", value = "true")
  public void deactivateAccountOnAuthenticationWhenAutoUpdateAccountActiveStatusIsEnabled()
      throws Exception {
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    AuthRequest who = AuthRequest.forUser(username);
    who.setActive(false);
    who.setAuthProvidesAccountActiveStatus(true);
    AccountException thrown =
        assertThrows(AccountException.class, () -> accountManager.authenticate(who));
    assertThat(thrown).hasMessageThat().isEqualTo("Authentication error, account inactive");

    Optional<AccountState> accountState = accounts.get(accountId);
    assertThat(accountState).isPresent();
    assertThat(accountState.get().account().isActive()).isFalse();
  }

  @Test
  public void cannotAuthenticateNewAccountWithEmailThatIsAlreadyUsed() throws Exception {
    String email = "foo@example.com";

    // Create an account with an SCHEME_EXTERNAL external ID that occupies the email.
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.createWithEmail(externalExtIdKey, accountId, email)));

    // Try to authenticate with this email to create a new account with a SCHEME_MAILTO external ID.
    // Expect that this fails because the email is already assigned to the other account.
    AuthRequest who = AuthRequest.forEmail(email);
    AccountException thrown =
        assertThrows(AccountException.class, () -> accountManager.authenticate(who));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Email 'foo@example.com' in use by another account");
  }

  @Test
  public void cannotAuthenticateNewAccountWithUsernameAndEmailThatIsAlreadyUsed() throws Exception {
    String email = "foo@example.com";

    // Create an account with an SCHEME_EXTERNAL external ID that occupies the email.
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.createWithEmail(externalExtIdKey, accountId, email)));

    // Try to authenticate with a new username and claim the same email.
    // Expect that this fails because the email is already assigned to the other account.
    AuthRequest who = AuthRequest.forUser("bar");
    who.setEmailAddress(email);
    AccountException thrown =
        assertThrows(AccountException.class, () -> accountManager.authenticate(who));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Email 'foo@example.com' in use by another account");
  }

  @Test
  public void cannotUpdateToEmailThatIsAlreadyUsed() throws Exception {
    String email = "foo@example.com";
    String newEmail = "bar@example.com";

    // Create an account with a SCHEME_GERRIT external ID and an email.
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u ->
            u.setPreferredEmail(email)
                .addExternalId(ExternalId.createWithEmail(gerritExtIdKey, accountId, email)));

    // Create another account with an SCHEME_EXTERNAL external ID that occupies the new email.
    Account.Id accountId2 = Account.id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, "bar");
    accountsUpdate.insert(
        "Create Test Account",
        accountId2,
        u -> u.addExternalId(ExternalId.createWithEmail(externalExtIdKey, accountId2, newEmail)));

    // Try to authenticate and update the email for the first account.
    // Expect that this fails because the new email is already assigned to the other account.
    AuthRequest who = AuthRequest.forUser(username);
    who.setEmailAddress(newEmail);
    AccountException thrown =
        assertThrows(AccountException.class, () -> accountManager.authenticate(who));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Email 'bar@example.com' in use by another account");

    // Verify that the email in the external ID was not updated.
    Optional<ExternalId> gerritExtId = externalIds.get(gerritExtIdKey);
    assertThat(gerritExtId).isPresent();
    assertThat(gerritExtId.get().email()).isEqualTo(email);

    // Verify that the preferred email was not updated.
    Optional<AccountState> accountState = accounts.get(accountId);
    assertThat(accountState).isPresent();
    assertThat(accountState.get().account().preferredEmail()).isEqualTo(email);
  }

  @Test
  public void canFlagExistingExternalIdMailAsPreferred() throws Exception {
    String email = "foo@example.com";

    // Create an account with a SCHEME_GERRIT external ID
    String username = "foo";
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username);
    Account.Id accountId = Account.id(seq.nextAccountId());
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.create(gerritExtIdKey, accountId)));

    // Add the additional mail external ID with SCHEME_EMAIL
    accountManager.link(accountId, AuthRequest.forEmail(email));

    // Try to authenticate and update the email for the account.
    // Expect that this to succeed because even if the email already exist
    // it is associated to the same account-id and thus is not really
    // a duplicate but simply a promotion of external id to preferred email.
    AuthRequest who = AuthRequest.forUser(username);
    who.setEmailAddress(email);
    AuthResult authResult = accountManager.authenticate(who);

    // Verify that no new accounts have been created
    assertThat(authResult.isNew()).isFalse();

    // Verify that the account external ids with scheme 'mailto:' contains the email
    AccountState account = accounts.get(authResult.getAccountId()).get();
    ImmutableSet<ExternalId> accountExternalIds = account.externalIds();
    assertThat(accountExternalIds).isNotEmpty();
    Set<String> emails = ExternalId.getEmails(accountExternalIds).collect(toSet());
    assertThat(emails).contains(email);

    // Verify the preferred email
    assertThat(account.account().preferredEmail()).isEqualTo(email);
  }

  @Test
  public void linkNewExternalId() throws Exception {
    // Create an account with a SCHEME_GERRIT external ID and no email
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
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
    Account.Id accountId = Account.id(seq.nextAccountId());
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
    Account.Id accountId1 = Account.id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey1 = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username1);
    accountsUpdate.insert(
        "Create Test Account",
        accountId1,
        u -> u.addExternalId(ExternalId.create(externalExtIdKey1, accountId1)));

    // Create another account with a SCHEME_EXTERNAL external ID
    String username2 = "bar";
    Account.Id accountId2 = Account.id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey2 = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username2);
    accountsUpdate.insert(
        "Create Test Account",
        accountId2,
        u -> u.addExternalId(ExternalId.create(externalExtIdKey2, accountId2)));

    // Try to link external ID of the first account to the second account.
    // Expect that this fails because the external ID is already assigned to the first account.
    AuthRequest who = AuthRequest.forExternalUser(username1);
    AccountException thrown =
        assertThrows(AccountException.class, () -> accountManager.link(accountId2, who));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Identity 'external:foo' in use by another account");
  }

  @Test
  public void cannotLinkEmailThatIsAlreadyUsed() throws Exception {
    String email = "foo@example.com";

    // Create an account with an SCHEME_EXTERNAL external ID that occupies the email.
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.createWithEmail(externalExtIdKey, accountId, email)));

    // Create another account with a SCHEME_GERRIT external ID and no email
    String username2 = "foo";
    Account.Id accountId2 = Account.id(seq.nextAccountId());
    ExternalId.Key gerritExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_GERRIT, username2);
    accountsUpdate.insert(
        "Create Test Account",
        accountId2,
        u -> u.addExternalId(ExternalId.create(gerritExtIdKey, accountId2)));

    // Try to link the email to the second account (via a new MAILTO external ID) and expect that
    // this fails because the email is already assigned to the first account.
    AuthRequest who = AuthRequest.forEmail(email);
    AccountException thrown =
        assertThrows(AccountException.class, () -> accountManager.link(accountId2, who));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Email 'foo@example.com' in use by another account");
  }

  @Test
  public void allowLinkingExistingExternalIdEmailAsPreferred() throws Exception {
    String email = "foo@example.com";

    // Create an account with an SCHEME_EXTERNAL external ID that occupies the email.
    String username = "foo";
    Account.Id accountId = Account.id(seq.nextAccountId());
    ExternalId.Key externalExtIdKey = ExternalId.Key.create(ExternalId.SCHEME_EXTERNAL, username);
    accountsUpdate.insert(
        "Create Test Account",
        accountId,
        u -> u.addExternalId(ExternalId.createWithEmail(externalExtIdKey, accountId, email)));

    AuthRequest who = AuthRequest.forEmail(email);
    AuthResult result = accountManager.link(accountId, who);
    assertThat(result.isNew()).isFalse();
    assertThat(result.getAccountId().get()).isEqualTo(accountId.get());
  }

  private void assertNoSuchExternalIds(ExternalId.Key... extIdKeys) throws Exception {
    for (ExternalId.Key extIdKey : extIdKeys) {
      assertWithMessage(extIdKey.get())
          .about(optionals())
          .that(externalIds.get(extIdKey))
          .isEmpty();
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
    assertWithMessage(extIdKey.get()).about(optionals()).that(extId).isPresent();
    if (expectedAccountId != null) {
      assertWithMessage("account ID of " + extIdKey.get())
          .that(extId.get().accountId())
          .isEqualTo(expectedAccountId);
    }
    assertWithMessage("email of " + extIdKey.get())
        .that(extId.get().email())
        .isEqualTo(expectedEmail);
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
