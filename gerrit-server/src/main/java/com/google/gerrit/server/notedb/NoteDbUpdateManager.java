// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.ChainedReceiveCommands;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;

public class NoteDbUpdateManager {
  public interface Factory {
    NoteDbUpdateManager create(Project.NameKey projectName);
  }

  private static class OpenRepo implements AutoCloseable {
    final Repository repo;
    final RevWalk rw;
    final ObjectInserter ins;
    final ChainedReceiveCommands cmds;
    final boolean close;

    OpenRepo(Repository repo, RevWalk rw, ObjectInserter ins,
        ChainedReceiveCommands cmds, boolean close) {
      this.repo = checkNotNull(repo);
      this.rw = checkNotNull(rw);
      this.ins = checkNotNull(ins);
      this.cmds = checkNotNull(cmds);
      this.close = close;
    }

    @Override
    public void close() {
      if (close) {
        ins.close();
        rw.close();
        repo.close();
      }
    }
  }

  private final GitRepositoryManager repoManager;
  private final NotesMigration migration;
  private final AllUsersName allUsersName;
  private final Project.NameKey projectName;
  private final ListMultimap<String, ChangeUpdate> changeUpdates;
  private final ListMultimap<String, ChangeDraftUpdate> draftUpdates;

  private OpenRepo changeRepo;
  private OpenRepo allUsersRepo;

  @AssistedInject
  NoteDbUpdateManager(GitRepositoryManager repoManager,
      NotesMigration migration,
      AllUsersName allUsersName,
      @Assisted Project.NameKey projectName) {
    this.repoManager = repoManager;
    this.migration = migration;
    this.allUsersName = allUsersName;
    this.projectName = projectName;
    changeUpdates = ArrayListMultimap.create();
    draftUpdates = ArrayListMultimap.create();
  }

  public NoteDbUpdateManager setChangeRepo(Repository repo, RevWalk rw,
      ObjectInserter ins, ChainedReceiveCommands cmds) {
    checkState(changeRepo == null, "change repo already initialized");
    changeRepo = new OpenRepo(repo, rw, ins, cmds, false);
    return this;
  }

  public NoteDbUpdateManager setAllUsersRepo(Repository repo, RevWalk rw,
      ObjectInserter ins, ChainedReceiveCommands cmds) {
    checkState(allUsersRepo == null, "allUsers repo already initialized");
    allUsersRepo = new OpenRepo(repo, rw, ins, cmds, false);
    return this;
  }

  Repository getAllUsersRepo() throws IOException {
    initAllUsersRepo();
    return allUsersRepo.repo;
  }

  ChainedReceiveCommands getAllUsersCommands() throws IOException {
    initAllUsersRepo();
    return allUsersRepo.cmds;
  }

  private void initChangeRepo() throws IOException {
    if (changeRepo == null) {
      changeRepo = openRepo(projectName);
    }
  }

  private void initAllUsersRepo() throws IOException {
    if (allUsersRepo == null) {
      allUsersRepo = openRepo(allUsersName);
    }
  }

  private OpenRepo openRepo(Project.NameKey p) throws IOException {
    Repository repo = repoManager.openMetadataRepository(p);
    ObjectInserter ins = repo.newObjectInserter();
    return new OpenRepo(repo, new RevWalk(ins.newReader()), ins,
        new ChainedReceiveCommands(), true);
  }

  private boolean isEmpty() {
    return !migration.writeChanges() || changeUpdates.isEmpty();
  }

  /**
   * Add an update to the list of updates to execute.
   * <p>
   * Updates should only be added to the manager after all mutations have been
   * made, as this method may eagerly access the update.
   *
   * @param update the update to add.
   */
  public void add(ChangeUpdate update) {
    checkArgument(update.getProjectName().equals(projectName),
      "update for project %s cannot be added to manager for project %s",
      update.getProjectName(), projectName);
    changeUpdates.put(update.getRefName(), update);
    ChangeDraftUpdate du = update.getDraftUpdate();
    if (du != null) {
      draftUpdates.put(du.getRefName(), du);
    }
  }

  public void add(ChangeDraftUpdate draftUpdate) {
    draftUpdates.put(draftUpdate.getRefName(), draftUpdate);
  }

  public void execute() throws OrmException, IOException {
    if (isEmpty()) {
      return;
    }
    try {
      initChangeRepo();
      if (!draftUpdates.isEmpty()) {
        initAllUsersRepo();
      }
      addCommands();

      execute(allUsersRepo);
      execute(changeRepo);
    } finally {
      if (allUsersRepo != null) {
        allUsersRepo.close();
      }
      if (changeRepo != null) {
        changeRepo.close();
      }
    }
  }

  private static void execute(OpenRepo or) throws IOException {
    if (or == null || or.cmds.isEmpty()) {
      return;
    }
    or.ins.flush();
    BatchRefUpdate bru = or.repo.getRefDatabase().newBatchUpdate();
    or.cmds.addTo(bru);
    bru.execute(or.rw, NullProgressMonitor.INSTANCE);
    for (ReceiveCommand cmd : bru.getCommands()) {
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        throw new IOException("Update failed: " + bru);
      }
    }
  }

  private void addCommands() throws OrmException, IOException {
    if (isEmpty()) {
      return;
    }
    checkState(changeRepo != null, "must set change repo");
    if (!draftUpdates.isEmpty()) {
      checkState(allUsersRepo != null, "must set all users repo");
    }
    addUpdates(changeUpdates, changeRepo);
    if (!draftUpdates.isEmpty()) {
      addUpdates(draftUpdates, allUsersRepo);
    }
  }

  private static void addUpdates(
      ListMultimap<String, ? extends AbstractChangeUpdate> updates, OpenRepo or)
      throws OrmException, IOException {
    for (String refName : updates.keySet()) {
      ObjectId old = firstNonNull(
          or.cmds.getObjectId(or.repo, refName), ObjectId.zeroId());
      ObjectId curr = old;

      for (AbstractChangeUpdate u : updates.get(refName)) {
        ObjectId next = u.apply(or.rw, or.ins, curr);
        if (next == null) {
          continue;
        }
        curr = next;
      }
      if (!old.equals(curr)) {
        or.cmds.add(new ReceiveCommand(old, curr, refName));
      }
    }
  }
}
