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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
      // TODO(ekempin,aliceks): Find a way not to use this config parameter here. Ideally,
      // completely factor out SSH from this class.
      @SshEnabled boolean sshEnabled) {
    this.accounts = accounts;
    this.accountsUpdate = accountsUpdate;
    this.seq = seq;
    this.sshKeyCache = sshKeyCache;
    this.authorizedKeys = authorizedKeys;
    this.sshEnabled = sshEnabled;
  }

  @Override
  public MoreAccountOperations account(Account.Id accountId) {
    return new MoreAccountOperationsImpl(accountId);
  }

  @Override
  public TestAccountCreation.Builder newAccount() {
    return TestAccountCreation.builder(this::createAccount);
  }

  private TestAccount createAccount(TestAccountCreation accountCreation) throws Exception {
    AccountsUpdate.AccountUpdater accountUpdater =
        (account, updateBuilder) ->
            fillBuilder(updateBuilder, accountCreation, account.getAccount().getId());
    AccountState createdAccount = createAccount(accountUpdater);

    TestAccount.Builder builder = toTestAccount(createdAccount);
    Optional<String> userName = createdAccount.getUserName();
    if (sshEnabled && userName.isPresent()) {
      addSshKeyPair(builder, createdAccount.getAccount(), userName.get());
    }
    return builder.build();
  }

  private AccountState createAccount(AccountsUpdate.AccountUpdater accountUpdater)
      throws OrmException, IOException, ConfigInvalidException {
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    return accountsUpdate.insert("Create Test Account", accountId, accountUpdater);
  }

  private static void fillBuilder(
      InternalAccountUpdate.Builder builder,
      TestAccountCreation accountCreation,
      Account.Id accountId) {
    accountCreation.fullname().ifPresent(builder::setFullName);
    accountCreation.preferredEmail().ifPresent(e -> setPreferredEmail(builder, accountId, e));
    String httpPassword = accountCreation.httpPassword().orElse(null);
    accountCreation.username().ifPresent(u -> setUsername(builder, accountId, u, httpPassword));
    accountCreation.status().ifPresent(builder::setStatus);
  }

  private void addSshKeyPair(TestAccount.Builder builder, Account account, String username)
      throws Exception {
    KeyPair sshKey = genSshKey();
    authorizedKeys.addKey(account.getId(), publicKey(sshKey, account.getPreferredEmail()));
    sshKeyCache.evict(username);
    builder.sshKeyPair(sshKey);
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

  private static TestAccount.Builder toTestAccount(AccountState accountState) {
    Account createdAccount = accountState.getAccount();
    return TestAccount.builder()
        .accountId(createdAccount.getId())
        .preferredEmail(Optional.ofNullable(createdAccount.getPreferredEmail()))
        .fullname(Optional.ofNullable(createdAccount.getFullName()))
        .username(accountState.getUserName());
  }

  private static InternalAccountUpdate.Builder setPreferredEmail(
      InternalAccountUpdate.Builder builder, Account.Id accountId, String preferredEmail) {
    return builder
        .setPreferredEmail(preferredEmail)
        .addExternalId(ExternalId.createEmail(accountId, preferredEmail));
  }

  private static InternalAccountUpdate.Builder setUsername(
      InternalAccountUpdate.Builder builder,
      Account.Id accountId,
      String username,
      String httpPassword) {
    return builder.addExternalId(ExternalId.createUsername(username, accountId, httpPassword));
  }

  private class MoreAccountOperationsImpl implements MoreAccountOperations {
    private final Account.Id accountId;

    MoreAccountOperationsImpl(Account.Id accountId) {
      this.accountId = accountId;
    }

    @Override
    public boolean exists() throws Exception {
      return accounts.get(accountId).isPresent();
    }

    @Override
    public TestAccount get() throws Exception {
      AccountState account =
          accounts
              .get(accountId)
              .orElseThrow(
                  () -> new IllegalStateException("Tried to get non-existing test account"));
      return toTestAccount(account).build();
    }

    @Override
    public TestAccountUpdate.Builder forUpdate() {
      return TestAccountUpdate.builder(this::updateAccount);
    }

    private TestAccount updateAccount(TestAccountUpdate accountUpdate)
        throws OrmException, IOException, ConfigInvalidException {
      AccountsUpdate.AccountUpdater accountUpdater =
          (account, updateBuilder) -> fillBuilder(updateBuilder, accountUpdate, accountId);
      Optional<AccountState> updatedAccount = updateAccount(accountUpdater);
      checkState(updatedAccount.isPresent(), "Tried to update non-existing test account");
      return toTestAccount(updatedAccount.get()).build();
    }

    private Optional<AccountState> updateAccount(AccountsUpdate.AccountUpdater accountUpdater)
        throws OrmException, IOException, ConfigInvalidException {
      return accountsUpdate.update("Update Test Account", accountId, accountUpdater);
    }

    private void fillBuilder(
        InternalAccountUpdate.Builder builder,
        TestAccountUpdate accountUpdate,
        Account.Id accountId) {
      accountUpdate.fullname().ifPresent(builder::setFullName);
      accountUpdate.preferredEmail().ifPresent(e -> setPreferredEmail(builder, accountId, e));
      String httpPassword = accountUpdate.httpPassword().orElse(null);
      accountUpdate.username().ifPresent(u -> setUsername(builder, accountId, u, httpPassword));
      accountUpdate.status().ifPresent(builder::setStatus);
    }
  }
}
