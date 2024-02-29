// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.InsertedObject;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Wrapper around {@link Repository} that keeps track of related {@link ObjectInserter}s and other
 * objects that are jointly closed when invoking {@link #close}.
 */
class OpenRepo implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final Repository repo;
  final RevWalk rw;
  final ChainedReceiveCommands cmds;
  final ObjectInserter tempIns;

  private final InMemoryInserter inMemIns;
  @Nullable private final ObjectInserter finalIns;
  private final boolean close;

  /** Returns a {@link OpenRepo} wrapping around an open {@link Repository}. */
  static OpenRepo open(GitRepositoryManager repoManager, Project.NameKey project)
      throws IOException {
    Repository repo = repoManager.openRepository(project); // Closed by OpenRepo#close.
    ObjectInserter ins = repo.newObjectInserter(); // Closed by OpenRepo#close.
    ObjectReader reader = ins.newReader(); // Not closed by OpenRepo#close.
    try (RevWalk rw = new RevWalk(reader)) { // Doesn't escape OpenRepo constructor.
      return new OpenRepo(repo, rw, ins, new ChainedReceiveCommands(repo), true) {
        @Override
        public void close() {
          reader.close();
          super.close();
        }
      };
    }
  }

  OpenRepo(
      Repository repo,
      RevWalk rw,
      @Nullable ObjectInserter ins,
      ChainedReceiveCommands cmds,
      boolean close) {
    ObjectReader reader = rw.getObjectReader();
    checkArgument(
        ins == null || reader.getCreatedFromInserter() == ins,
        "expected reader to be created from %s, but was %s",
        ins,
        reader.getCreatedFromInserter());
    this.repo = requireNonNull(repo);

    this.inMemIns = new InMemoryInserter(rw.getObjectReader());
    this.tempIns = inMemIns;

    this.rw = new RevWalk(tempIns.newReader());
    this.finalIns = ins;
    this.cmds = requireNonNull(cmds);
    this.close = close;
  }

  @Override
  public void close() {
    rw.getObjectReader().close();
    rw.close();
    if (close) {
      if (finalIns != null) {
        finalIns.close();
      }
      repo.close();
    }
  }

  void flush() throws IOException {
    flushToFinalInserter();
    logger.atFine().log("flushing inserter %s", finalIns);
    finalIns.flush();
  }

  void flushToFinalInserter() throws IOException {
    checkState(finalIns != null);
    for (InsertedObject obj : inMemIns.getInsertedObjects()) {
      logger.atFine().log(
          "copying %s object %s to final inserter %s",
          Constants.typeString(obj.type()), obj.id().name(), finalIns);
      finalIns.insert(obj.type(), obj.data().toByteArray());
    }
    inMemIns.clear();
  }

  private static <U extends AbstractChangeUpdate> boolean allowWrite(
      Collection<U> updates, ObjectId old) {
    if (!old.equals(ObjectId.zeroId())) {
      return true;
    }
    return updates.iterator().next().allowWriteToNewRef();
  }

  <U extends AbstractChangeUpdate> void addUpdatesNoLimits(ListMultimap<String, U> all)
      throws IOException {
    addUpdates(
        all, Optional.empty() /* unlimited updates */, Optional.empty() /* unlimited patch sets */);
  }

  <U extends AbstractChangeUpdate> void addUpdates(
      ListMultimap<String, U> all, Optional<Integer> maxUpdates, Optional<Integer> maxPatchSets)
      throws IOException {
    for (Map.Entry<String, Collection<U>> e : all.asMap().entrySet()) {
      String refName = e.getKey();
      Collection<U> updates = e.getValue();
      ObjectId old = cmds.get(refName).orElse(ObjectId.zeroId());
      // Only actually write to the ref if one of the updates explicitly allows
      // us to do so, i.e. it is known to represent a new change. This avoids
      // writing partial change meta if the change hasn't been backfilled yet.
      if (!allowWrite(updates, old)) {
        continue;
      }

      int updateCount = 0;
      U first = updates.iterator().next();
      if (maxUpdates.isPresent()) {
        checkState(first.getNotes() != null, "expected ChangeNotes on %s", first);
        updateCount = first.getNotes().getUpdateCount();
      }

      ObjectId curr = old;
      for (U update : updates) {
        if (maxPatchSets.isPresent() && update.psId != null) {
          // Patch set IDs are assigned consecutively. Patch sets may have been deleted, but the ID
          // is still a good estimate and an upper bound.
          if (update.psId.get() > maxPatchSets.get()) {
            throw new LimitExceededException(
                String.format(
                    "Change %d may not exceed %d patch sets. To continue working on this change, "
                        + "recreate it with a new Change-Id, then abandon this one.",
                    update.getId().get(), maxPatchSets.get()));
          }
        }
        if (update.isRootOnly() && !old.equals(ObjectId.zeroId())) {
          throw new StorageException("Given ChangeUpdate is only allowed on initial commit");
        }
        ObjectId next = update.apply(rw, tempIns, curr);
        if (next == null) {
          continue;
        }
        if (maxUpdates.isPresent()
            && !Objects.equals(next, curr)
            && ++updateCount > maxUpdates.get()
            && !update.bypassMaxUpdates()) {
          throw new LimitExceededException(
              String.format(
                  "Change %s may not exceed %d updates. It may still be abandoned, submitted and you can add/remove"
                      + " reviewers to/from the attention-set. To continue working on this change, recreate it with a new"
                      + " Change-Id, then abandon this one.",
                  update.getId(), maxUpdates.get()));
        }
        curr = next;
      }
      if (!old.equals(curr)) {
        cmds.add(new ReceiveCommand(old, curr, refName));
      }
    }
  }
}
