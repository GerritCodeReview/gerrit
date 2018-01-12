// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Runnables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIdNotes.ExternalIdNotesLoader;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.index.change.ReindexAfterRefUpdate;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * Updates accounts.
 *
 * <p>The account updates are written to NoteDb.
 *
 * <p>In NoteDb accounts are represented as user branches in the All-Users repository. Optionally a
 * user branch can contain a 'account.config' file that stores account properties, such as full
 * name, preferred email, status and the active flag. The timestamp of the first commit on a user
 * branch denotes the registration date. The initial commit on the user branch may be empty (since
 * having an 'account.config' is optional). See {@link AccountConfig} for details of the
 * 'account.config' file format.
 *
 * <p>On updating accounts the accounts are evicted from the account cache and thus reindexed. The
 * eviction from the account cache is done by the {@link ReindexAfterRefUpdate} class which receives
 * the event about updating the user branch that is triggered by this class.
 */
@Singleton
public class AccountsUpdate {
  /**
   * Updater for an account.
   *
   * <p>Allows to read the current state of an account and to prepare updates to it.
   */
  @FunctionalInterface
  public static interface AccountUpdater {
    /**
     * Prepare updates to an account.
     *
     * <p>Use the provided account only to read the current state of the account. Don't do updates
     * to the account. For updates use the provided account update builder.
     *
     * @param accountState the account that is being updated
     * @param update account update builder
     */
    void update(AccountState accountState, InternalAccountUpdate.Builder update);

    public static AccountUpdater join(List<AccountUpdater> updaters) {
      return (a, u) -> updaters.stream().forEach(updater -> updater.update(a, u));
    }

    public static AccountUpdater joinConsumers(
        List<Consumer<InternalAccountUpdate.Builder>> consumers) {
      return join(Lists.transform(consumers, AccountUpdater::fromConsumer));
    }

    static AccountUpdater fromConsumer(Consumer<InternalAccountUpdate.Builder> consumer) {
      return (a, u) -> consumer.accept(u);
    }
  }

  /**
   * Factory to create an AccountsUpdate instance for updating accounts by the Gerrit server.
   *
   * <p>The Gerrit server identity will be used as author and committer for all commits that update
   * the accounts.
   */
  @Singleton
  public static class Server {
    private final GitRepositoryManager repoManager;
    private final GitReferenceUpdated gitRefUpdated;
    private final AllUsersName allUsersName;
    private final ExternalIds externalIds;
    private final Provider<PersonIdent> serverIdentProvider;
    private final Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory;
    private final RetryHelper retryHelper;
    private final ExternalIdNotes.Factory extIdNotesFactory;

    @Inject
    public Server(
        GitRepositoryManager repoManager,
        GitReferenceUpdated gitRefUpdated,
        AllUsersName allUsersName,
        ExternalIds externalIds,
        @GerritPersonIdent Provider<PersonIdent> serverIdentProvider,
        Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
        RetryHelper retryHelper,
        ExternalIdNotes.Factory extIdNotesFactory) {
      this.repoManager = repoManager;
      this.gitRefUpdated = gitRefUpdated;
      this.allUsersName = allUsersName;
      this.externalIds = externalIds;
      this.serverIdentProvider = serverIdentProvider;
      this.metaDataUpdateInternalFactory = metaDataUpdateInternalFactory;
      this.retryHelper = retryHelper;
      this.extIdNotesFactory = extIdNotesFactory;
    }

    public AccountsUpdate create() {
      PersonIdent serverIdent = serverIdentProvider.get();
      return new AccountsUpdate(
          repoManager,
          gitRefUpdated,
          null,
          allUsersName,
          externalIds,
          metaDataUpdateInternalFactory,
          retryHelper,
          extIdNotesFactory,
          serverIdent,
          serverIdent);
    }
  }

  /**
   * Factory to create an AccountsUpdate instance for updating accounts by the Gerrit server.
   *
   * <p>Using this class no reindex will be performed for the affected accounts and they will also
   * not be evicted from the account cache.
   *
   * <p>The Gerrit server identity will be used as author and committer for all commits that update
   * the accounts.
   */
  @Singleton
  public static class ServerNoReindex {
    private final GitRepositoryManager repoManager;
    private final GitReferenceUpdated gitRefUpdated;
    private final AllUsersName allUsersName;
    private final ExternalIds externalIds;
    private final Provider<PersonIdent> serverIdentProvider;
    private final Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory;
    private final RetryHelper retryHelper;
    private final ExternalIdNotes.FactoryNoReindex extIdNotesFactory;

