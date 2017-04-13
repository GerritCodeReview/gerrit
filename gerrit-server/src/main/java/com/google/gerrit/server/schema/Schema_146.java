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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.SQLException;
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Make sure that for every account a user branch exists that has an initial empty commit with the
 * registration date as commit time.
 *
 * <p>For accounts that don't have a user branch yet the user branch is created with an initial
 * empty commit that has the registration date as commit time.
 *
 * <p>For accounts that already have a user branch the user branch is rewritten and an initial empty
 * commit with the registration date as commit time is inserted (if such a commit doesn't exist
 * yet).
 */
public class Schema_146 extends SchemaVersion {
  private static final String CREATE_ACCOUNT_MSG = "Create Account";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent serverIdent;

  @Inject
  Schema_146(
      Provider<Schema_145> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverIdent) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverIdent = serverIdent;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId emptyTree = emptyTree(oi);

      for (Account account : db.accounts().all()) {
        String refName = RefNames.refsUsers(account.getId());
        Ref ref = repo.exactRef(refName);
        if (ref != null) {
          rewriteUserBranch(repo, rw, oi, emptyTree, ref, account);
        } else {
          AccountsUpdate.createUserBranch(repo, oi, serverIdent, serverIdent, account);
        }
      }
    } catch (IOException e) {
      throw new OrmException("Failed to rewrite user branches.", e);
    }
  }

  private void rewriteUserBranch(
      Repository repo, RevWalk rw, ObjectInserter oi, ObjectId emptyTree, Ref ref, Account account)
      throws IOException {
    ObjectId current = createInitialEmptyCommit(oi, emptyTree, account.getRegisteredOn());

    rw.reset();
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.REVERSE, true);
    rw.markStart(rw.parseCommit(ref.getObjectId()));

    RevCommit c;
    while ((c = rw.next()) != null) {
      if (isInitialEmptyCommit(emptyTree, c)) {
        return;
      }

      CommitBuilder cb = new CommitBuilder();
      cb.setParentId(current);
      cb.setTreeId(c.getTree());
      cb.setAuthor(c.getAuthorIdent());
      cb.setCommitter(c.getCommitterIdent());
      cb.setMessage(c.getFullMessage());
      cb.setEncoding(c.getEncoding());
      current = oi.insert(cb);
    }

    oi.flush();

    RefUpdate ru = repo.updateRef(ref.getName());
    ru.setExpectedOldObjectId(ref.getObjectId());
    ru.setNewObjectId(current);
    ru.setForceUpdate(true);
    ru.setRefLogIdent(serverIdent);
    ru.setRefLogMessage(getClass().getSimpleName(), true);
    Result result = ru.update();
    if (result != Result.FORCED) {
      throw new IOException(
          String.format("Failed to update ref %s: %s", ref.getName(), result.name()));
    }
  }

  private ObjectId createInitialEmptyCommit(
      ObjectInserter oi, ObjectId emptyTree, Timestamp registrationDate) throws IOException {
    PersonIdent ident = new PersonIdent(serverIdent, registrationDate);

    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(emptyTree);
    cb.setCommitter(ident);
    cb.setAuthor(ident);
    cb.setMessage(CREATE_ACCOUNT_MSG);
    return oi.insert(cb);
  }

  private boolean isInitialEmptyCommit(ObjectId emptyTree, RevCommit c) {
    return c.getParentCount() == 0
        && c.getTree().equals(emptyTree)
        && c.getShortMessage().equals(CREATE_ACCOUNT_MSG);
  }

  private static ObjectId emptyTree(ObjectInserter oi) throws IOException {
    return oi.insert(Constants.OBJ_TREE, new byte[] {});
  }
}
