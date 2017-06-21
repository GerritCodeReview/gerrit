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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.index.change.ReindexAfterRefUpdate;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

/**
 * Updates accounts.
 *
 * <p>The account updates are written to both ReviewDb and NoteDb.
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
    private final OutgoingEmailValidator emailValidator;
    private final Provider<PersonIdent> serverIdent;
    private final Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory;

    @Inject
    public Server(
        GitRepositoryManager repoManager,
        GitReferenceUpdated gitRefUpdated,
        AllUsersName allUsersName,
        OutgoingEmailValidator emailValidator,
        @GerritPersonIdent Provider<PersonIdent> serverIdent,
        Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory) {
      this.repoManager = repoManager;
      this.gitRefUpdated = gitRefUpdated;
      this.allUsersName = allUsersName;
      this.emailValidator = emailValidator;
      this.serverIdent = serverIdent;
      this.metaDataUpdateServerFactory = metaDataUpdateServerFactory;
    }

    public AccountsUpdate create() {
      PersonIdent i = serverIdent.get();
      return new AccountsUpdate(
          repoManager,
          gitRefUpdated,
          null,
          allUsersName,
          emailValidator,
          i,
          () -> metaDataUpdateServerFactory.get().create(allUsersName));
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
    private final OutgoingEmailValidator emailValidator;
    private final Provider<PersonIdent> serverIdent;
    private final Provider<IdentifiedUser> identifiedUser;
    private final Provider<MetaDataUpdate.User> metaDataUpdateUserFactory;

    @Inject
    public User(
        GitRepositoryManager repoManager,
        GitReferenceUpdated gitRefUpdated,
        AllUsersName allUsersName,
        OutgoingEmailValidator emailValidator,
        @GerritPersonIdent Provider<PersonIdent> serverIdent,
        Provider<IdentifiedUser> identifiedUser,
        Provider<MetaDataUpdate.User> metaDataUpdateUserFactory) {
      this.repoManager = repoManager;
      this.gitRefUpdated = gitRefUpdated;
      this.allUsersName = allUsersName;
      this.serverIdent = serverIdent;
      this.emailValidator = emailValidator;
      this.identifiedUser = identifiedUser;
      this.metaDataUpdateUserFactory = metaDataUpdateUserFactory;
    }

    public AccountsUpdate create() {
      IdentifiedUser user = identifiedUser.get();
      PersonIdent i = serverIdent.get();
      return new AccountsUpdate(
          repoManager,
          gitRefUpdated,
          user,
          allUsersName,
          emailValidator,
          createPersonIdent(i, user),
          () -> metaDataUpdateUserFactory.get().create(allUsersName));
    }

    private PersonIdent createPersonIdent(PersonIdent ident, IdentifiedUser user) {
      return user.newCommitterIdent(ident.getWhen(), ident.getTimeZone());
    }
  }

  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;
  @Nullable private final IdentifiedUser currentUser;
  private final AllUsersName allUsersName;
  private final OutgoingEmailValidator emailValidator;
  private final PersonIdent committerIdent;
  private final MetaDataUpdateFactory metaDataUpdateFactory;

  private AccountsUpdate(
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      @Nullable IdentifiedUser currentUser,
      AllUsersName allUsersName,
      OutgoingEmailValidator emailValidator,
      PersonIdent committerIdent,
      MetaDataUpdateFactory metaDataUpdateFactory) {
    this.repoManager = checkNotNull(repoManager, "repoManager");
    this.gitRefUpdated = checkNotNull(gitRefUpdated, "gitRefUpdated");
    this.currentUser = currentUser;
    this.allUsersName = checkNotNull(allUsersName, "allUsersName");
    this.emailValidator = checkNotNull(emailValidator, "emailValidator");
    this.committerIdent = checkNotNull(committerIdent, "committerIdent");
    this.metaDataUpdateFactory = checkNotNull(metaDataUpdateFactory, "metaDataUpdateFactory");
  }

  /**
   * Inserts a new account.
   *
   * @param db ReviewDb
   * @param accountId ID of the new account
   * @param init consumer to populate the new account
   * @return the newly created account
   * @throws OrmException if updating the database fails
   * @throws OrmDuplicateKeyException if the account already exists
   * @throws IOException if updating the user branch fails
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public Account insert(ReviewDb db, Account.Id accountId, Consumer<Account> init)
      throws OrmException, IOException, ConfigInvalidException {
    AccountConfig accountConfig = read(accountId);
    Account account = accountConfig.getNewAccount();
    init.accept(account);

    // Create in ReviewDb
    db.accounts().insert(ImmutableSet.of(account));

    // Create in NoteDb
    commitNew(accountConfig);
    return account;
  }

  /**
   * Updates the account.
   *
   * <p>Changing the registration date of an account is not supported.
   *
   * @param db ReviewDb
   * @param account the account
   * @throws OrmException if updating the database fails
   * @throws IOException if updating the user branch fails
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public void update(ReviewDb db, Account account)
      throws OrmException, IOException, ConfigInvalidException {
    // Update in ReviewDb
    db.accounts().update(ImmutableSet.of(account));

    // Update in NoteDb
    AccountConfig accountConfig = read(account.getId());
    accountConfig.setAccount(account);
    commit(accountConfig);
  }

  /**
   * Gets the account and updates it atomically.
   *
   * <p>Changing the registration date of an account is not supported.
   *
   * @param db ReviewDb
   * @param accountId ID of the account
   * @param consumer consumer to update the account, only invoked if the account exists
   * @return the updated account, {@code null} if the account doesn't exist
   * @throws OrmException if updating the database fails
   * @throws IOException if updating the user branch fails
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public Account atomicUpdate(ReviewDb db, Account.Id accountId, Consumer<Account> consumer)
      throws OrmException, IOException, ConfigInvalidException {
    return atomicUpdate(db, accountId, ImmutableList.of(consumer));
  }

  /**
   * Gets the account and updates it atomically.
   *
   * <p>Changing the registration date of an account is not supported.
   *
   * @param db ReviewDb
   * @param accountId ID of the account
   * @param consumers consumers to update the account, only invoked if the account exists
   * @return the updated account, {@code null} if the account doesn't exist
   * @throws OrmException if updating the account fails
   */
  public Account atomicUpdate(ReviewDb db, Account.Id accountId, List<Consumer<Account>> consumers)
      throws OrmException, IOException, ConfigInvalidException {
    if (consumers.isEmpty()) {
      return null;
    }

    // Update in ReviewDb
    db.accounts()
        .atomicUpdate(
            accountId,
            a -> {
              consumers.stream().forEach(c -> c.accept(a));
              return a;
            });

    // Update in NoteDb
    AccountConfig accountConfig = read(accountId);
    Account account = accountConfig.getAccount();
    consumers.stream().forEach(c -> c.accept(account));
    commit(accountConfig);

    return account;
  }

  /**
   * Deletes the account.
   *
   * @param db ReviewDb
   * @param account the account that should be deleted
   * @throws OrmException if updating the database fails
   * @throws IOException if updating the user branch fails
   */
  public void delete(ReviewDb db, Account account) throws OrmException, IOException {
    // Delete in ReviewDb
    db.accounts().delete(ImmutableSet.of(account));

    // Delete in NoteDb
    deleteUserBranch(account.getId());
  }

  /**
   * Deletes the account.
   *
   * @param db ReviewDb
   * @param accountId the ID of the account that should be deleted
   * @throws OrmException if updating the database fails
   * @throws IOException if updating the user branch fails
   */
  public void deleteByKey(ReviewDb db, Account.Id accountId) throws OrmException, IOException {
    // Delete in ReviewDb
    db.accounts().deleteKeys(ImmutableSet.of(accountId));

    // Delete in NoteDb
    deleteUserBranch(accountId);
  }

  public static void createUserBranch(
      Repository repo,
      Project.NameKey project,
      GitReferenceUpdated gitRefUpdated,
      @Nullable IdentifiedUser user,
      ObjectInserter oi,
      PersonIdent committerIdent,
      PersonIdent authorIdent,
      Account.Id accountId,
      Timestamp registeredOn)
      throws IOException {
    ObjectId id = createInitialEmptyCommit(oi, committerIdent, authorIdent, registeredOn);

    String refName = RefNames.refsUsers(accountId);
    RefUpdate ru = repo.updateRef(refName);
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(id);
    ru.setForceUpdate(true);
    ru.setRefLogIdent(committerIdent);
    ru.setRefLogMessage("Create Account", true);
    Result result = ru.update();
    if (result != Result.NEW) {
      throw new IOException(String.format("Failed to update ref %s: %s", refName, result.name()));
    }
    gitRefUpdated.fire(project, ru, user != null ? user.getAccount() : null);
  }

  private static ObjectId createInitialEmptyCommit(
      ObjectInserter oi,
      PersonIdent committerIdent,
      PersonIdent authorIdent,
      Timestamp registrationDate)
      throws IOException {
    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(emptyTree(oi));
    cb.setCommitter(new PersonIdent(committerIdent, registrationDate));
    cb.setAuthor(new PersonIdent(authorIdent, registrationDate));
    cb.setMessage("Create Account");
    ObjectId id = oi.insert(cb);
    oi.flush();
    return id;
  }

  private static ObjectId emptyTree(ObjectInserter oi) throws IOException {
    return oi.insert(Constants.OBJ_TREE, new byte[] {});
  }

  private void deleteUserBranch(Account.Id accountId) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      deleteUserBranch(repo, allUsersName, gitRefUpdated, currentUser, committerIdent, accountId);
    }
  }

  public static void deleteUserBranch(
      Repository repo,
      Project.NameKey project,
      GitReferenceUpdated gitRefUpdated,
      @Nullable IdentifiedUser user,
      PersonIdent refLogIdent,
      Account.Id accountId)
      throws IOException {
    String refName = RefNames.refsUsers(accountId);
    Ref ref = repo.exactRef(refName);
    if (ref == null) {
      return;
    }

    RefUpdate ru = repo.updateRef(refName);
    ru.setExpectedOldObjectId(ref.getObjectId());
    ru.setNewObjectId(ObjectId.zeroId());
    ru.setForceUpdate(true);
    ru.setRefLogIdent(refLogIdent);
    ru.setRefLogMessage("Delete Account", true);
    Result result = ru.delete();
    if (result != Result.FORCED) {
      throw new IOException(String.format("Failed to delete ref %s: %s", refName, result.name()));
    }
    gitRefUpdated.fire(project, ru, user != null ? user.getAccount() : null);
  }

  private AccountConfig read(Account.Id accountId) throws IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      AccountConfig accountConfig = new AccountConfig(emailValidator, accountId);
      accountConfig.load(repo);
      return accountConfig;
    }
  }

  private void commitNew(AccountConfig accountConfig) throws IOException {
    // When creating a new account we must allow empty commits so that the user branch gets created
    // with an empty commit when no account properties are set and hence no 'account.config' file
    // will be created.
    commit(accountConfig, true);
  }

  private void commit(AccountConfig accountConfig) throws IOException {
    commit(accountConfig, false);
  }

  private void commit(AccountConfig accountConfig, boolean allowEmptyCommit) throws IOException {
    try (MetaDataUpdate md = metaDataUpdateFactory.create()) {
      md.setAllowEmpty(allowEmptyCommit);
      accountConfig.commit(md);
    }
  }

  @FunctionalInterface
  private static interface MetaDataUpdateFactory {
    MetaDataUpdate create() throws IOException;
  }
}
