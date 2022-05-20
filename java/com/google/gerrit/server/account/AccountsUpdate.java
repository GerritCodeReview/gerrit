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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIdNotes.ExternalIdNotesLoader;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.CachedPreferences;
import com.google.gerrit.server.config.VersionedDefaultPreferences;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.change.ReindexAfterRefUpdate;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryableAction.Action;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * Creates and updates accounts.
 *
 * <p>This class should be used for all account updates. See {@link AccountDelta} for what can be
 * updated.
 *
 * <p>Batch updates of multiple different accounts can be performed atomically, see {@link
 * #updateBatch(List)}. Batch creation is not supported.
 *
 * <p>For any account update the caller must provide a commit message, the account ID and an {@link
 * ConfigureDeltaFromState}. The account updater reads the current {@link AccountState} and prepares
 * updates to the account by calling setters on the provided {@link AccountDelta.Builder}. If the
 * current account state is of no interest the caller may also provide a {@link Consumer} for {@link
 * AccountDelta.Builder} instead of the account updater.
 *
 * <p>The provided commit message is used for the update of the user branch. Using a precise and
 * unique commit message allows to identify the code from which an update was made when looking at a
 * commit in the user branch, and thus help debugging.
 *
 * <p>For creating a new account a new account ID can be retrieved from {@link
 * Sequences#nextAccountId()}.
 *
 * <p>The account updates are written to NoteDb. In NoteDb accounts are represented as user branches
 * in the {@code All-Users} repository. Optionally a user branch can contain a 'account.config' file
 * that stores account properties, such as full name, display name, preferred email, status and the
 * active flag. The timestamp of the first commit on a user branch denotes the registration date.
 * The initial commit on the user branch may be empty (since having an 'account.config' is
 * optional). See {@link AccountConfig} for details of the 'account.config' file format. In addition
 * the user branch can contain a 'preferences.config' config file to store preferences (see {@link
 * StoredPreferences}) and a 'watch.config' config file to store project watches (see {@link
 * ProjectWatches}). External IDs are stored separately in the {@code refs/meta/external-ids} notes
 * branch (see {@link ExternalIdNotes}).
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
 *       parameter of {@link AccountsUpdate.Factory#create(IdentifiedUser,
 *       ExternalIdNotes.ExternalIdNotesLoader)}
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
     * all commits related to accounts. The server identity will be used as committer.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.UserInitiated} annotation on the provider of an {@code
     * AccountsUpdate} instead.
     *
     * @param currentUser the user to which modifications should be attributed
     * @param externalIdNotesLoader the loader that should be used to load external ID notes
     */
    AccountsUpdate create(IdentifiedUser currentUser, ExternalIdNotesLoader externalIdNotesLoader);

    /**
     * Creates an {@code AccountsUpdate} which uses the server identity as author and committer for
     * all commits related to accounts.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.ServerInitiated} annotation on the provider of an {@code
     * AccountsUpdate} instead.
     *
     * @param externalIdNotesLoader the loader that should be used to load external ID notes
     */
    AccountsUpdate createWithServerIdent(ExternalIdNotesLoader externalIdNotesLoader);
  }

  /**
   * Account updates are commonly performed by evaluating the current account state and creating a
   * delta to be applied to it in a later step. This is done by implementing this interface.
   *
   * <p>If the current account state is not needed, use a {@link Consumer} of {@link
   * AccountDelta.Builder} instead.
   */
  @FunctionalInterface
  public interface ConfigureDeltaFromState {
    /**
     * Receives the current {@link AccountState} (which is immutable) and configures an {@link
     * AccountDelta.Builder} with changes to the account.
     *
     * @param accountState the state of the account that is being updated
     * @param delta the changes to be applied
     */
    void configure(AccountState accountState, AccountDelta.Builder delta) throws IOException;
  }

  /** Data holder for the set of arguments required to update an account. Used for batch updates. */
  public static class UpdateArguments {
    private final String message;
    private final Account.Id accountId;
    private final ConfigureDeltaFromState configureDeltaFromState;

    public UpdateArguments(
        String message, Account.Id accountId, ConfigureDeltaFromState configureDeltaFromState) {
      this.message = message;
      this.accountId = accountId;
      this.configureDeltaFromState = configureDeltaFromState;
    }
  }

  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final Optional<IdentifiedUser> currentUser;
  private final AllUsersName allUsersName;
  private final ExternalIds externalIds;
  private final Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory;
  private final RetryHelper retryHelper;
  private final ExternalIdNotesLoader extIdNotesLoader;
  private final PersonIdent committerIdent;
  private final PersonIdent authorIdent;

  /** Invoked after reading the account config. */
  private final Runnable afterReadRevision;

  /** Invoked after updating the account but before committing the changes. */
  private final Runnable beforeCommit;

  /** Single instance that accumulates updates from the batch. */
  private ExternalIdNotes externalIdNotes;

  @AssistedInject
  @SuppressWarnings("BindingAnnotationWithoutInject")
  AccountsUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      AllUsersName allUsersName,
      ExternalIds externalIds,
      Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      @GerritPersonIdent PersonIdent serverIdent,
      @Assisted ExternalIdNotesLoader extIdNotesLoader) {
    this(
        repoManager,
        gitRefUpdated,
        Optional.empty(),
        allUsersName,
        externalIds,
        metaDataUpdateInternalFactory,
        retryHelper,
        extIdNotesLoader,
        serverIdent,
        createPersonIdent(serverIdent, Optional.empty()),
        AccountsUpdate::doNothing,
        AccountsUpdate::doNothing);
  }

  @AssistedInject
  @SuppressWarnings("BindingAnnotationWithoutInject")
  AccountsUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      AllUsersName allUsersName,
      ExternalIds externalIds,
      Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      @GerritPersonIdent PersonIdent serverIdent,
      @Assisted IdentifiedUser currentUser,
      @Assisted ExternalIdNotesLoader extIdNotesLoader) {
    this(
        repoManager,
        gitRefUpdated,
        Optional.of(currentUser),
        allUsersName,
        externalIds,
        metaDataUpdateInternalFactory,
        retryHelper,
        extIdNotesLoader,
        serverIdent,
        createPersonIdent(serverIdent, Optional.of(currentUser)),
        AccountsUpdate::doNothing,
        AccountsUpdate::doNothing);
  }

  @VisibleForTesting
  public AccountsUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      Optional<IdentifiedUser> currentUser,
      AllUsersName allUsersName,
      ExternalIds externalIds,
      Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      ExternalIdNotesLoader extIdNotesLoader,
      PersonIdent committerIdent,
      PersonIdent authorIdent,
      Runnable afterReadRevision,
      Runnable beforeCommit) {
    this.repoManager = requireNonNull(repoManager, "repoManager");
    this.gitRefUpdated = requireNonNull(gitRefUpdated, "gitRefUpdated");
    this.currentUser = currentUser;
    this.allUsersName = requireNonNull(allUsersName, "allUsersName");
    this.externalIds = requireNonNull(externalIds, "externalIds");
    this.metaDataUpdateInternalFactory =
        requireNonNull(metaDataUpdateInternalFactory, "metaDataUpdateInternalFactory");
    this.retryHelper = requireNonNull(retryHelper, "retryHelper");
    this.extIdNotesLoader = requireNonNull(extIdNotesLoader, "extIdNotesLoader");
    this.committerIdent = requireNonNull(committerIdent, "committerIdent");
    this.authorIdent = requireNonNull(authorIdent, "authorIdent");
    this.afterReadRevision = requireNonNull(afterReadRevision, "afterReadRevision");
    this.beforeCommit = requireNonNull(beforeCommit, "beforeCommit");
  }

  /** Returns an instance that runs all specified consumers. */
  public static ConfigureDeltaFromState joinConsumers(
      List<Consumer<AccountDelta.Builder>> consumers) {
    return (accountStateIgnored, update) -> consumers.forEach(c -> c.accept(update));
  }

  private static ConfigureDeltaFromState fromConsumer(Consumer<AccountDelta.Builder> consumer) {
    return (a, u) -> consumer.accept(u);
  }

  private static PersonIdent createPersonIdent(
      PersonIdent serverIdent, Optional<IdentifiedUser> user) {
    return user.isPresent() ? user.get().newCommitterIdent(serverIdent) : serverIdent;
  }

  /**
   * Like {@link #insert(String, Account.Id, ConfigureDeltaFromState)}, but using a {@link Consumer}
   * instead, i.e. the update does not depend on the current account state (which, for insertion,
   * would only contain the account ID).
   */
  public AccountState insert(
      String message, Account.Id accountId, Consumer<AccountDelta.Builder> init)
      throws IOException, ConfigInvalidException {
    return insert(message, accountId, fromConsumer(init));
  }

  /**
   * Inserts a new account.
   *
   * @param message commit message for the account creation, must not be {@code null or empty}
   * @param accountId ID of the new account
   * @param init to populate the new account
   * @return the newly created account
   * @throws DuplicateKeyException if the account already exists
   * @throws IOException if creating the user branch fails due to an IO error
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public AccountState insert(String message, Account.Id accountId, ConfigureDeltaFromState init)
      throws IOException, ConfigInvalidException {
    return execute(
            ImmutableList.of(
                repo -> {
                  AccountConfig accountConfig = read(repo, accountId);
                  Account account = accountConfig.getNewAccount(committerIdent.getWhenAsInstant());
                  AccountState accountState = AccountState.forAccount(account);
                  AccountDelta.Builder deltaBuilder = AccountDelta.builder();
                  init.configure(accountState, deltaBuilder);

                  AccountDelta accountDelta = deltaBuilder.build();
                  accountConfig.setAccountDelta(accountDelta);
                  externalIdNotes =
                      createExternalIdNotes(
                          repo, accountConfig.getExternalIdsRev(), accountId, accountDelta);
                  CachedPreferences defaultPreferences =
                      CachedPreferences.fromConfig(
                          VersionedDefaultPreferences.get(repo, allUsersName));

                  return new UpdatedAccount(message, accountConfig, defaultPreferences, true);
                }))
        .get(0)
        .get();
  }

  /**
   * Like {@link #update(String, Account.Id, ConfigureDeltaFromState)}, but using a {@link Consumer}
   * instead, i.e. the update does not depend on the current account state.
   */
  public Optional<AccountState> update(
      String message, Account.Id accountId, Consumer<AccountDelta.Builder> update)
      throws IOException, ConfigInvalidException {
    return update(message, accountId, fromConsumer(update));
  }

  /**
   * Gets the account and updates it atomically.
   *
   * <p>Changing the registration date of an account is not supported.
   *
   * @param message commit message for the account update, must not be {@code null or empty}
   * @param accountId ID of the account
   * @param configureDeltaFromState deltaBuilder to update the account, only invoked if the account
   *     exists
   * @return the updated account, {@link Optional#empty} if the account doesn't exist
   * @throws IOException if updating the user branch fails due to an IO error
   * @throws LockFailureException if updating the user branch still fails due to concurrent updates
   *     after the retry timeout exceeded
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public Optional<AccountState> update(
      String message, Account.Id accountId, ConfigureDeltaFromState configureDeltaFromState)
      throws LockFailureException, IOException, ConfigInvalidException {
    return updateBatch(
            ImmutableList.of(new UpdateArguments(message, accountId, configureDeltaFromState)))
        .get(0);
  }

  private ExecutableUpdate createExecutableUpdate(UpdateArguments updateArguments) {
    return repo -> {
      AccountConfig accountConfig = read(repo, updateArguments.accountId);
      CachedPreferences defaultPreferences =
          CachedPreferences.fromConfig(VersionedDefaultPreferences.get(repo, allUsersName));
      Optional<AccountState> accountState =
          AccountState.fromAccountConfig(externalIds, accountConfig, defaultPreferences);
      if (!accountState.isPresent()) {
        return null;
      }

      AccountDelta.Builder deltaBuilder = AccountDelta.builder();
      updateArguments.configureDeltaFromState.configure(accountState.get(), deltaBuilder);

      AccountDelta delta = deltaBuilder.build();
      accountConfig.setAccountDelta(delta);
      ExternalIdNotes.checkSameAccount(
          Iterables.concat(
              delta.getCreatedExternalIds(),
              delta.getUpdatedExternalIds(),
              delta.getDeletedExternalIds()),
          updateArguments.accountId);

      if (externalIdNotes == null) {
        externalIdNotes =
            extIdNotesLoader.load(
                repo, accountConfig.getExternalIdsRev().orElse(ObjectId.zeroId()));
      }
      externalIdNotes.replace(delta.getDeletedExternalIds(), delta.getCreatedExternalIds());
      externalIdNotes.upsert(delta.getUpdatedExternalIds());

      CachedPreferences cachedDefaultPreferences =
          CachedPreferences.fromConfig(VersionedDefaultPreferences.get(repo, allUsersName));

      return new UpdatedAccount(
          updateArguments.message, accountConfig, cachedDefaultPreferences, false);
    };
  }

  /**
   * Updates multiple different accounts atomically. This will only store a single new value (aka
   * set of all external IDs of the host) in the external ID cache, which is important for storage
   * economy. All {@code updates} must be for different accounts.
   *
   * <p>NOTE on error handling: Since updates are executed in multiple stages, with some stages
   * resulting from the union of all individual updates, we cannot point to the update that caused
   * the error. Callers should be aware that a single "update of death" (or a set of updates that
   * together have this property) will always prevent the entire batch from being executed.
   */
  public ImmutableList<Optional<AccountState>> updateBatch(List<UpdateArguments> updates)
      throws IOException, ConfigInvalidException {
    checkArgument(
        updates.stream().map(u -> u.accountId.get()).distinct().count() == updates.size(),
        "updates must all be for different accounts");
    return execute(updates.stream().map(this::createExecutableUpdate).collect(toList()));
  }

  private AccountConfig read(Repository allUsersRepo, Account.Id accountId)
      throws IOException, ConfigInvalidException {
    AccountConfig accountConfig = new AccountConfig(accountId, allUsersName, allUsersRepo).load();
    afterReadRevision.run();
    return accountConfig;
  }

  private ImmutableList<Optional<AccountState>> execute(List<ExecutableUpdate> executableUpdates)
      throws IOException, ConfigInvalidException {
    List<Optional<AccountState>> accountState = new ArrayList<>();
    List<UpdatedAccount> updatedAccounts = new ArrayList<>();
    executeWithRetry(
        () -> {
          // Reset state for retry.
          externalIdNotes = null;
          accountState.clear();
          updatedAccounts.clear();

          try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
            for (ExecutableUpdate executableUpdate : executableUpdates) {
              updatedAccounts.add(executableUpdate.execute(allUsersRepo));
            }
            commit(
                allUsersRepo, updatedAccounts.stream().filter(Objects::nonNull).collect(toList()));
            for (UpdatedAccount ua : updatedAccounts) {
              accountState.add(ua == null ? Optional.empty() : ua.getAccountState());
            }
          }
          return null;
        });
    return ImmutableList.copyOf(accountState);
  }

  private void executeWithRetry(Action<Void> action) throws IOException, ConfigInvalidException {
    try {
      retryHelper.accountUpdate("updateAccount", action).call();
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      throw new StorageException(e);
    }
  }

  private ExternalIdNotes createExternalIdNotes(
      Repository allUsersRepo, Optional<ObjectId> rev, Account.Id accountId, AccountDelta update)
      throws IOException, ConfigInvalidException, DuplicateKeyException {
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

  private void commit(Repository allUsersRepo, List<UpdatedAccount> updatedAccounts)
      throws IOException {
    if (updatedAccounts.isEmpty()) {
      return;
    }

    beforeCommit.run();

    BatchRefUpdate batchRefUpdate = allUsersRepo.getRefDatabase().newBatchUpdate();

    String externalIdUpdateMessage =
        updatedAccounts.size() == 1
            ? Iterables.getOnlyElement(updatedAccounts).message
            : "Batch update for " + updatedAccounts.size() + " accounts";
    for (UpdatedAccount updatedAccount : updatedAccounts) {
      // These updates are all for different refs (because batches never update the same account
      // more than once), so there can be multiple commits in the same batch, all with the same base
      // revision in their AccountConfig.
      commitAccountConfig(
          updatedAccount.message,
          allUsersRepo,
          batchRefUpdate,
          updatedAccount.accountConfig,
          updatedAccount.created /* allowEmptyCommit */);
      // When creating a new account we must allow empty commits so that the user branch gets
      // created with an empty commit when no account properties are set and hence no
      // 'account.config' file will be created.

      // These update the same ref, so they need to be stacked on top of one another using the same
      // ExternalIdNotes instance.
      commitExternalIdUpdates(externalIdUpdateMessage, allUsersRepo, batchRefUpdate);
    }

    RefUpdateUtil.executeChecked(batchRefUpdate, allUsersRepo);

    Set<Account.Id> accountsToSkipForReindex = getUpdatedAccountIds(batchRefUpdate);
    extIdNotesLoader.updateExternalIdCacheAndMaybeReindexAccounts(
        externalIdNotes, accountsToSkipForReindex);

    gitRefUpdated.fire(
        allUsersName, batchRefUpdate, currentUser.map(IdentifiedUser::state).orElse(null));
  }

  private static Set<Account.Id> getUpdatedAccountIds(BatchRefUpdate batchRefUpdate) {
    return batchRefUpdate.getCommands().stream()
        .map(c -> Account.Id.fromRef(c.getRefName()))
        .filter(Objects::nonNull)
        .collect(toSet());
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
      String message, Repository allUsersRepo, BatchRefUpdate batchRefUpdate) throws IOException {
    try (MetaDataUpdate md = createMetaDataUpdate(message, allUsersRepo, batchRefUpdate)) {
      externalIdNotes.commit(md);
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

  private static void doNothing() {}

  @FunctionalInterface
  private interface ExecutableUpdate {
    UpdatedAccount execute(Repository allUsersRepo) throws IOException, ConfigInvalidException;
  }

  private class UpdatedAccount {
    final String message;
    final AccountConfig accountConfig;
    final CachedPreferences defaultPreferences;
    final boolean created;

    UpdatedAccount(
        String message,
        AccountConfig accountConfig,
        CachedPreferences defaultPreferences,
        boolean created) {
      checkState(!Strings.isNullOrEmpty(message), "message for account update must be set");
      this.message = requireNonNull(message);
      this.accountConfig = requireNonNull(accountConfig);
      this.defaultPreferences = defaultPreferences;
      this.created = created;
    }

    Optional<AccountState> getAccountState() throws IOException {
      return AccountState.fromAccountConfig(
          externalIds, accountConfig, externalIdNotes, defaultPreferences);
    }
  }
}
