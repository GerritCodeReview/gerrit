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
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
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

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class StarredChangesUtil {
  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;
    private final NotesMigration migration;

    @Inject
    Factory(GitRepositoryManager repoManager,
        AllUsersNameProvider allUsersProvider,
        NotesMigration migration) {
      this.repoManager = repoManager;
      this.allUsers = allUsersProvider.get();
      this.migration = migration;
    }

    public StarredChangesUtil create(Provider<ReviewDb> dbProvider) {
      return new StarredChangesUtil(repoManager, allUsers, migration,
          dbProvider);
    }
  }

  @Singleton
  public static class RequestFactory {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;
    private final NotesMigration migration;
    private final Provider<ReviewDb> dbProvider;

    @Inject
    RequestFactory(GitRepositoryManager repoManager,
        AllUsersNameProvider allUsersProvider,
        NotesMigration migration,
        Provider<ReviewDb> dbProvider) {
      this.repoManager = repoManager;
      this.allUsers = allUsersProvider.get();
      this.migration = migration;
      this.dbProvider = dbProvider;
    }

    public StarredChangesUtil create() {
      return new StarredChangesUtil(repoManager, allUsers, migration,
          dbProvider);
    }
  }

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final NotesMigration migration;
  private final Provider<ReviewDb> dbProvider;

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
    if (migration.writeChanges()) {
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
          default: {
            throw new OrmException(
                String.format("Star change %d for account %d failed: %s",
                    changeId.get(), user.getAccountId().get(), result.name()));
          }
        }
      } catch (IOException e) {
        throw new OrmException(
            String.format("Star change %d for account %d failed",
                changeId.get(), user.getAccountId().get()), e);
      }
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
    if (migration.writeChanges()) {
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
  }

  public void unstarAll(IdentifiedUser user, Change.Id changeId) throws OrmException {
    dbProvider.get().starredChanges().delete(
        dbProvider.get().starredChanges().byChange(changeId));
    if (migration.writeChanges()) {
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
  }

  public ImmutableSet<Account.Id> byChange(final Change.Id changeId)
      throws OrmException {
    if (!migration.readChanges()) {
      return FluentIterable
          .from(dbProvider.get().starredChanges().byChange(changeId))
          .transform(new Function<StarredChange, Account.Id>() {
            @Override
            public Account.Id apply(StarredChange in) {
              return in.getAccountId();
            }
          }).toSet();
    }
    return FluentIterable.from(getRefNames(RefNames.REFS_STARRED_CHANGES))
        .filter(new Predicate<String>() {
            @Override
            public boolean apply(String refPart) {
              return refPart.endsWith("-" + changeId.get());
            }
        })
        .transform(new Function<String, Account.Id>() {
            @Override
            public Account.Id apply(String refPart) {
              return Account.Id.fromRefPart(refPart);
            }
          })
        .toSet();
  }

  public Query query(Account.Id accountId) throws OrmException {
    if (!migration.readChanges()) {
      return new DbQuery(dbProvider.get().starredChanges().byAccount(accountId));
    } else {
      final String prefix = RefNames.refsStarredChangesPrefix(accountId);
      return new NotedbQuery(FluentIterable.from(getRefNames(prefix))
          .transform(new Function<String, Change.Id>() {
              @Override
              public Change.Id apply(String changeId) {
                return Change.Id.parse(changeId);
              }
            })
          .toSet());
    }
  }

  private Set<String> getRefNames(String prefix)
      throws OrmException {
    try (Repository repo = repoManager.openMetadataRepository(allUsers)) {
      RefDatabase refDb = repo.getRefDatabase();
      return refDb.getRefs(prefix).keySet();
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  public interface Query {
    public ImmutableSet<Change.Id> list();
    public void abort();
  }

  private static class DbQuery implements Query {
    private ResultSet<StarredChange> query;

    DbQuery(ResultSet<StarredChange> query) {
      this.query = query;
    }

    @Override
    public ImmutableSet<Change.Id> list() {
      return FluentIterable.from(query)
          .transform(new Function<StarredChange, Change.Id>() {
              @Override
              public Change.Id apply(StarredChange in) {
                return in.getChangeId();
              }
            })
          .toSet();
    }

    @Override
    public void abort() {
      query.close();
    }
  }

  private static class NotedbQuery implements Query {
    private final ImmutableSet<Id> starredChanges;

    NotedbQuery(ImmutableSet<Change.Id> starredChanges) {
      this.starredChanges = starredChanges;
    }

    @Override
    public ImmutableSet<Change.Id> list() {
      return starredChanges;
    }

    @Override
    public void abort() {
    }
  }
}
