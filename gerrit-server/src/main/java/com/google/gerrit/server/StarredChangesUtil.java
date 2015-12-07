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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.StarredChangesCacheImpl.StarredChangesChacheProvider;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
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
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@Singleton
public class StarredChangesUtil {
  public static final String DEFAULT_LABEL = "default";
  public static final ImmutableSortedSet<String> DEFAULT_LABELS =
      ImmutableSortedSet.copyOf(Collections.singleton(DEFAULT_LABEL));

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final NotesMigration migration;
  private final Provider<ReviewDb> dbProvider;
  private final PersonIdent serverIdent;
  private final StarredChangesChacheProvider starredChangesCacheProvider;

  @Inject
  StarredChangesUtil(GitRepositoryManager repoManager,
      AllUsersName allUsers,
      NotesMigration migration,
      Provider<ReviewDb> dbProvider,
      @GerritPersonIdent PersonIdent serverIdent,
      StarredChangesChacheProvider starredChangesCacheProvider) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.migration = migration;
    this.dbProvider = dbProvider;
    this.serverIdent = serverIdent;
    this.starredChangesCacheProvider = starredChangesCacheProvider;
  }

  public boolean isStarred(Account.Id accountId, Change.Id changeId,
      String label) {
    return starredChangesCacheProvider.get().isStarred(
        accountId, changeId, label);
  }

  public ImmutableSortedSet<String> getLabels(Account.Id accountId,
      Change.Id changeId) {
    return starredChangesCacheProvider.get().getLabels(accountId, changeId);
  }

  public Iterable<Change.Id> byAccount(Account.Id accountId, String label) {
    return starredChangesCacheProvider.get().byAccount(accountId, label);
  }

  public ImmutableMultimap<Change.Id, String> byAccount(Account.Id accountId) {
    return starredChangesCacheProvider.get().byAccount(accountId);
  }

  public Iterable<Account.Id> byChange(Change.Id changeId, String label) {
    return starredChangesCacheProvider.get().byChange(changeId, label);
  }

  public ImmutableSortedSet<String> star(Account.Id accountId,
      Change.Id changeId, Set<String> labelsToAdd, Set<String> labelsToRemove)
          throws OrmException {
    if (labelsToAdd == null) {
      labelsToAdd = Collections.emptySet();
    }
    if (labelsToRemove == null) {
      labelsToRemove = Collections.emptySet();
    }
    StarredChangesCache cache = starredChangesCacheProvider.get();
    if (labelsToAdd.contains(DEFAULT_LABEL)) {
      try {
        dbProvider.get().starredChanges()
            .insert(Collections.singleton(new StarredChange(
                new StarredChange.Key(accountId, changeId))));
        Set<String> labels = new HashSet<>(cache.getLabels(accountId, changeId));
        labels.add(DEFAULT_LABEL);
        cache.star(accountId, changeId, labels);
      } catch(OrmDuplicateKeyException e) {
        // Ignored
      }
    }
    if (labelsToRemove.contains(DEFAULT_LABEL)) {
      dbProvider.get().starredChanges()
          .delete(Collections.singleton(new StarredChange(
              new StarredChange.Key(accountId, changeId))));
      Set<String> labels = new HashSet<>(cache.getLabels(accountId, changeId));
      labels.remove(DEFAULT_LABEL);
      cache.star(accountId, changeId, labels);
    }
    if (!migration.writeChanges()) {
      if (!DEFAULT_LABELS.containsAll(labelsToAdd)) {
        throw new OrmException("labeled stars not supported");
      }
      if (labelsToAdd.contains(DEFAULT_LABEL)) {
        return DEFAULT_LABELS;
      } else {
        return ImmutableSortedSet.of();
      }
    }
    try (Repository repo = repoManager.openMetadataRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      String refName = RefNames.refsStarredChanges(accountId, changeId);
      ObjectId oldObjectId = getObjectId(repo, refName);

      SortedSet<String> labels = readLabels(repo, oldObjectId);
      labels.addAll(labelsToAdd);
      labels.removeAll(labelsToRemove);

      if (labels.isEmpty()) {
        deleteRef(repo, refName, oldObjectId);
      } else {
        updateLabels(repo, refName, oldObjectId, labels);
      }

      starredChangesCacheProvider.get().star(accountId, changeId, labels);
      return ImmutableSortedSet.copyOf(labels);
    } catch (IOException e) {
      throw new OrmException(
          String.format("Update stars on change %d for account %d failed",
              changeId.get(), accountId.get()), e);
    }
  }

  public void unstarAll(Change.Id changeId) throws OrmException {
    dbProvider.get().starredChanges().delete(
        dbProvider.get().starredChanges().byChange(changeId));
    Iterable<Account.Id> accounts =
        starredChangesCacheProvider.get().unstarAll(changeId);
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

  public static SortedSet<String> readLabels(Repository repo, String refName)
      throws IOException {
    return readLabels(repo, getObjectId(repo, refName));
  }

  private static ObjectId getObjectId(Repository repo, String refName)
      throws IOException {
    Ref ref = repo.getRef(refName);
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
      Collections.addAll(labels,
          new String(obj.getCachedBytes(Integer.MAX_VALUE), UTF_8)
                .split("\n"));
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
        default:
          throw new OrmException(
              String.format("Update star labels on ref %s failed: %s", refName,
                  result.name()));
      }
    }
  }

  private void deleteRef(Repository repo, String refName, ObjectId oldObjectId)
      throws IOException, OrmException {
    RefUpdate u = repo.updateRef(refName);
    u.setForceUpdate(true);
    u.setExpectedOldObjectId(oldObjectId);
    u.setRefLogIdent(serverIdent);
    u.setRefLogMessage("Unstar change", true);
    RefUpdate.Result result = u.delete();
    switch (result) {
      case FORCED:
        return;
      default:
        throw new OrmException(String.format("Delete star ref %s failed: %s",
            refName, result.name()));
    }
  }
}
