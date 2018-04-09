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
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Runnables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.InternalAccountUpdate.Builder;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIdNotes.ExternalIdNotesLoader;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.change.ReindexAfterRefUpdate;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryHelper.Action;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
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
 * Creates and updates accounts.
 *
 * <p>This class should be used for all account updates. It supports updating account properties,
 * external IDs, preferences (general, diff and edit preferences) and project watches.
 *
 * <p>Updates to one account are always atomic. Batch updating several accounts within one
 * transaction is not supported.
 *
 * <p>For any account update the caller must provide a commit message, the account ID and an {@link
 * AccountUpdater}. The account updater allows to read the current {@link AccountState} and to
 * prepare updates to the account by calling setters on the provided {@link
 * InternalAccountUpdate.Builder}. If the current account state is of no interest the caller may
 * also provide a {@link Consumer} for {@link InternalAccountUpdate.Builder} instead of the account
 * updater.
 *
 * <p>The provided commit message is used for the update of the user branch. Using a precise and
 * unique commit message allows to identify the code from which an update was made when looking at a
 * commit in the user branch, and thus help debugging.
 *
 * <p>For creating a new account a new account ID can be retrieved from {@link
 * com.google.gerrit.server.Sequences#nextAccountId()}.
 *
 * <p>The account updates are written to NoteDb. In NoteDb accounts are represented as user branches
 * in the {@code All-Users} repository. Optionally a user branch can contain a 'account.config' file
 * that stores account properties, such as full name, preferred email, status and the active flag.
 * The timestamp of the first commit on a user branch denotes the registration date. The initial
 * commit on the user branch may be empty (since having an 'account.config' is optional). See {@link
 * AccountConfig} for details of the 'account.config' file format. In addition the user branch can
 * contain a 'preferences.config' config file to store preferences (see {@link Preferences}) and a
 * 'watch.config' config file to store project watches (see {@link ProjectWatches}). External IDs
 * are stored separately in the {@code refs/meta/external-ids} notes branch (see {@link
 * ExternalIdNotes}).
 *
 * <p>On updating an account the account is evicted from the account cache and reindexed. The
 * eviction from the account cache and the reindexing is done by the {@link ReindexAfterRefUpdate}
 * class which receives the event about updating the user branch that is triggered by this class.
 *
 * <p>If external IDs are updated, the ExternalIdCache is automatically updated by {@link
 * ExternalIdNotes}. In addition {@link ExternalIdNotes} takes care about evicting and reindexing
 * corresponding accounts. This is needed because external ID updates don't touch the user branches.
 * Hence in this case the accounts are not evicted and reindexed via {@link ReindexAfterRefUpdate}.
 *
 * <p>Reindexing and flushing accounts from the account cache can be disabled by
 *
 * <ul>
 *   <li>binding {@link GitReferenceUpdated#DISABLED} and
 *   <li>passing an {@link
 *       com.google.gerrit.server.account.externalids.ExternalIdNotes.FactoryNoReindex} factory as
 *       parameter of {@link AccountsUpdate.Factory#create(IdentifiedUser, ExternalIdNotesLoader)}
 * </ul>
 *
 * <p>If there are concurrent account updates updating the user branch in NoteDb may fail with
 * {@link LockFailureException}. In this case the account update is automatically retried and the
 * account updater is invoked once more with the updated account state. This means the whole
 * read-modify-write sequence is atomic. Retrying is limited by a timeout. If the timeout is
 * exceeded the account update can still fail with {@link LockFailureException}.
 */
public class AccountsUpdate {
  public interface Factory {
    /**
     * Creates an {@code AccountsUpdate} which uses the identity of the specified user as author for
     * all commits related to accounts. The Gerrit server identity will be used as committer.
     *
     * <p><strong>Note</strong>: Please use this method with care and rather consider to use the
     * correct annotation on the provider of an {@code AccountsUpdate} instead.
     *
     * @param currentUser the user to which modifications should be attributed, or {@code null} if
     *     the Gerrit server identity should also be used as author
     */
    AccountsUpdate create(
        @Nullable IdentifiedUser currentUser, ExternalIdNotesLoader externalIdNotesLoader);
  }

  /**
   * Updater for an account.
   *
   * <p>Allows to read the current state of an account and to prepare updates to it.
   */
  @FunctionalInterface
  public interface AccountUpdater {
    /**
     * Prepare updates to an account.
     *
     * <p>Use the provided account only to read the current state of the account. Don't do updates
     * to the account. For updates use the provided account update builder.
     *
     * @param accountState the account that is being updated
     * @param update account update builder
     */
    void update(AccountState accountState, InternalAccountUpdate.Builder update) throws IOException;

    static AccountUpdater join(List<AccountUpdater> updaters) {
      return new AccountUpdater() {
        @Override
        public void update(AccountState accountState, Builder update) throws IOException {
          for (AccountUpdater updater : updaters) {
            updater.update(accountState, update);
          }
        }
      };
    }

    static AccountUpdater joinConsumers(List<Consumer<InternalAccountUpdate.Builder>> consumers) {
      return join(Lists.transform(consumers, AccountUpdater::fromConsumer));
    }

    static AccountUpdater fromConsumer(Consumer<InternalAccountUpdate.Builder> consumer) {
      return (a, u) -> consumer.accept(u);
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

  @Inject
  AccountsUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      AllUsersName allUsersName,
      ExternalIds externalIds,
      Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      @GerritPersonIdent PersonIdent serverIdent,
      @Assisted @Nullable IdentifiedUser currentUser,
      @Assisted ExternalIdNotesLoader extIdNotesLoader) {
    this(
        repoManager,
        gitRefUpdated,
        currentUser,
        allUsersName,
        externalIds,
        metaDataUpdateInternalFactory,
        retryHelper,
        extIdNotesLoader,
        serverIdent,
        createPersonIdent(serverIdent, currentUser),
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

  private static PersonIdent createPersonIdent(
      PersonIdent serverIdent, @Nullable IdentifiedUser user) {
    if (user == null) {
      return serverIdent;
    }
    return user.newCommitterIdent(serverIdent.getWhen(), serverIdent.getTimeZone());
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
   * @throws LockFailureException if updating the user branch still fails due to concurrent updates
   *     after the retry timeout exceeded
   * @throws OrmException if updating the user branch fails
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public Optional<AccountState> update(
      String message, Account.Id accountId, Consumer<InternalAccountUpdate.Builder> update)
      throws OrmException, LockFailureException, IOException, ConfigInvalidException {
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
   * @throws LockFailureException if updating the user branch still fails due to concurrent updates
   *     after the retry timeout exceeded
   * @throws OrmException if updating the user branch fails
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public Optional<AccountState> update(String message, Account.Id accountId, AccountUpdater updater)
      throws OrmException, LockFailureException, IOException, ConfigInvalidException {
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
    return executeAccountUpdate(
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

  private Optional<AccountState> executeAccountUpdate(Action<Optional<AccountState>> action)
      throws IOException, ConfigInvalidException, OrmException {
    try {
      return retryHelper.execute(
          ActionType.ACCOUNT_UPDATE, action, LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      Throwables.throwIfInstanceOf(e, OrmException.class);
      throw new OrmException(e);
    }
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
        allUsersName, batchRefUpdate, currentUser != null ? currentUser.state() : null);
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
