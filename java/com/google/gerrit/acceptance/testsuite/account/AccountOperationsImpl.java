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

package com.google.gerrit.acceptance.testsuite.account;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountDelta;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AccountsUpdate.ConfigureDeltaFromState;
import com.google.gerrit.server.account.AccountsUpdate.ConfigureStatelessDelta;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * The implementation of {@code AccountOperations}.
 *
 * <p>There is only one implementation of {@code AccountOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
public class AccountOperationsImpl implements AccountOperations {
  private final Accounts accounts;
  private final AccountsUpdate accountsUpdate;
  private final Sequences seq;
  private final ExternalIdFactory externalIdFactory;

  @Inject
  public AccountOperationsImpl(
      Accounts accounts,
      @ServerInitiated AccountsUpdate accountsUpdate,
      Sequences seq,
      ExternalIdFactory externalIdFactory) {
    this.accounts = accounts;
    this.accountsUpdate = accountsUpdate;
    this.seq = seq;
    this.externalIdFactory = externalIdFactory;
  }

  @Override
  public PerAccountOperations account(Account.Id accountId) {
    return new PerAccountOperationsImpl(accountId);
  }

  @Override
  public TestAccountCreation.Builder newAccount() {
    return TestAccountCreation.builder(
        this::createAccount, externalIdFactory.arePasswordsAllowed());
  }

  protected Account.Id createAccount(TestAccountCreation testAccountCreation) throws Exception {
    Account.Id accountId = Account.id(seq.nextAccountId());
    ConfigureStatelessDelta accountCreation =
        deltaBuilder -> initAccountDelta(deltaBuilder, testAccountCreation, accountId);
    AccountState createdAccount =
        accountsUpdate.insert("Create Test Account", accountId, accountCreation);
    return createdAccount.account().id();
  }

  private void initAccountDelta(
      AccountDelta.Builder builder, TestAccountCreation accountCreation, Account.Id accountId) {
    accountCreation.fullname().ifPresent(builder::setFullName);
    accountCreation.preferredEmail().ifPresent(e -> setPreferredEmail(builder, accountId, e));
    String httpPassword = accountCreation.httpPassword().orElse(null);
    accountCreation.username().ifPresent(u -> setUsername(builder, accountId, u, httpPassword));
    accountCreation.status().ifPresent(builder::setStatus);
    accountCreation.active().ifPresent(builder::setActive);
    accountCreation
        .secondaryEmails()
        .forEach(
            secondaryEmail ->
                builder.addExternalId(externalIdFactory.createEmail(accountId, secondaryEmail)));
  }

  private void setPreferredEmail(
      AccountDelta.Builder builder, Account.Id accountId, String preferredEmail) {
    builder
        .setPreferredEmail(preferredEmail)
        .addExternalId(externalIdFactory.createEmail(accountId, preferredEmail));
  }

  private void setUsername(
      AccountDelta.Builder builder, Account.Id accountId, String username, String httpPassword) {
    builder.addExternalId(externalIdFactory.createUsername(username, accountId, httpPassword));
  }

  private class PerAccountOperationsImpl implements PerAccountOperations {
    private final Account.Id accountId;

    PerAccountOperationsImpl(Account.Id accountId) {
      this.accountId = accountId;
    }

    @Override
    public boolean exists() {
      return getAccountState(accountId).isPresent();
    }

    @Override
    public TestAccount get() {
      AccountState account =
          getAccountState(accountId)
              .orElseThrow(
                  () -> new IllegalStateException("Tried to get non-existing test account"));
      return toTestAccount(account);
    }

    private Optional<AccountState> getAccountState(Account.Id accountId) {
      try {
        return accounts.get(accountId);
      } catch (IOException | ConfigInvalidException e) {
        throw new IllegalStateException(e);
      }
    }

    private TestAccount toTestAccount(AccountState accountState) {
      Account account = accountState.account();
      return TestAccount.builder()
          .accountId(account.id())
          .preferredEmail(Optional.ofNullable(account.preferredEmail()))
          .fullname(Optional.ofNullable(account.fullName()))
          .username(accountState.userName())
          .active(accountState.account().isActive())
          .emails(ExternalId.getEmails(accountState.externalIds()).collect(toImmutableSet()))
          .build();
    }

    @Override
    public TestAccountUpdate.Builder forUpdate() {
      return TestAccountUpdate.builder(this::updateAccount);
    }

    private void updateAccount(TestAccountUpdate accountUpdate)
        throws IOException, ConfigInvalidException {
      ConfigureDeltaFromState configureDeltaFromState =
          (accountState, deltaBuilder) -> fillBuilder(deltaBuilder, accountUpdate, accountState);
      Optional<AccountState> updatedAccount = updateAccount(configureDeltaFromState);
      checkState(updatedAccount.isPresent(), "Tried to update non-existing test account");
    }

    @CanIgnoreReturnValue
    private Optional<AccountState> updateAccount(ConfigureDeltaFromState configureDeltaFromState)
        throws IOException, ConfigInvalidException {
      return accountsUpdate.update("Update Test Account", accountId, configureDeltaFromState);
    }

    private void fillBuilder(
        AccountDelta.Builder builder, TestAccountUpdate accountUpdate, AccountState accountState) {
      accountUpdate.fullname().ifPresent(builder::setFullName);
      accountUpdate.preferredEmail().ifPresent(e -> setPreferredEmail(builder, accountId, e));
      String httpPassword = accountUpdate.httpPassword().orElse(null);
      accountUpdate.username().ifPresent(u -> setUsername(builder, accountId, u, httpPassword));
      accountUpdate.status().ifPresent(builder::setStatus);
      accountUpdate.active().ifPresent(builder::setActive);

      ImmutableSet<String> secondaryEmails = getSecondaryEmails(accountUpdate, accountState);
      ImmutableSet<String> newSecondaryEmails =
          ImmutableSet.copyOf(accountUpdate.secondaryEmailsModification().apply(secondaryEmails));
      if (!secondaryEmails.equals(newSecondaryEmails)) {
        setSecondaryEmails(builder, accountUpdate, accountState, newSecondaryEmails);
      }
    }

    private ImmutableSet<String> getSecondaryEmails(
        TestAccountUpdate accountUpdate, AccountState accountState) {
      ImmutableSet<String> allEmails =
          ExternalId.getEmails(accountState.externalIds()).collect(toImmutableSet());
      if (accountUpdate.preferredEmail().isPresent()) {
        return ImmutableSet.copyOf(
            Sets.difference(allEmails, ImmutableSet.of(accountUpdate.preferredEmail().get())));
      } else if (accountState.account().preferredEmail() != null) {
        return ImmutableSet.copyOf(
            Sets.difference(allEmails, ImmutableSet.of(accountState.account().preferredEmail())));
      }
      return allEmails;
    }

    private void setSecondaryEmails(
        AccountDelta.Builder builder,
        TestAccountUpdate accountUpdate,
        AccountState accountState,
        ImmutableSet<String> newSecondaryEmails) {
      // delete all external IDs of SCHEME_MAILTO scheme, then add back SCHEME_MAILTO external IDs
      // for the new secondary emails and the preferred email
      builder.deleteExternalIds(
          accountState.externalIds().stream()
              .filter(e -> e.isScheme(ExternalId.SCHEME_MAILTO))
              .collect(toImmutableSet()));
      builder.addExternalIds(
          newSecondaryEmails.stream()
              .map(secondaryEmail -> externalIdFactory.createEmail(accountId, secondaryEmail))
              .collect(toImmutableSet()));
      if (accountUpdate.preferredEmail().isPresent()) {
        builder.addExternalId(
            externalIdFactory.createEmail(accountId, accountUpdate.preferredEmail().get()));
      } else if (accountState.account().preferredEmail() != null) {
        builder.addExternalId(
            externalIdFactory.createEmail(accountId, accountState.account().preferredEmail()));
      }
    }

    @Override
    public TestAccountInvalidation.Builder forInvalidation() {
      return TestAccountInvalidation.builder(this::invalidateAccount);
    }

    private void invalidateAccount(TestAccountInvalidation testAccountInvalidation)
        throws Exception {
      Optional<AccountState> accountState = getAccountState(accountId);
      checkState(accountState.isPresent(), "Tried to invalidate a non-existing test account");

      if (testAccountInvalidation.preferredEmailWithoutExternalId().isPresent()) {
        updateAccount(
            (account, deltaBuilder) ->
                deltaBuilder.setPreferredEmail(
                    testAccountInvalidation.preferredEmailWithoutExternalId().get()));
      }
    }
  }
}
