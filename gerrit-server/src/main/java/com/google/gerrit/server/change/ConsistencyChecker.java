// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Checks changes for various kinds of inconsistency and corruption.
 * <p>
 * A single instance may be reused for checking multiple changes, but not
 * concurrently.
 */
public class ConsistencyChecker {
  private static final Logger log =
      LoggerFactory.getLogger(ConsistencyChecker.class);

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;

  private Change change;
  private Repository repo;
  private RevWalk rw;

  private PatchSet currPs;
  private RevCommit currPsCommit;

  private List<String> messages;

  @Inject
  ConsistencyChecker(Provider<ReviewDb> db,
      GitRepositoryManager repoManager) {
    this.db = db;
    this.repoManager = repoManager;
    reset();
  }

  private void reset() {
    change = null;
    repo = null;
    rw = null;
    messages = new ArrayList<>();
  }

  public CheckResult check(Change c) {
    reset();
    change = c;
    try {
      checkImpl();
      CheckResult result = new CheckResult();
      result.messages = messages;
      return result;
    } finally {
      if (rw != null) {
        rw.release();
      }
      if (repo != null) {
        repo.close();
      }
    }
  }

  private void checkImpl() {
    // TODO(dborowitz):
    //  - check current patch set exists (w/o repo)
    //  - check owner exists

    // All checks that require the repo.
    if (!openRepo()) {
      return;
    }
    if (!checkPatchSets()) {
      return;
    }
    checkMerged();
  }

  private boolean openRepo() {
    Project.NameKey project = change.getDest().getParentKey();
    try {
      repo = repoManager.openRepository(project);
      rw = new RevWalk(repo);
      return true;
    } catch (RepositoryNotFoundException e) {
      return error("Destination repository not found: " + project, e);
    } catch (IOException e) {
      return error("Failed to open repository: " + project, e);
    }
  }

  private boolean checkPatchSets() {
    List<PatchSet> all;
    try {
      all = db.get().patchSets().byChange(change.getId()).toList();
    } catch (OrmException e) {
      return error("Failed to look up patch sets", e);
    }
    Multimap<ObjectId, PatchSet> bySha = MultimapBuilder.hashKeys(all.size())
        .treeSetValues(Ordering.natural()
        .onResultOf(new Function<PatchSet, Integer>() {
          @Override
          public Integer apply(PatchSet in) {
            return in.getId().get();
          }
        })).build();
    for (PatchSet ps : all) {
      ObjectId objId;
      String rev = ps.getRevision().get();
      int psNum = ps.getId().get();
      try {
        objId = ObjectId.fromString(rev);
      } catch (IllegalArgumentException e) {
        messages.add(String.format("Invalid revision on patch set %d: %s",
            psNum, rev));
        continue;
      }
      bySha.put(objId, ps);

      boolean isCurrent = ps.getId().equals(change.currentPatchSetId());
      if (isCurrent) {
        currPs = ps;
      }
      RevCommit psCommit = parseCommit(
          objId, String.format("patch set %d: %s", psNum, rev));
      if (psCommit == null) {
        continue;
      }
      if (isCurrent) {
        currPsCommit = psCommit;
      }
    }

    for (Map.Entry<ObjectId, Collection<PatchSet>> e
        : bySha.asMap().entrySet()) {
      if (e.getValue().size() > 1) {
        messages.add(String.format("Multiple patch sets pointing to %s: %s",
            e.getKey().name(), Joiner.on(", ").join(e.getValue())));
      }
    }

    if (currPs == null) {
      messages.add(String.format("Current patch set %d not found",
          change.currentPatchSetId().get()));
    }

    return currPs != null && currPsCommit != null;
  }

  private void checkMerged() {
    String refName = change.getDest().get();
    Ref dest;
    try {
      dest = repo.getRef(refName);
    } catch (IOException e) {
      messages.add("Failed to look up destination ref: " + refName);
      return;
    }
    if (dest == null) {
      messages.add("Destination ref not found (may be new branch): "
          + change.getDest());
    }
    RevCommit tip = parseCommit(dest.getObjectId(),
        "destination ref " + refName);
    if (tip == null) {
      return;
    }
    boolean merged;
    try {
      merged = rw.isMergedInto(currPsCommit, tip);
    } catch (IOException e) {
      messages.add("Error checking whether patch set " + currPs.getId().get()
          + " is merged");
      return;
    }
    if (merged && change.getStatus() != Change.Status.MERGED) {
      messages.add(String.format("Patch set %d (%s) is merged into destination"
            + " ref %s (%s), but change status is %s", currPs.getId().get(),
            currPsCommit.name(), refName, tip.name(), change.getStatus()));
      // TODO(dborowitz): Just fix it.
    } else if (!merged && change.getStatus() == Change.Status.MERGED) {
      messages.add(String.format("Patch set %d (%s) is not merged into"
            + " destination ref %s (%s), but change status is %s",
            currPs.getId().get(), currPsCommit.name(), refName, tip.name(),
            change.getStatus()));
    }
  }

  private RevCommit parseCommit(ObjectId objId, String desc) {
    try {
      return rw.parseCommit(objId);
    } catch (MissingObjectException e) {
      messages.add(String.format("Object missing: %s: %s", desc, objId.name()));
    } catch (IncorrectObjectTypeException e) {
      messages.add(String.format("Not a commit: %s: %s", desc, objId.name()));
    } catch (IOException e) {
      messages.add(String.format("Failed to look up: %s: %s", desc, objId.name()));
    }
    return null;
  }

  private boolean error(String msg, Throwable t) {
    messages.add(msg);
    // TODO(dborowitz): Expose stack trace to administrators.
    log.warn("Error in consistency check of change " + change.getId(), t);
    return false;
  }
}
