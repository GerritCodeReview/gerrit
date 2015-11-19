// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.util.Collections;

@Singleton
public class StarredChangesUtil {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final NotesMigration migration;
  private final Provider<ReviewDb> dbProvider;
  private final PersonIdent serverIdent;
  private final DynamicItem<StarredChangesCache> starredChangesCache;

  @Inject
  StarredChangesUtil(GitRepositoryManager repoManager,
      AllUsersName allUsers,
      NotesMigration migration,
      Provider<ReviewDb> dbProvider,
      @GerritPersonIdent PersonIdent serverIdent,
      DynamicItem<StarredChangesCache> starredChangesCache) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.migration = migration;
    this.dbProvider = dbProvider;
    this.serverIdent = serverIdent;
    this.starredChangesCache = starredChangesCache;
  }

  public boolean isStarred(Account.Id accountId, Change.Id changeId) {
    return starredChangesCache.get().isStarred(accountId, changeId);
  }

  public Iterable<Change.Id> byAccount(Account.Id accountId) {
    return starredChangesCache.get().byAccount(accountId);
  }

  public Iterable<Account.Id> byChange(Change.Id changeId) {
    return starredChangesCache.get().byChange(changeId);
  }

  public void star(Account.Id accountId, Change.Id changeId)
      throws OrmException {
    dbProvider.get().starredChanges()
        .insert(Collections.singleton(new StarredChange(
            new StarredChange.Key(accountId, changeId))));
    starredChangesCache.get().star(accountId, changeId);
    if (!migration.writeChanges()) {
      return;
    }
    try (Repository repo = repoManager.openMetadataRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(
          RefNames.refsStarredChanges(accountId, changeId));
      u.setExpectedOldObjectId(ObjectId.zeroId());
      u.setNewObjectId(emptyTree(repo));
      u.setRefLogIdent(serverIdent);
      u.setRefLogMessage("Star change " + changeId.get(), false);
      RefUpdate.Result result = u.update(rw);
      switch (result) {
        case NEW:
          return;
        default:
          throw new OrmException(
              String.format("Star change %d for account %d failed: %s",
                  changeId.get(), accountId.get(), result.name()));
      }
    } catch (IOException e) {
      throw new OrmException(
          String.format("Star change %d for account %d failed",
              changeId.get(), accountId.get()), e);
    }
  }

  private static ObjectId emptyTree(Repository repo) throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId id = oi.insert(Constants.OBJ_TREE, new byte[] {});
      oi.flush();
      return id;
    }
  }

  public void unstar(Account.Id accountId, Change.Id changeId)
      throws OrmException {
    dbProvider.get().starredChanges()
        .delete(Collections.singleton(new StarredChange(
            new StarredChange.Key(accountId, changeId))));
    starredChangesCache.get().unstar(accountId, changeId);
    if (!migration.writeChanges()) {
      return;
    }
    try (Repository repo = repoManager.openMetadataRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(
          RefNames.refsStarredChanges(accountId, changeId));
      u.setForceUpdate(true);
      u.setRefLogIdent(serverIdent);
      u.setRefLogMessage("Unstar change " + changeId.get(), true);
      RefUpdate.Result result = u.delete();
      switch (result) {
        case FORCED:
          return;
        default:
          throw new OrmException(
              String.format("Unstar change %d for account %d failed: %s",
                  changeId.get(), accountId.get(), result.name()));
      }
    } catch (IOException e) {
      throw new OrmException(
          String.format("Unstar change %d for account %d failed",
              changeId.get(), accountId.get()), e);
    }
  }

  public void unstarAll(Change.Id changeId) throws OrmException {
    dbProvider.get().starredChanges().delete(
        dbProvider.get().starredChanges().byChange(changeId));
    Iterable<Account.Id> accounts =
        starredChangesCache.get().unstarAll(changeId);
    if (!migration.writeChanges()) {
      return;
    }
    try (Repository repo = repoManager.openMetadataRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      batchUpdate.setAllowNonFastForwards(true);
      batchUpdate.setRefLogIdent(serverIdent);
      batchUpdate.setRefLogMessage("Unstar change " + changeId.get(), true);
      for (Account.Id accountId : accounts) {
        String refName = RefNames.refsStarredChanges(accountId, changeId);
        Ref ref = repo.getRefDatabase().getRef(refName);
        batchUpdate.addCommand(new ReceiveCommand(ref.getObjectId(),
            ObjectId.zeroId(), refName));
      }
      batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
      for (ReceiveCommand command : batchUpdate.getCommands()) {
        if (command.getResult() != ReceiveCommand.Result.OK) {
          throw new IOException(String.format(
              "Unstar change %d failed, ref %s could not be deleted: %s",
              changeId.get(), command.getRefName(), command.getResult()));
        }
      }
    } catch (IOException e) {
      throw new OrmException(
          String.format("Unstar change %d failed", changeId.get()), e);
    }
  }
}
