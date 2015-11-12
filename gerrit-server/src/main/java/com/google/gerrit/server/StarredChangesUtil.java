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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Singleton
public class StarredChangesUtil {
  private static final Logger log =
      LoggerFactory.getLogger(StarredChangesUtil.class);

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final NotesMigration migration;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  StarredChangesUtil(GitRepositoryManager repoManager,
      AllUsersName allUsers,
      NotesMigration migration,
      Provider<ReviewDb> dbProvider) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.migration = migration;
    this.dbProvider = dbProvider;
  }

  public void star(IdentifiedUser user, Change.Id changeId)
      throws OrmException {
    dbProvider.get().starredChanges()
        .insert(Collections.singleton(new StarredChange(
            new StarredChange.Key(user.getAccountId(), changeId))));
    if (!migration.writeChanges()) {
      return;
    }
    try (Repository repo = repoManager.openMetadataRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(
          RefNames.refsStarredChanges(user.getAccountId(), changeId));
      u.setExpectedOldObjectId(ObjectId.zeroId());
      u.setNewObjectId(emptyTree(repo));
      u.setRefLogIdent(user.newRefLogIdent());
      u.setRefLogMessage("Star change " + changeId.get(), false);
      RefUpdate.Result result = u.update(rw);
      switch (result) {
        case NEW:
          return;
        default:
          throw new OrmException(
              String.format("Star change %d for account %d failed: %s",
                  changeId.get(), user.getAccountId().get(), result.name()));
      }
    } catch (IOException e) {
      throw new OrmException(
          String.format("Star change %d for account %d failed",
              changeId.get(), user.getAccountId().get()), e);
    }
  }

  private static ObjectId emptyTree(Repository repo) throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId id = oi.insert(Constants.OBJ_TREE, new byte[] {});
      oi.flush();
      return id;
    }
  }

  public void unstar(IdentifiedUser user, Change.Id changeId)
      throws OrmException {
    dbProvider.get().starredChanges()
        .delete(Collections.singleton(new StarredChange(
            new StarredChange.Key(user.getAccountId(), changeId))));
    if (!migration.writeChanges()) {
      return;
    }
    try (Repository repo = repoManager.openMetadataRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(
          RefNames.refsStarredChanges(user.getAccountId(), changeId));
      u.setForceUpdate(true);
      u.setRefLogIdent(user.newRefLogIdent());
      u.setRefLogMessage("Unstar change " + changeId.get(), true);
      RefUpdate.Result result = u.delete();
      switch (result) {
        case FORCED:
          return;
        default:
          throw new OrmException(
              String.format("Unstar change %d for account %d failed: %s",
                  changeId.get(), user.getAccountId().get(), result.name()));
      }
    } catch (IOException e) {
      throw new OrmException(
          String.format("Unstar change %d for account %d failed",
              changeId.get(), user.getAccountId().get()), e);
    }
  }

  public void unstarAll(IdentifiedUser user, Change.Id changeId)
      throws OrmException {
    dbProvider.get().starredChanges().delete(
        dbProvider.get().starredChanges().byChange(changeId));
    if (!migration.writeChanges()) {
      return;
    }
    try (Repository repo = repoManager.openMetadataRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      batchUpdate.setAllowNonFastForwards(true);
      batchUpdate.setRefLogIdent(user.newRefLogIdent());
      batchUpdate.setRefLogMessage("Unstar change " + changeId.get(), true);
      for (Account.Id accountId : byChange(changeId)) {
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

  public Iterable<Account.Id> byChange(final Change.Id changeId)
      throws OrmException {
    if (!migration.readChanges()) {
      return FluentIterable
          .from(dbProvider.get().starredChanges().byChange(changeId))
          .transform(new Function<StarredChange, Account.Id>() {
            @Override
            public Account.Id apply(StarredChange in) {
              return in.getAccountId();
            }
          });
    }
    return FluentIterable.from(getRefNames(RefNames.REFS_STARRED_CHANGES))
        .filter(new Predicate<String>() {
          @Override
          public boolean apply(String refPart) {
            return refPart.endsWith("/" + changeId.get());
          }
        })
        .transform(new Function<String, Account.Id>() {
          @Override
          public Account.Id apply(String refPart) {
            return Account.Id.fromRefPart(refPart);
          }
        });
  }

  public ResultSet<Change.Id> query(Account.Id accountId) {
    try {
      if (!migration.readChanges()) {
        return new ChangeIdResultSet(
            dbProvider.get().starredChanges().byAccount(accountId));
      }

      return new ListResultSet<>(FluentIterable
          .from(getRefNames(RefNames.refsStarredChangesPrefix(accountId)))
          .transform(new Function<String, Change.Id>() {
            @Override
            public Change.Id apply(String changeId) {
              return Change.Id.parse(changeId);
            }
          }).toList());
    } catch (OrmException | RuntimeException e) {
      log.warn(String.format("Cannot query starred changes for account %d",
          accountId.get()), e);
      List<Change.Id> empty = Collections.emptyList();
      return new ListResultSet<>(empty);
    }
  }

  private Set<String> getRefNames(String prefix) throws OrmException {
    try (Repository repo = repoManager.openMetadataRepository(allUsers)) {
      RefDatabase refDb = repo.getRefDatabase();
      return refDb.getRefs(prefix).keySet();
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  private static class ChangeIdResultSet implements ResultSet<Change.Id> {
    private static final Function<StarredChange, Change.Id>
        STARRED_CHANGE_TO_CHANGE_ID =
            new Function<StarredChange, Change.Id>() {
              @Override
              public Change.Id apply(StarredChange starredChange) {
                return starredChange.getChangeId();
              }
            };

    private final ResultSet<StarredChange> starredChangesResultSet;

    ChangeIdResultSet(ResultSet<StarredChange> starredChangesResultSet) {
      this.starredChangesResultSet = starredChangesResultSet;
    }

    @Override
    public Iterator<Change.Id> iterator() {
      return Iterators.transform(starredChangesResultSet.iterator(),
          STARRED_CHANGE_TO_CHANGE_ID);
    }

    @Override
    public List<Change.Id> toList() {
      return Lists.transform(starredChangesResultSet.toList(),
          STARRED_CHANGE_TO_CHANGE_ID);
    }

    @Override
    public void close() {
      starredChangesResultSet.close();
    }
  }
}
