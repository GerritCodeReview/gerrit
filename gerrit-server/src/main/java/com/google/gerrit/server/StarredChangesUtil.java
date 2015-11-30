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
import com.google.common.collect.ImmutableSortedSet;
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
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@Singleton
public class StarredChangesUtil {
  public static final String DEFAULT_LABEL = "default";
  public static final TreeSet<String> DEFAULT_LABELS =
      new TreeSet<>(Collections.singleton(DEFAULT_LABEL));

  private static final TreeSet<String> NO_LABELS = new TreeSet<>();

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

  public boolean isStarred(Account.Id accountId, Change.Id changeId,
      String label) {
    return starredChangesCache.get().isStarred(accountId, changeId, label);
  }

  public ImmutableSortedSet<String> getLabels(Account.Id accountId,
      Change.Id changeId) {
    return starredChangesCache.get().getLabels(accountId, changeId);
  }

  public Iterable<Change.Id> byAccount(Account.Id accountId, String label) {
    return starredChangesCache.get().byAccount(accountId, label);
  }

  public Iterable<Account.Id> byChange(Change.Id changeId, String label) {
    return starredChangesCache.get().byChange(changeId, label);
  }

  public void star(Account.Id accountId, Change.Id changeId,
      Set<String> newLabels) throws OrmException {
    if (newLabels.contains(DEFAULT_LABEL)) {
      dbProvider.get().starredChanges()
          .insert(Collections.singleton(new StarredChange(
              new StarredChange.Key(accountId, changeId))));
      starredChangesCache.get().star(accountId, changeId, newLabels);
    }
    if (!migration.writeChanges()) {
      if (!Collections.singleton(DEFAULT_LABEL).containsAll(newLabels)) {
        throw new OrmException("labeled stars not supported");
      }
      return;
    }
    try (Repository repo = repoManager.openMetadataRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      String refName = RefNames.refsStarredChanges(accountId, changeId);
      ObjectId oldObjectId = getObjectId(repo, refName);

      SortedSet<String> labels = readLabels(repo, oldObjectId);
      if (!labels.addAll(newLabels)) {
        return;
      }

      updateLabels(repo, refName, oldObjectId, labels);
      starredChangesCache.get().star(accountId, changeId, newLabels);
    } catch (IOException e) {
      throw new OrmException(
          String.format("Star change %d for account %d failed",
              changeId.get(), accountId.get()), e);
    }
  }

  public void unstar(Account.Id accountId, Change.Id changeId,
      Set<String> labelsToRemove) throws OrmException {
    if (labelsToRemove.contains(DEFAULT_LABEL)) {
      dbProvider.get().starredChanges()
          .delete(Collections.singleton(new StarredChange(
              new StarredChange.Key(accountId, changeId))));
      starredChangesCache.get().unstar(accountId, changeId, labelsToRemove);
    }
    if (!migration.writeChanges()) {
      if (!Collections.singleton(DEFAULT_LABEL).containsAll(labelsToRemove)) {
        throw new OrmException("labeled stars not supported");
      }
      return;
    }
    try (Repository repo = repoManager.openMetadataRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      String refName = RefNames.refsStarredChanges(accountId, changeId);
      ObjectId oldObjectId = getObjectId(repo, refName);

      SortedSet<String> labels = readLabels(repo, oldObjectId);
      if (!labels.removeAll(labelsToRemove)) {
        return;
      }

      if (labels.isEmpty()) {
        deleteRef(repo, refName, oldObjectId);
      } else {
        updateLabels(repo, refName, oldObjectId, labels);
      }
      starredChangesCache.get().unstar(accountId, changeId, labelsToRemove);
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

  public static TreeSet<String> readLabels(Repository repo, String refName)
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
      return NO_LABELS;
    }

    try (ObjectReader reader = repo.newObjectReader()) {
      ObjectLoader obj = reader.open(id, Constants.OBJ_BLOB);
      TreeSet<String> labels = new TreeSet<>();
      Collections.addAll(labels,
          UTF_8.newDecoder().decode(
              ByteBuffer.wrap(obj.getCachedBytes(Integer.MAX_VALUE)))
              .toString().split("\\n"));
      return labels;
    }
  }

  private static ObjectId writeLabels(Repository repo, SortedSet<String> labels)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId id = oi.insert(Constants.OBJ_TREE, UTF_8.newEncoder()
          .encode(CharBuffer.wrap(Joiner.on("\n").join(labels))).array());
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
