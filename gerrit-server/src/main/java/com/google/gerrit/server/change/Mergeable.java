// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.Sets;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class Mergeable implements RestReadView<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(Mergeable.class);

  public static class MergeableInfo {
    public Project.SubmitType submitType;
    public boolean mergeable;
  }

  private final TestSubmitType.Get submitType;
  private final GitRepositoryManager gitManager;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final Provider<ReviewDb> db;
  private final ChangeIndexer indexer;

  private boolean force;

  @Inject
  Mergeable(TestSubmitType.Get submitType,
      GitRepositoryManager gitManager,
      SubmitStrategyFactory submitStrategyFactory,
      Provider<ReviewDb> db,
      ChangeIndexer indexer) {
    this.submitType = submitType;
    this.gitManager = gitManager;
    this.submitStrategyFactory = submitStrategyFactory;
    this.db = db;
    this.indexer = indexer;
  }

  public void setForce(boolean force) {
    this.force = force;
  }

  @Override
  public MergeableInfo apply(RevisionResource resource) throws AuthException,
      ResourceConflictException, BadRequestException, OrmException, IOException {
    Change change = resource.getChange();
    PatchSet ps = resource.getPatchSet();
    MergeableInfo result = new MergeableInfo();

    if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + Submit.status(change));
    } else if (!ps.getId().equals(change.currentPatchSetId())) {
      // Only the current revision is mergeable. Others always fail.
      return result;
    }

    result.submitType = submitType.apply(resource);
    result.mergeable = change.isMergeable();

    Repository git = gitManager.openRepository(change.getProject());
    try {
      Map<String, Ref> refs = git.getRefDatabase().getRefs(RefDatabase.ALL);
      Ref ref = refs.get(change.getDest().get());
      if (force || isStale(change, ref)) {
        result.mergeable =
            refresh(change, ps, result.submitType, git, refs, ref);
      }
    } finally {
      git.close();
    }
    return result;
  }

  private static boolean isStale(Change change, Ref ref) {
    return change.getLastSha1MergeTested() == null
        || !toRevId(ref).equals(change.getLastSha1MergeTested());
  }

  private static RevId toRevId(Ref ref) {
    return new RevId(ref != null && ref.getObjectId() != null
        ? ref.getObjectId().name()
        : "");
  }

  private boolean refresh(Change change,
      final PatchSet ps,
      Project.SubmitType type,
      Repository git,
      Map<String, Ref> refs,
      final Ref ref) throws IOException, OrmException {
    RevWalk rw = new RevWalk(git) {
      @Override
      protected CodeReviewCommit createCommit(AnyObjectId id) {
        return new CodeReviewCommit(id);
      }
    };
    try {
      ObjectId id;
      try {
        id = ObjectId.fromString(ps.getRevision().get());
      } catch (IllegalArgumentException e) {
        log.error(String.format(
            "Invalid revision on patch set %d of %d",
            ps.getId().get(),
            change.getId().get()));
        return false;
      }

      RevFlag canMerge = rw.newFlag("CAN_MERGE");
      CodeReviewCommit rev = parse(rw, id);
      rev.add(canMerge);

      final boolean mergeable;
      if (ref == null || ref.getObjectId() == null) {
        mergeable = true; // Assume yes on new branch.
      } else {
        CodeReviewCommit tip = parse(rw, ref.getObjectId());
        Set<RevCommit> accepted = alreadyAccepted(rw, refs.values());
        accepted.add(tip);
        accepted.addAll(Arrays.asList(rev.getParents()));
        mergeable = submitStrategyFactory.create(
            type,
            db.get(),
            git,
            rw,
            null /*inserter*/,
            canMerge,
            accepted,
            change.getDest()).dryRun(tip, rev);
      }

      Change c = db.get().changes().atomicUpdate(
        change.getId(),
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change c) {
            if (c.getStatus().isOpen()
                && ps.getId().equals(c.currentPatchSetId())) {
              c.setMergeable(mergeable);
              c.setLastSha1MergeTested(toRevId(ref));
              return c;
            } else {
              return null;
            }
          }
        });
      if (c != null) {
        indexer.index(db.get(), c);
      }
      return mergeable;
    } catch (MergeException | IOException | NoSuchProjectException e) {
      log.error(String.format(
          "Cannot merge test change %d", change.getId().get()), e);
      return false;
    } finally {
      rw.close();
    }
  }

  private static Set<RevCommit> alreadyAccepted(RevWalk rw, Collection<Ref> refs)
      throws MissingObjectException, IOException {
    Set<RevCommit> accepted = Sets.newHashSet();
    for (Ref r : refs) {
      if (r.getName().startsWith(Constants.R_HEADS)
          || r.getName().startsWith(Constants.R_TAGS)) {
        try {
          accepted.add(rw.parseCommit(r.getObjectId()));
        } catch (IncorrectObjectTypeException nonCommit) {
          // Not a commit? Skip over it.
        }
      }
    }
    return accepted;
  }

  private static CodeReviewCommit parse(RevWalk rw, ObjectId id)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    return (CodeReviewCommit) rw.parseCommit(id);
  }
}
