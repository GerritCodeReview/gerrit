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

package com.google.gerrit.server.account.storage.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.ACCOUNTS_UPDATE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.AccountDelta;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.ProjectWatches;
import com.google.gerrit.server.account.StoredPreferences;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdsNoteDbImpl;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.CachedPreferences;
import com.google.gerrit.server.config.VersionedDefaultPreferences;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.change.ReindexAfterRefUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryableAction.Action;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Creates and updates accounts which are stored in All-Users NoteDB repository.
 *
 * <p>Batch updates of multiple different accounts can be performed atomically, see {@link
 * #updateBatch(List)}. Batch creation is not supported.
 *
 * <p>For any account update the caller must provide a commit message, the account ID and an {@link
 * com.google.gerrit.server.account.AccountsUpdate.ConfigureDeltaFromState}. The account updater
 * reads the current {@link AccountState} and prepares updates to the account by calling setters on
 * the provided {@link com.google.gerrit.server.account.AccountDelta.Builder}. If the current
 * account state is of no interest the caller may also provide a {@link Consumer} for {@link
 * com.google.gerrit.server.account.AccountDelta.Builder} instead of the account updater.
 *
 * <p>The provided commit message is used for the update of the user branch. Using a precise and
 * unique commit message allows to identify the code from which an update was made when looking at a
 * commit in the user branch, and thus help debugging.
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
 *   <li>using {@link
 *       com.google.gerrit.server.account.storage.notedb.AccountsUpdateNoteDbImpl.FactoryNoReindex}
 *       and
 *   <li>binding {@link GitReferenceUpdated#DISABLED}
 * </ul>
 *
 * <p>If there are concurrent account updates which updating the user branch in NoteDb may fail with
 * {@link LockFailureException}. In this case the account update is automatically retried and the
 * account updater is invoked once more with the updated account state. This means the whole
 * read-modify-write sequence is atomic. Retrying is limited by a timeout. If the timeout is
 * exceeded the account update can still fail with {@link LockFailureException}.
 */
public class AccountsUpdateNoteDbImpl implements AccountsUpdate {
  private static class AbstractFactory extends AccountsUpdateLoader {
    private final GitRepositoryManager repoManager;
    private final GitReferenceUpdated gitRefUpdated;
    private final AllUsersName allUsersName;
    private final ExternalIdsNoteDbImpl externalIds;
    private final ExternalIdNotes.ExternalIdNotesLoader extIdNotesFactory;
    private final Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory;
    private final RetryHelper retryHelper;
    private final Provider<PersonIdent> serverIdentProvider;

    private AbstractFactory(
        GitRepositoryManager repoManager,
        GitReferenceUpdated gitRefUpdated,
        AllUsersName allUsersName,
        ExternalIdsNoteDbImpl externalIds,
        Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
        RetryHelper retryHelper,
        @GerritPersonIdent Provider<PersonIdent> serverIdentProvider,
        ExternalIdNotes.ExternalIdNotesLoader extIdNotesFactory) {
      this.repoManager = repoManager;
      this.gitRefUpdated = gitRefUpdated;
      this.allUsersName = allUsersName;
      this.externalIds = externalIds;
      this.metaDataUpdateInternalFactory = metaDataUpdateInternalFactory;
      this.retryHelper = retryHelper;
      this.serverIdentProvider = serverIdentProvider;
      this.extIdNotesFactory = extIdNotesFactory;
    }

    @Override
    public AccountsUpdate create(IdentifiedUser currentUser) {
      PersonIdent serverIdent = serverIdentProvider.get();
      return new AccountsUpdateNoteDbImpl(
          repoManager,
          gitRefUpdated,
          Optional.of(currentUser),
          allUsersName,
          externalIds,
          extIdNotesFactory,
          metaDataUpdateInternalFactory,
          retryHelper,
          serverIdent,
          createPersonIdent(serverIdent, Optional.of(currentUser)),
          AccountsUpdateNoteDbImpl::doNothing,
          AccountsUpdateNoteDbImpl::doNothing);
    }

    @Override
    public AccountsUpdate createWithServerIdent() {
      PersonIdent serverIdent = serverIdentProvider.get();
      return new AccountsUpdateNoteDbImpl(
          repoManager,
          gitRefUpdated,
          Optional.empty(),
          allUsersName,
          externalIds,
          extIdNotesFactory,
          metaDataUpdateInternalFactory,
          retryHelper,
          serverIdent,
          createPersonIdent(serverIdent, Optional.empty()),
          AccountsUpdateNoteDbImpl::doNothing,
          AccountsUpdateNoteDbImpl::doNothing);
    }
  }

  @Singleton
  public static class Factory extends AbstractFactory {
    @Inject
    Factory(
        GitRepositoryManager repoManager,
        GitReferenceUpdated gitRefUpdated,
        AllUsersName allUsersName,
        ExternalIdsNoteDbImpl externalIds,
        Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
        RetryHelper retryHelper,
        @GerritPersonIdent Provider<PersonIdent> serverIdentProvider,
        ExternalIdNotes.Factory extIdNotesFactory) {
      super(
          repoManager,
          gitRefUpdated,
          allUsersName,
          externalIds,
          metaDataUpdateInternalFactory,
          retryHelper,
          serverIdentProvider,
          extIdNotesFactory);
    }
  }

  @Singleton
  public static class FactoryNoReindex extends AbstractFactory {
    @Inject
    FactoryNoReindex(
        GitRepositoryManager repoManager,
        GitReferenceUpdated gitRefUpdated,
        AllUsersName allUsersName,
        ExternalIdsNoteDbImpl externalIds,
        Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
        RetryHelper retryHelper,
        @GerritPersonIdent Provider<PersonIdent> serverIdentProvider,
        ExternalIdNotes.FactoryNoReindex extIdNotesFactory) {
      super(
          repoManager,
          gitRefUpdated,
          allUsersName,
          externalIds,
          metaDataUpdateInternalFactory,
          retryHelper,
          serverIdentProvider,
          extIdNotesFactory);
    }
  }

  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  private final Optional<IdentifiedUser> currentUser;
  private final AllUsersName allUsersName;
  private final ExternalIdsNoteDbImpl externalIds;

  private final ExternalIdNotes.ExternalIdNotesLoader extIdNotesFactory;
  private final Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory;
  private final RetryHelper retryHelper;
  private final PersonIdent committerIdent;
  private final PersonIdent authorIdent;

  /** Invoked after reading the account config. */
  private final Runnable afterReadRevision;

  /** Invoked after updating the account but before committing the changes. */
  private final Runnable beforeCommit;

  /** Single instance that accumulates updates from the batch. */
  @Nullable private ExternalIdNotes externalIdNotes;

  @VisibleForTesting
  public AccountsUpdateNoteDbImpl(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      Optional<IdentifiedUser> currentUser,
      AllUsersName allUsersName,
      ExternalIdsNoteDbImpl externalIds,
      ExternalIdNotes.ExternalIdNotesLoader extIdNotesFactory,
      Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory,
      RetryHelper retryHelper,
      PersonIdent committerIdent,
      PersonIdent authorIdent,
      Runnable afterReadRevision,
      Runnable beforeCommit) {
    this.repoManager = requireNonNull(repoManager, "repoManager");
    this.gitRefUpdated = requireNonNull(gitRefUpdated, "gitRefUpdated");
    this.currentUser = currentUser;
    this.allUsersName = requireNonNull(allUsersName, "allUsersName");
    this.externalIds = requireNonNull(externalIds, "externalIds");
    this.extIdNotesFactory = extIdNotesFactory;
    this.metaDataUpdateInternalFactory =
        requireNonNull(metaDataUpdateInternalFactory, "metaDataUpdateInternalFactory");
    this.retryHelper = requireNonNull(retryHelper, "retryHelper");
    this.committerIdent = requireNonNull(committerIdent, "committerIdent");
    this.authorIdent = requireNonNull(authorIdent, "authorIdent");
    this.afterReadRevision = requireNonNull(afterReadRevision, "afterReadRevision");
    this.beforeCommit = requireNonNull(beforeCommit, "beforeCommit");
  }

  private static ConfigureDeltaFromState fromConsumer(Consumer<AccountDelta.Builder> consumer) {
    return (a, u) -> consumer.accept(u);
  }

  private static PersonIdent createPersonIdent(
      PersonIdent serverIdent, Optional<IdentifiedUser> user) {
    return user.isPresent() ? user.get().newCommitterIdent(serverIdent) : serverIdent;
  }

  @Override
  public AccountState insert(
      String message, Account.Id accountId, Consumer<AccountDelta.Builder> init)
      throws IOException, ConfigInvalidException {
    return insert(message, accountId, fromConsumer(init));
  }

  @Override
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

  @Override
  @CanIgnoreReturnValue
  public Optional<AccountState> update(
      String message, Account.Id accountId, Consumer<AccountDelta.Builder> update)
      throws IOException, ConfigInvalidException {
    return update(message, accountId, fromConsumer(update));
  }

  @Override
  @CanIgnoreReturnValue
  public Optional<AccountState> update(
      String message, Account.Id accountId, ConfigureDeltaFromState configureDeltaFromState)
      throws LockFailureException, IOException, ConfigInvalidException {
    return updateBatch(
            ImmutableList.of(new UpdateArguments(message, accountId, configureDeltaFromState)))
        .get(0);
  }

  @Override
  public void delete(String message, Account.Id accountId)
      throws IOException, ConfigInvalidException {
    ImmutableSet<ExternalId> accountExternalIds = externalIds.byAccount(accountId);
    Consumer<AccountDelta.Builder> delta =
        deltaBuilder -> deltaBuilder.deleteAccount(accountExternalIds);
    update(message, accountId, delta);
  }

  private ExecutableUpdate createExecutableUpdate(UpdateArguments updateArguments) {
    return repo -> {
      AccountConfig accountConfig = read(repo, updateArguments.accountId);
      CachedPreferences defaultPreferences =
          CachedPreferences.fromConfig(VersionedDefaultPreferences.get(repo, allUsersName));
      Optional<AccountState> accountState =
          AccountsNoteDbImpl.getFromAccountConfig(externalIds, accountConfig, defaultPreferences);
      if (!accountState.isPresent()) {
        return null;
      }

      AccountDelta.Builder deltaBuilder = AccountDelta.builder();
      updateArguments.configureDeltaFromState.configure(accountState.get(), deltaBuilder);

      AccountDelta delta = deltaBuilder.build();
      ExternalIdNotes.checkSameAccount(
          Iterables.concat(
              delta.getCreatedExternalIds(),
              delta.getUpdatedExternalIds(),
              delta.getDeletedExternalIds()),
          updateArguments.accountId);

      if (delta.hasExternalIdUpdates()) {
        // Only load the externalIds if they are going to be updated
        // This makes e.g. preferences updates faster.
        if (externalIdNotes == null) {
          externalIdNotes =
              extIdNotesFactory.load(
                  repo, accountConfig.getExternalIdsRev().orElse(ObjectId.zeroId()));
        }
        externalIdNotes.replace(delta.getDeletedExternalIds(), delta.getCreatedExternalIds());
        externalIdNotes.upsert(delta.getUpdatedExternalIds());
      }

      if (delta.getShouldDeleteAccount().orElse(false)) {
        return new DeletedAccount(updateArguments.message, accountConfig.getRefName());
      }

      accountConfig.setAccountDelta(delta);
      CachedPreferences cachedDefaultPreferences =
          CachedPreferences.fromConfig(VersionedDefaultPreferences.get(repo, allUsersName));
      return new UpdatedAccount(
          updateArguments.message, accountConfig, cachedDefaultPreferences, false);
    };
  }

  @Override
  @CanIgnoreReturnValue
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
    try (RefUpdateContext ctx = RefUpdateContext.open(ACCOUNTS_UPDATE)) {
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
                  allUsersRepo,
                  updatedAccounts.stream().filter(Objects::nonNull).collect(toList()));
              for (UpdatedAccount ua : updatedAccounts) {
                accountState.add(
                    ua == null || ua.deleted ? Optional.empty() : ua.getAccountState());
              }
            }
            return null;
          });

      return ImmutableList.copyOf(accountState);
    }
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

    ExternalIdNotes extIdNotes =
        extIdNotesFactory.load(allUsersRepo, rev.orElse(ObjectId.zeroId()));
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
    Set<Account.Id> accountsToSkipForReindex = new HashSet<>();
    //  External ids may be not updated if:
    //  * externalIdNotes is not loaded  (there were no externalId updates in the delta)
    //  * new revCommit is identical to the previous externalId tip
    boolean externalIdsUpdated = false;
    if (externalIdNotes != null) {
      String externalIdUpdateMessage =
          updatedAccounts.size() == 1
              ? Iterables.getOnlyElement(updatedAccounts).message
              : "Batch update for " + updatedAccounts.size() + " accounts";
      ObjectId oldExternalIdsRevision = externalIdNotes.getRevision();
      // These update the same ref, so they need to be stacked on top of one another using the same
      // ExternalIdNotes instance.
      RevCommit revCommit =
          commitExternalIdUpdates(externalIdUpdateMessage, allUsersRepo, batchRefUpdate);
      externalIdsUpdated = !Objects.equals(revCommit.getId(), oldExternalIdsRevision);
    }
    for (UpdatedAccount updatedAccount : updatedAccounts) {
      if (updatedAccount.deleted) {
        RefUpdate ru = RefUpdateUtil.deleteChecked(allUsersRepo, updatedAccount.refName);
        gitRefUpdated.fire(allUsersName, ru, ReceiveCommand.Type.DELETE, null);
        accountsToSkipForReindex.add(Account.Id.fromRef(updatedAccount.refName));
        continue;
      }
      // These updates are all for different refs (because batches never update the same account
      // more than once), so there can be multiple commits in the same batch, all with the same base
      // revision in their AccountConfig.
      // We allow empty commits:
      // 1) When creating a new account, so that the user branch gets created with an empty commit
      // when no account properties are set and hence no
      // 'account.config' file will be created.
      // 2) When updating "refs/meta/external-ids", so that refs/users/* meta ref is updated too.
      // This allows to schedule reindexing of account transactionally on refs/users/* meta
      // updates.
      boolean allowEmptyCommit = externalIdsUpdated || updatedAccount.created;
      commitAccountConfig(
          updatedAccount.message,
          allUsersRepo,
          batchRefUpdate,
          updatedAccount.accountConfig,
          allowEmptyCommit);
    }

    RefUpdateUtil.executeChecked(batchRefUpdate, allUsersRepo);

    if (externalIdsUpdated) {
      accountsToSkipForReindex.addAll(getUpdatedAccountIds(batchRefUpdate));
      extIdNotesFactory.updateExternalIdCacheAndMaybeReindexAccounts(
          externalIdNotes, accountsToSkipForReindex);
    }

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

  private RevCommit commitExternalIdUpdates(
      String message, Repository allUsersRepo, BatchRefUpdate batchRefUpdate) throws IOException {
    try (MetaDataUpdate md = createMetaDataUpdate(message, allUsersRepo, batchRefUpdate)) {
      return externalIdNotes.commit(md);
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
    final String refName;
    final boolean created;
    final boolean deleted;

    UpdatedAccount(
        String message,
        AccountConfig accountConfig,
        CachedPreferences defaultPreferences,
        boolean created) {
      this(
          message,
          requireNonNull(accountConfig),
          defaultPreferences,
          accountConfig.getRefName(),
          created,
          false);
    }

    protected UpdatedAccount(
        String message,
        AccountConfig accountConfig,
        CachedPreferences defaultPreferences,
        String refName,
        boolean created,
        boolean deleted) {
      checkState(!Strings.isNullOrEmpty(message), "message for account update must be set");
      this.message = requireNonNull(message);
      this.accountConfig = accountConfig;
      this.defaultPreferences = defaultPreferences;
      this.refName = refName;
      this.created = created;
      this.deleted = deleted;
    }

    Optional<AccountState> getAccountState() throws IOException {
      return AccountsNoteDbImpl.getFromAccountConfig(
          externalIds, accountConfig, externalIdNotes, defaultPreferences);
    }
  }

  private class DeletedAccount extends UpdatedAccount {
    DeletedAccount(String message, String refName) {
      super(message, null, null, refName, false, true);
    }
  }
}