    @Inject
    public ServerNoReindex(
        GitRepositoryManager repoManager,
        GitReferenceUpdated gitRefUpdated,
        AllUsersName allUsersName,
        ExternalIds externalIds,
        @GerritPersonIdent Provider<PersonIdent> serverIdentProvider,
        Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
        RetryHelper retryHelper,
        ExternalIdNotes.FactoryNoReindex extIdNotesFactory) {
      this.repoManager = repoManager;
      this.gitRefUpdated = gitRefUpdated;
      this.allUsersName = allUsersName;
      this.externalIds = externalIds;
      this.serverIdentProvider = serverIdentProvider;
      this.metaDataUpdateInternalFactory = metaDataUpdateInternalFactory;
      this.retryHelper = retryHelper;
      this.extIdNotesFactory = extIdNotesFactory;
    }

    public AccountsUpdate create() {
      PersonIdent serverIdent = serverIdentProvider.get();
      return new AccountsUpdate(
          repoManager,
          gitRefUpdated,
          null,
          allUsersName,
          externalIds,
          metaDataUpdateInternalFactory,
          retryHelper,
          extIdNotesFactory,
          serverIdent,
          serverIdent);
    }
  }

  /**
   * Factory to create an AccountsUpdate instance for updating accounts by the current user.
   *
   * <p>The identity of the current user will be used as author for all commits that update the
   * accounts. The Gerrit server identity will be used as committer.
   */
  @Singleton
  public static class User {
    private final GitRepositoryManager repoManager;
    private final GitReferenceUpdated gitRefUpdated;
    private final AllUsersName allUsersName;
    private final ExternalIds externalIds;
    private final Provider<PersonIdent> serverIdentProvider;
    private final Provider<IdentifiedUser> identifiedUser;
    private final Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory;
    private final RetryHelper retryHelper;
    private final ExternalIdNotes.Factory extIdNotesFactory;

    @Inject
    public User(
        GitRepositoryManager repoManager,
        GitReferenceUpdated gitRefUpdated,
        AllUsersName allUsersName,
        ExternalIds externalIds,
        @GerritPersonIdent Provider<PersonIdent> serverIdentProvider,
        Provider<IdentifiedUser> identifiedUser,
        Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
        RetryHelper retryHelper,
        ExternalIdNotes.Factory extIdNotesFactory) {
      this.repoManager = repoManager;
      this.gitRefUpdated = gitRefUpdated;
      this.allUsersName = allUsersName;
      this.externalIds = externalIds;
      this.serverIdentProvider = serverIdentProvider;
      this.identifiedUser = identifiedUser;
      this.metaDataUpdateInternalFactory = metaDataUpdateInternalFactory;
      this.retryHelper = retryHelper;
      this.extIdNotesFactory = extIdNotesFactory;
    }

    public AccountsUpdate create() {
      IdentifiedUser user = identifiedUser.get();
      PersonIdent serverIdent = serverIdentProvider.get();
      PersonIdent userIdent = createPersonIdent(serverIdent, user);
      return new AccountsUpdate(
          repoManager,
          gitRefUpdated,
          user,
          allUsersName,
          externalIds,
          metaDataUpdateInternalFactory,
          retryHelper,
          extIdNotesFactory,
          serverIdent,
          userIdent);
    }

    private PersonIdent createPersonIdent(PersonIdent ident, IdentifiedUser user) {
      return user.newCommitterIdent(ident.getWhen(), ident.getTimeZone());
    }
  }

  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  @Nullable private final IdentifiedUser currentUser;
  private final AllUsersName allUsersName;
  private final ExternalIds externalIds;
  private final Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory;
  private final RetryHelper retryHelper;
  private final ExternalIdNotesLoader extIdNotesLoader;
  private final PersonIdent committerIdent;
  private final PersonIdent authorIdent;

  // Invoked after reading the account config.
  private final Runnable afterReadRevision;

