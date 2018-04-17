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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.InternalAccountUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class AccountOperationsImpl implements AccountOperations {
  private final Accounts accounts;
  private final AccountsUpdate accountsUpdate;
  private final Sequences seq;

  @Inject
  public AccountOperationsImpl(
      Accounts accounts, @ServerInitiated AccountsUpdate accountsUpdate, Sequences seq) {
    this.accounts = accounts;
    this.accountsUpdate = accountsUpdate;
    this.seq = seq;
  }

  @Override
  public boolean exists(Account.Id accountId) throws Exception {
    return accounts.get(accountId).isPresent();
  }

  @Override
  public TestAccount get(Account.Id accountId) throws Exception {
    AccountState account =
        accounts
            .get(accountId)
            .orElseThrow(() -> new IllegalStateException("Tried to get non-existing test account"));
    return toTestAccount(account);
  }

  @Override
  public TestAccount create(Consumer<TestAccountUpdate.Builder> creation) throws Exception {
    AccountState createdAccount = createAccount(creation);

    return toTestAccount(createdAccount);
  }

  private AccountState createAccount(Consumer<TestAccountUpdate.Builder> accountUpdate)
      throws OrmException, IOException, ConfigInvalidException {
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    return accountsUpdate.insert(
        "Create Test Account",
        accountId,
        (account, updateBuilder) ->
            applyUpdate(account, updateBuilder, toAccountUpdate(accountUpdate)));
  }

  @Override
  public TestAccount update(Account.Id accountId, Consumer<TestAccountUpdate.Builder> update)
      throws Exception {
    Optional<AccountState> updatedAccount = updateAccount(accountId, update);
    checkState(updatedAccount.isPresent(), "Tried to update non-existing test account");
    return toTestAccount(updatedAccount.get());
  }

  private Optional<AccountState> updateAccount(
      Account.Id accountId, Consumer<TestAccountUpdate.Builder> accountUpdate)
      throws OrmException, IOException, ConfigInvalidException {
    return accountsUpdate.update(
        "Update Test Account",
        accountId,
        (account, updateBuilder) ->
            applyUpdate(account, updateBuilder, toAccountUpdate(accountUpdate)));
  }

  private static TestAccountUpdate toAccountUpdate(
      Consumer<TestAccountUpdate.Builder> accountUpdate) {
    TestAccountUpdate.Builder accountUpdateBuilder = TestAccountUpdate.builder();
    accountUpdate.accept(accountUpdateBuilder);
    return accountUpdateBuilder.build();
  }

  private static void applyUpdate(
      AccountState accountState,
      InternalAccountUpdate.Builder builder,
      TestAccountUpdate accountUpdate) {
    Account.Id accountId = accountState.getAccount().getId();

    accountUpdate.fullname().ifPresent(builder::setFullName);
    accountUpdate
        .preferredEmail()
        .ifPresent(
            e -> builder.setPreferredEmail(e).addExternalId(ExternalId.createEmail(accountId, e)));
    String httpPassword = accountUpdate.httpPassword().orElse(null);
    accountUpdate
        .username()
        .ifPresent(
            u -> builder.addExternalId(ExternalId.createUsername(u, accountId, httpPassword)));
  }

  private static TestAccount toTestAccount(AccountState accountState) {
    Account createdAccount = accountState.getAccount();
    return TestAccount.builder()
        .accountId(createdAccount.getId())
        .preferredEmail(Optional.ofNullable(createdAccount.getPreferredEmail()))
        .fullname(Optional.ofNullable(createdAccount.getFullName()))
        .username(accountState.getUserName())
        .build();
  }
}
