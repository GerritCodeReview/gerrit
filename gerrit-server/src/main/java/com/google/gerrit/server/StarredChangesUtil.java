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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
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
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
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
import java.util.SortedSet;
import java.util.TreeSet;

@Singleton
public class StarredChangesUtil {
  private static final Logger log =
      LoggerFactory.getLogger(StarredChangesUtil.class);

  private static final String DEFAULT_LABEL = "star";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final NotesMigration migration;
  private final Provider<ReviewDb> dbProvider;
  private final PersonIdent serverIdent;
  private final ChangeIndexer indexer;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  StarredChangesUtil(GitRepositoryManager repoManager,
      AllUsersName allUsers,
      NotesMigration migration,
      Provider<ReviewDb> dbProvider,
      @GerritPersonIdent PersonIdent serverIdent,
      ChangeIndexer indexer,
      Provider<InternalChangeQuery> queryProvider) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.migration = migration;
    this.dbProvider = dbProvider;
    this.serverIdent = serverIdent;
    this.indexer = indexer;
    this.queryProvider = queryProvider;
  }

  public void star(Account.Id accountId, Project.NameKey project,
      Change.Id changeId) throws OrmException, IOException {
    dbProvider.get().starredChanges()
        .insert(Collections.singleton(new StarredChange(
            new StarredChange.Key(accountId, changeId))));
    if (!migration.writeAccounts()) {
      indexer.index(dbProvider.get(), project, changeId);
      return;
    }
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String refName = RefNames.refsStarredChanges(changeId, accountId);
      ObjectId oldObjectId = getObjectId(repo, refName);
      SortedSet<String> labels = readLabels(repo, oldObjectId);
      labels.add(DEFAULT_LABEL);
      updateLabels(repo, refName, oldObjectId, labels);
      indexer.index(dbProvider.get(), project, changeId);
    } catch (IOException e) {
      throw new OrmException(
          String.format("Star change %d for account %d failed",
              changeId.get(), accountId.get()), e);
    }
  }

  public void unstar(Account.Id accountId, Project.NameKey project,
      Change.Id changeId) throws OrmException, IOException {
    dbProvider.get().starredChanges()
        .delete(Collections.singleton(new StarredChange(
            new StarredChange.Key(accountId, changeId))));
    if (!migration.writeAccounts()) {
      indexer.index(dbProvider.get(), project, changeId);
      return;
    }
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(
          RefNames.refsStarredChanges(changeId, accountId));
      u.setForceUpdate(true);
      u.setRefLogIdent(serverIdent);
      u.setRefLogMessage("Unstar change " + changeId.get(), true);
      RefUpdate.Result result = u.delete();
      switch (result) {
        case FORCED:
          indexer.index(dbProvider.get(), project, changeId);
          return;
        case FAST_FORWARD:
        case IO_FAILURE:
        case LOCK_FAILURE:
        case NEW:
        case NOT_ATTEMPTED:
        case NO_CHANGE:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case RENAMED:
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

  public void unstarAll(Project.NameKey project, Change.Id changeId)
      throws OrmException, IOException, NoSuchChangeException {
    dbProvider.get().starredChanges().delete(
        dbProvider.get().starredChanges().byChange(changeId));
    if (!migration.writeAccounts()) {
      indexer.index(dbProvider.get(), project, changeId);
      return;
    }
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      batchUpdate.setAllowNonFastForwards(true);
      batchUpdate.setRefLogIdent(serverIdent);
      batchUpdate.setRefLogMessage("Unstar change " + changeId.get(), true);
      for (Account.Id accountId : byChangeFromIndex(changeId)) {
        String refName = RefNames.refsStarredChanges(changeId, accountId);
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
      indexer.index(dbProvider.get(), project, changeId);
    } catch (IOException e) {
      throw new OrmException(
          String.format("Unstar change %d failed", changeId.get()), e);
    }
  }

  public Iterable<Account.Id> byChange(Change.Id changeId)
      throws OrmException {
    if (!migration.readAccounts()) {
      return FluentIterable
          .from(dbProvider.get().starredChanges().byChange(changeId))
          .transform(new Function<StarredChange, Account.Id>() {
            @Override
            public Account.Id apply(StarredChange in) {
              return in.getAccountId();
            }
          });
    }
    return FluentIterable
        .from(getRefNames(RefNames.refsStarredChangesPrefix(changeId)))
        .transform(new Function<String, Account.Id>() {
          @Override
          public Account.Id apply(String refPart) {
            return Account.Id.fromRefPart(refPart);
          }
        });
  }

  public Iterable<Account.Id> byChangeFromIndex(Change.Id changeId)
      throws OrmException, NoSuchChangeException {
    Set<String> fields = ImmutableSet.of(
        ChangeField.ID.getName(),
        ChangeField.STARREDBY.getName());
    List<ChangeData> changeData = queryProvider.get().setRequestedFields(fields)
        .byLegacyChangeId(changeId);
    if (changeData.size() != 1) {
      throw new NoSuchChangeException(changeId);
    }
    return changeData.get(0).starredBy();
  }

  public ResultSet<Change.Id> queryFromIndex(final Account.Id accountId) {
    try {
      if (!migration.readAccounts()) {
        return new ChangeIdResultSet(
            dbProvider.get().starredChanges().byAccount(accountId));
      }

      Set<String> fields = ImmutableSet.of(
          ChangeField.ID.getName(),
          ChangeField.STARREDBY.getName());
      List<ChangeData> changeData =
          queryProvider.get().setRequestedFields(fields).byIsStarred(accountId);
      return new ListResultSet<>(FluentIterable
          .from(changeData)
          .transform(new Function<ChangeData, Change.Id>() {
            @Override
            public Change.Id apply(ChangeData cd) {
              return cd.getId();
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
    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefDatabase refDb = repo.getRefDatabase();
      return refDb.getRefs(prefix).keySet();
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  private static ObjectId getObjectId(Repository repo, String refName)
      throws IOException {
    Ref ref = repo.exactRef(refName);
    return ref != null ? ref.getObjectId() : ObjectId.zeroId();
  }

  private static TreeSet<String> readLabels(Repository repo, ObjectId id)
      throws IOException {
    if (ObjectId.zeroId().equals(id)) {
      return new TreeSet<>();
    }

    try (ObjectReader reader = repo.newObjectReader()) {
      ObjectLoader obj = reader.open(id, Constants.OBJ_BLOB);
      TreeSet<String> labels = new TreeSet<>();
      Iterables.addAll(labels,
          Splitter.on(CharMatcher.whitespace()).omitEmptyStrings()
              .split(new String(obj.getCachedBytes(Integer.MAX_VALUE), UTF_8)));
      return labels;
    }
  }

  public static ObjectId writeLabels(Repository repo, SortedSet<String> labels)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId id = oi.insert(Constants.OBJ_BLOB,
          Joiner.on("\n").join(labels).getBytes(UTF_8));
      oi.flush();
      return id;
    }
  }

  private void updateLabels(Repository repo, String refName,
      ObjectId oldObjectId, SortedSet<String> labels)
          throws IOException, OrmException {
    try (RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(refName);
      u.setExpectedOldObjectId(oldObjectId);
      u.setForceUpdate(true);
      u.setNewObjectId(writeLabels(repo, labels));
      u.setRefLogIdent(serverIdent);
      u.setRefLogMessage("Update star labels", true);
      RefUpdate.Result result = u.update(rw);
      switch (result) {
        case NEW:
        case FORCED:
        case NO_CHANGE:
        case FAST_FORWARD:
          return;
        case IO_FAILURE:
        case LOCK_FAILURE:
        case NOT_ATTEMPTED:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case RENAMED:
          throw new OrmException(
              String.format("Update star labels on ref %s failed: %s", refName,
                  result.name()));
      }
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
