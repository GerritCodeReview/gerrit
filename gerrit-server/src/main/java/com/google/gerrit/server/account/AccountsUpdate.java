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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

/** Updates accounts. */
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
    private final AllUsersName allUsersName;
    private final Provider<PersonIdent> serverIdent;

    @Inject
    public Server(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        @GerritPersonIdent Provider<PersonIdent> serverIdent) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.serverIdent = serverIdent;
    }

    public AccountsUpdate create() {
      PersonIdent i = serverIdent.get();
      return new AccountsUpdate(repoManager, allUsersName, i, i);
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
    private final AllUsersName allUsersName;
    private final Provider<PersonIdent> serverIdent;
    private final Provider<IdentifiedUser> identifiedUser;

    @Inject
    public User(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        @GerritPersonIdent Provider<PersonIdent> serverIdent,
        Provider<IdentifiedUser> identifiedUser) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.serverIdent = serverIdent;
      this.identifiedUser = identifiedUser;
    }

    public AccountsUpdate create() {
      PersonIdent i = serverIdent.get();
      return new AccountsUpdate(
          repoManager, allUsersName, createPersonIdent(i, identifiedUser.get()), i);
    }

    private PersonIdent createPersonIdent(PersonIdent ident, IdentifiedUser user) {
      return user.newCommitterIdent(ident.getWhen(), ident.getTimeZone());
    }
  }

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent committerIdent;
  private final PersonIdent authorIdent;

  private AccountsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      PersonIdent committerIdent,
      PersonIdent authorIdent) {
    this.repoManager = checkNotNull(repoManager, "repoManager");
    this.allUsersName = checkNotNull(allUsersName, "allUsersName");
    this.committerIdent = checkNotNull(committerIdent, "committerIdent");
    this.authorIdent = checkNotNull(authorIdent, "authorIdent");
  }

  /**
   * Inserts a new account.
   *
   * @throws OrmDuplicateKeyException if the account already exists
   * @throws IOException if updating the user branch fails
   */
  public void insert(ReviewDb db, Account account) throws OrmException, IOException {
    db.accounts().insert(ImmutableSet.of(account));
    createUserBranch(account);
  }

  /**
   * Inserts or updates an account.
   *
   * <p>If the account already exists, it is overwritten, otherwise it is inserted.
   */
  public void upsert(ReviewDb db, Account account) throws OrmException, IOException {
    db.accounts().upsert(ImmutableSet.of(account));
    createUserBranchIfNeeded(account);
  }

  /** Updates the account. */
  public void update(ReviewDb db, Account account) throws OrmException {
    db.accounts().update(ImmutableSet.of(account));
  }

  /** Deletes the account. */
  public void delete(ReviewDb db, Account account) throws OrmException, IOException {
    db.accounts().delete(ImmutableSet.of(account));
    deleteUserBranch(account.getId());
  }

  /** Deletes the account. */
  public void deleteByKey(ReviewDb db, Account.Id accountId) throws OrmException, IOException {
    db.accounts().deleteKeys(ImmutableSet.of(accountId));
    deleteUserBranch(accountId);
  }

  private void createUserBranch(Account account) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName);
        ObjectInserter oi = repo.newObjectInserter()) {
      String refName = RefNames.refsUsers(account.getId());
      if (repo.exactRef(refName) != null) {
        throw new IOException(
            String.format(
                "User branch %s for newly created account %s already exists.",
                refName, account.getId().get()));
      }
      createUserBranch(repo, oi, committerIdent, authorIdent, account);
    }
  }

  private void createUserBranchIfNeeded(Account account) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName);
        ObjectInserter oi = repo.newObjectInserter()) {
      if (repo.exactRef(RefNames.refsUsers(account.getId())) == null) {
        createUserBranch(repo, oi, committerIdent, authorIdent, account);
      }
    }
  }

  public static void createUserBranch(
      Repository repo,
      ObjectInserter oi,
      PersonIdent committerIdent,
      PersonIdent authorIdent,
      Account account)
      throws IOException {
    ObjectId id =
        createInitialEmptyCommit(oi, committerIdent, authorIdent, account.getRegisteredOn());

    String refName = RefNames.refsUsers(account.getId());
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
      deleteUserBranch(repo, committerIdent, accountId);
    }
  }

  public static void deleteUserBranch(
      Repository repo, PersonIdent refLogIdent, Account.Id accountId) throws IOException {
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
  }
}