  // Invoked after updating the account but before committing the changes.
  private final Runnable beforeCommit;

  private AccountsUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      @Nullable IdentifiedUser currentUser,
      AllUsersName allUsersName,
      ExternalIds externalIds,
      Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      ExternalIdNotesLoader extIdNotesLoader,
      PersonIdent committerIdent,
      PersonIdent authorIdent) {
    this(
        repoManager,
        gitRefUpdated,
        currentUser,
        allUsersName,
        externalIds,
        metaDataUpdateInternalFactory,
        retryHelper,
        extIdNotesLoader,
        committerIdent,
        authorIdent,
        Runnables.doNothing(),
        Runnables.doNothing());
  }

  @VisibleForTesting
  public AccountsUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      @Nullable IdentifiedUser currentUser,
      AllUsersName allUsersName,
      ExternalIds externalIds,
      Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      ExternalIdNotesLoader extIdNotesLoader,
      PersonIdent committerIdent,
      PersonIdent authorIdent,
      Runnable afterReadRevision,
      Runnable beforeCommit) {
    this.repoManager = checkNotNull(repoManager, "repoManager");
    this.gitRefUpdated = checkNotNull(gitRefUpdated, "gitRefUpdated");
    this.currentUser = currentUser;
    this.allUsersName = checkNotNull(allUsersName, "allUsersName");
    this.externalIds = checkNotNull(externalIds, "externalIds");
    this.metaDataUpdateInternalFactory =
        checkNotNull(metaDataUpdateInternalFactory, "metaDataUpdateInternalFactory");
    this.retryHelper = checkNotNull(retryHelper, "retryHelper");
    this.extIdNotesLoader = checkNotNull(extIdNotesLoader, "extIdNotesLoader");
    this.committerIdent = checkNotNull(committerIdent, "committerIdent");
    this.authorIdent = checkNotNull(authorIdent, "authorIdent");
    this.afterReadRevision = checkNotNull(afterReadRevision, "afterReadRevision");
    this.beforeCommit = checkNotNull(beforeCommit, "beforeCommit");
  }

  /**
   * Inserts a new account.
   *
   * @param message commit message for the account creation, must not be {@code null or empty}
   * @param accountId ID of the new account
   * @param init consumer to populate the new account
   * @return the newly created account
   * @throws OrmDuplicateKeyException if the account already exists
   * @throws IOException if creating the user branch fails due to an IO error
   * @throws OrmException if creating the user branch fails
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public AccountState insert(
      String message, Account.Id accountId, Consumer<InternalAccountUpdate.Builder> init)
      throws OrmException, IOException, ConfigInvalidException {
    return insert(message, accountId, AccountUpdater.fromConsumer(init));
  }

  /**
   * Inserts a new account.
   *
   * @param message commit message for the account creation, must not be {@code null or empty}
   * @param accountId ID of the new account
   * @param updater updater to populate the new account
   * @return the newly created account
   * @throws OrmDuplicateKeyException if the account already exists
   * @throws IOException if creating the user branch fails due to an IO error
   * @throws OrmException if creating the user branch fails
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public AccountState insert(String message, Account.Id accountId, AccountUpdater updater)
      throws OrmException, IOException, ConfigInvalidException {
    return updateAccount(
            r -> {
              AccountConfig accountConfig = read(r, accountId);
              Account account =
                  accountConfig.getNewAccount(new Timestamp(committerIdent.getWhen().getTime()));
              AccountState accountState = AccountState.forAccount(allUsersName, account);
              InternalAccountUpdate.Builder updateBuilder = InternalAccountUpdate.builder();
              updater.update(accountState, updateBuilder);

              InternalAccountUpdate update = updateBuilder.build();
              accountConfig.setAccountUpdate(update);
              ExternalIdNotes extIdNotes =
                  createExternalIdNotes(r, accountConfig.getExternalIdsRev(), accountId, update);
              UpdatedAccount updatedAccounts =
                  new UpdatedAccount(allUsersName, externalIds, message, accountConfig, extIdNotes);
              updatedAccounts.setCreated(true);
              return updatedAccounts;
            })
        .get();
  }

  /**
   * Gets the account and updates it atomically.
   *
   * <p>Changing the registration date of an account is not supported.
   *
   * @param message commit message for the account update, must not be {@code null or empty}
   * @param accountId ID of the account
   * @param update consumer to update the account, only invoked if the account exists
   * @return the updated account, {@link Optional#empty()} if the account doesn't exist
   * @throws IOException if updating the user branch fails due to an IO error
   * @throws OrmException if updating the user branch fails
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public Optional<AccountState> update(
      String message, Account.Id accountId, Consumer<InternalAccountUpdate.Builder> update)
      throws OrmException, IOException, ConfigInvalidException {
    return update(message, accountId, AccountUpdater.fromConsumer(update));
  }

  /**
   * Gets the account and updates it atomically.
   *
   * <p>Changing the registration date of an account is not supported.
   *
   * @param message commit message for the account update, must not be {@code null or empty}
   * @param accountId ID of the account
   * @param updater updater to update the account, only invoked if the account exists
   * @return the updated account, {@link Optional#empty} if the account doesn't exist
   * @throws IOException if updating the user branch fails due to an IO error
   * @throws OrmException if updating the user branch fails
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public Optional<AccountState> update(String message, Account.Id accountId, AccountUpdater updater)
      throws OrmException, IOException, ConfigInvalidException {
    return updateAccount(
        r -> {
          AccountConfig accountConfig = read(r, accountId);
          Optional<AccountState> account =
              AccountState.fromAccountConfig(allUsersName, externalIds, accountConfig);
          if (!account.isPresent()) {
            return null;
          }

          InternalAccountUpdate.Builder updateBuilder = InternalAccountUpdate.builder();
          updater.update(account.get(), updateBuilder);

          InternalAccountUpdate update = updateBuilder.build();
          accountConfig.setAccountUpdate(update);
          ExternalIdNotes extIdNotes =
              createExternalIdNotes(r, accountConfig.getExternalIdsRev(), accountId, update);
          UpdatedAccount updatedAccounts =
              new UpdatedAccount(allUsersName, externalIds, message, accountConfig, extIdNotes);
          return updatedAccounts;
        });
  }

  private AccountConfig read(Repository allUsersRepo, Account.Id accountId)
      throws IOException, ConfigInvalidException {
    AccountConfig accountConfig = new AccountConfig(accountId, allUsersRepo).load();
    afterReadRevision.run();
    return accountConfig;
  }

  private Optional<AccountState> updateAccount(AccountUpdate accountUpdate)
      throws IOException, ConfigInvalidException, OrmException {
    return retryHelper.execute(
        ActionType.ACCOUNT_UPDATE,
        () -> {
          try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
            UpdatedAccount updatedAccount = accountUpdate.update(allUsersRepo);
            if (updatedAccount == null) {
              return Optional.empty();
            }

            commit(allUsersRepo, updatedAccount);
            return Optional.of(updatedAccount.getAccount());
          }
        });
  }

  private ExternalIdNotes createExternalIdNotes(
      Repository allUsersRepo,
      Optional<ObjectId> rev,
      Account.Id accountId,
      InternalAccountUpdate update)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    ExternalIdNotes.checkSameAccount(
        Iterables.concat(
            update.getCreatedExternalIds(),
            update.getUpdatedExternalIds(),
            update.getDeletedExternalIds()),
        accountId);

    ExternalIdNotes extIdNotes = extIdNotesLoader.load(allUsersRepo, rev.orElse(ObjectId.zeroId()));
    extIdNotes.replace(update.getDeletedExternalIds(), update.getCreatedExternalIds());
    extIdNotes.upsert(update.getUpdatedExternalIds());
    return extIdNotes;
  }

  private void commit(Repository allUsersRepo, UpdatedAccount updatedAccount) throws IOException {
    beforeCommit.run();

    BatchRefUpdate batchRefUpdate = allUsersRepo.getRefDatabase().newBatchUpdate();

    if (updatedAccount.isCreated()) {
      commitNewAccountConfig(
          updatedAccount.getMessage(),
          allUsersRepo,
          batchRefUpdate,
          updatedAccount.getAccountConfig());
    } else {
      commitAccountConfig(
          updatedAccount.getMessage(),
          allUsersRepo,
          batchRefUpdate,
          updatedAccount.getAccountConfig());
    }

    commitExternalIdUpdates(
        updatedAccount.getMessage(),
        allUsersRepo,
        batchRefUpdate,
        updatedAccount.getExternalIdNotes());

    RefUpdateUtil.executeChecked(batchRefUpdate, allUsersRepo);
    updatedAccount.getExternalIdNotes().updateCaches();
    gitRefUpdated.fire(
        allUsersName, batchRefUpdate, currentUser != null ? currentUser.getAccount() : null);
  }

  private void commitNewAccountConfig(
      String message,
      Repository allUsersRepo,
      BatchRefUpdate batchRefUpdate,
      AccountConfig accountConfig)
      throws IOException {
    // When creating a new account we must allow empty commits so that the user branch gets created
    // with an empty commit when no account properties are set and hence no 'account.config' file
    // will be created.
    commitAccountConfig(message, allUsersRepo, batchRefUpdate, accountConfig, true);
  }

  private void commitAccountConfig(
      String message,
      Repository allUsersRepo,
      BatchRefUpdate batchRefUpdate,
      AccountConfig accountConfig)
      throws IOException {
    commitAccountConfig(message, allUsersRepo, batchRefUpdate, accountConfig, false);
  }

  private void commitAccountConfig(
      String message,
      Repository allUsersRepo,
      BatchRefUpdate batchRefUpdate,
      AccountConfig accountConfig,
      boolean allowEmptyCommit)
      throws IOException {
    try (MetaDataUpdate md = createMetaDataUpdate(message, allUsersRepo, batchRefUpdate)) {
      md.setAllowEmpty(allowEmptyCommit);
      accountConfig.commit(md);
    }
  }

  private void commitExternalIdUpdates(
      String message,
      Repository allUsersRepo,
      BatchRefUpdate batchRefUpdate,
      ExternalIdNotes extIdNotes)
      throws IOException {
    try (MetaDataUpdate md = createMetaDataUpdate(message, allUsersRepo, batchRefUpdate)) {
      extIdNotes.commit(md);
    }
  }

  private MetaDataUpdate createMetaDataUpdate(
      String message, Repository allUsersRepo, BatchRefUpdate batchRefUpdate) {
    MetaDataUpdate metaDataUpdate =
        metaDataUpdateInternalFactory.get().create(allUsersName, allUsersRepo, batchRefUpdate);
    if (!message.endsWith("\n")) {
      message = message + "\n";
    }

    metaDataUpdate.getCommitBuilder().setMessage(message);
    metaDataUpdate.getCommitBuilder().setCommitter(committerIdent);
    metaDataUpdate.getCommitBuilder().setAuthor(authorIdent);
    return metaDataUpdate;
  }

  @FunctionalInterface
  private static interface AccountUpdate {
    UpdatedAccount update(Repository allUsersRepo)
        throws IOException, ConfigInvalidException, OrmException;
  }

  private static class UpdatedAccount {
    private final AllUsersName allUsersName;
    private final ExternalIds externalIds;
    private final String message;
    private final AccountConfig accountConfig;
    private final ExternalIdNotes extIdNotes;

    private boolean created;

    private UpdatedAccount(
        AllUsersName allUsersName,
        ExternalIds externalIds,
        String message,
        AccountConfig accountConfig,
        ExternalIdNotes extIdNotes) {
      checkState(!Strings.isNullOrEmpty(message), "message for account update must be set");
      this.allUsersName = checkNotNull(allUsersName);
      this.externalIds = checkNotNull(externalIds);
      this.message = checkNotNull(message);
      this.accountConfig = checkNotNull(accountConfig);
      this.extIdNotes = checkNotNull(extIdNotes);
    }

    public String getMessage() {
      return message;
    }

    public AccountConfig getAccountConfig() {
      return accountConfig;
    }

    public AccountState getAccount() throws IOException {
      return AccountState.fromAccountConfig(allUsersName, externalIds, accountConfig, extIdNotes)
          .get();
    }

    public ExternalIdNotes getExternalIdNotes() {
      return extIdNotes;
    }

    public void setCreated(boolean created) {
      this.created = created;
    }

    public boolean isCreated() {
      return created;
    }
  }
}
