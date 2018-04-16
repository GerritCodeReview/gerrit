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
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.gerrit.acceptance.SshEnabled;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.InternalAccountUpdate;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.inject.Inject;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.function.Consumer;

public class AccountOperationsImpl implements AccountOperations {
  private final Accounts accounts;
  private final AccountsUpdate accountsUpdate;
  private final Sequences seq;
  private final SshKeyCache sshKeyCache;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final boolean sshEnabled;

  @Inject
  public AccountOperationsImpl(
      Accounts accounts,
      @ServerInitiated AccountsUpdate accountsUpdate,
      Sequences seq,
      SshKeyCache sshKeyCache,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      @SshEnabled boolean sshEnabled) {
    this.accounts = accounts;
    this.accountsUpdate = accountsUpdate;
    this.seq = seq;
    this.sshKeyCache = sshKeyCache;
    this.authorizedKeys = authorizedKeys;
    this.sshEnabled = sshEnabled;
  }

  @Override
  public boolean exists(Account.Id accountId) throws Exception {
    return accounts.get(accountId).isPresent();
  }

  @Override
  public TestAccount get(Account.Id accountId) throws Exception {
    return toTestAccount(
            accounts
                .get(accountId)
                .orElseThrow(
                    () -> new IllegalStateException("Tried to get non-existing test account")))
        .build();
  }

  @Override
  public TestAccount create(Consumer<TestAccountUpdate.Builder> accountUpdate) throws Exception {
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    AccountState createdAccountState =
        accountsUpdate.insert(
            "Create Test Account",
            accountId,
            (account, updateBuilder) ->
                applyUpdate(account, updateBuilder, toAccountUpdate(accountUpdate)));

    TestAccount.Builder builder = toTestAccount(createdAccountState);
    if (sshEnabled && createdAccountState.getUserName().isPresent()) {
      KeyPair sshKey = genSshKey();
      authorizedKeys.addKey(
          createdAccountState.getAccount().getId(),
          publicKey(sshKey, createdAccountState.getAccount().getPreferredEmail()));
      sshKeyCache.evict(createdAccountState.getUserName().get());
      builder.sshKeyPair(sshKey);
    }
    return builder.build();
  }

  @Override
  public TestAccount update(Account.Id accountId, Consumer<TestAccountUpdate.Builder> accountUpdate)
      throws Exception {
    Optional<AccountState> updatedAccountState =
        accountsUpdate.update(
            "Update Test Account",
            accountId,
            (account, updateBuilder) ->
                applyUpdate(account, updateBuilder, toAccountUpdate(accountUpdate)));
    checkState(updatedAccountState.isPresent(), "Tried to update non-existing test account");
    return toTestAccount(updatedAccountState.get()).build();
  }

  private TestAccountUpdate toAccountUpdate(Consumer<TestAccountUpdate.Builder> accountUpdate) {
    TestAccountUpdate.Builder accountUpdateBuilder = TestAccountUpdate.builder();
    accountUpdate.accept(accountUpdateBuilder);
    return accountUpdateBuilder.build();
  }

  private void applyUpdate(
      AccountState accountState,
      InternalAccountUpdate.Builder builder,
      TestAccountUpdate accountUpdate) {
    accountUpdate.fullname().ifPresent(n -> builder.setFullName(n));
    accountUpdate
        .preferredEmail()
        .ifPresent(
            e ->
                builder
                    .setPreferredEmail(e)
                    .addExternalId(ExternalId.createEmail(accountState.getAccount().getId(), e)));
    accountUpdate
        .username()
        .ifPresent(
            u ->
                builder.addExternalId(
                    ExternalId.createUsername(
                        u,
                        accountState.getAccount().getId(),
                        accountUpdate.httpPassword().orElse(null))));
  }

  private TestAccount.Builder toTestAccount(AccountState accountState) {
    Account createdAccount = accountState.getAccount();
    TestAccount.Builder testAccountBuilder =
        TestAccount.builder()
            .accountId(createdAccount.getId())
            .preferredEmail(createdAccount.getPreferredEmail())
            .fullname(createdAccount.getFullName());

    if (accountState.getUserName().isPresent()) {
      String username = accountState.getUserName().get();
      testAccountBuilder.username(username);
    }

    return testAccountBuilder;
  }

  private static KeyPair genSshKey() throws JSchException {
    JSch jsch = new JSch();
    return KeyPair.genKeyPair(jsch, KeyPair.RSA);
  }

  private static String publicKey(KeyPair sshKey, String comment)
      throws UnsupportedEncodingException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sshKey.writePublicKey(out, comment);
    return out.toString(US_ASCII.name()).trim();
  }
}
