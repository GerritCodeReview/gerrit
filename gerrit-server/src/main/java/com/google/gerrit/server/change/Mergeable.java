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

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.BranchOrderSection;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Mergeable implements RestReadView<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(Mergeable.class);

  public static class MergeableInfo {
    public SubmitType submitType;
    public boolean mergeable;
    public List<String> mergeableInto;
  }

  @Option(name = "--other-branches", aliases = {"-o"},
      usage = "test mergeability for other branches too")
  private boolean otherBranches;

  @Option(name = "--force", aliases = {"-f"},
      usage = "force recheck of mergeable field")
  public void setForce(boolean force) {
    this.force = force;
  }

  public void setReindex(boolean reindex) {
    this.reindex = reindex;
  }

  private final TestSubmitType.Get submitType;
  private final GitRepositoryManager gitManager;
  private final ProjectCache projectCache;
  private final SubmitStrategyFactory submitStrategyFactory;
  private final Provider<ReviewDb> db;
  private final ChangeIndexer indexer;
  private final MergeabilityCache cache;

  private boolean force;
  private boolean reindex;

  @Inject
  Mergeable(TestSubmitType.Get submitType,
      GitRepositoryManager gitManager,
      ProjectCache projectCache,
      SubmitStrategyFactory submitStrategyFactory,
      Provider<ReviewDb> db,
      ChangeIndexer indexer,
      MergeabilityCache cache) {
    this.submitType = submitType;
    this.gitManager = gitManager;
    this.projectCache = projectCache;
    this.submitStrategyFactory = submitStrategyFactory;
    this.db = db;
    this.indexer = indexer;
    this.cache = cache;
    reindex = true;
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

    Repository git = gitManager.openRepository(change.getProject());
    try {
      RevWalk rw = CodeReviewCommit.newRevWalk(git);
      try {
        CodeReviewCommit rev = parse(rw, ps);
        Map<String, Ref> refs = git.getRefDatabase().getRefs(RefDatabase.ALL);
        Ref ref = refs.get(change.getDest().get());
        ObjectId into = ref != null ? ref.getObjectId() : ObjectId.zeroId();

        Optional<Boolean> m = cache.get(rev, into, result.submitType);
        if (!m.isPresent() || force || isStale(rev, into, result.submitType)) {
          result.mergeable =
              refresh(change, rev, result.submitType, git, rw, refs, into, m);
        } else {
          result.mergeable = m.get();
        }

        if (otherBranches) {
          result.mergeableInto = new ArrayList<>();
          BranchOrderSection branchOrder =
              projectCache.get(change.getProject()).getBranchOrderSection();
          if (branchOrder != null) {
            int prefixLen = Constants.R_HEADS.length();
            for (String n : branchOrder.getMoreStable(ref.getName())) {
              Ref other = refs.get(n);
              if (other == null) {
                continue;
              }
              if (isMergeable(change, rev, SubmitType.CHERRY_PICK, git, rw,
                  refs, other.getObjectId())) {
                result.mergeableInto.add(other.getName().substring(prefixLen));
              }
            }
          }
        }
      } finally {
        rw.release();
      }
    } finally {
      git.close();
    }
    return result;
  }

  private boolean isStale(ObjectId id, ObjectId into, SubmitType submitType) {
    return !cache.get(id, into, submitType).isPresent();
  }

  private boolean refresh(Change change,
      CodeReviewCommit rev,
      SubmitType type,
      Repository git,
      RevWalk rw,
      Map<String, Ref> refs,
      ObjectId into,
      Optional<Boolean> old) throws IOException, OrmException {
    boolean mergeable = isMergeable(change, rev, type, git, rw, refs, into);
    if (reindex && !old.equals(Optional.of(mergeable))) {
      indexer.index(db.get(), change);
    }
    cache.save(rev, into, type, mergeable);
    return mergeable;
  }

  private boolean isMergeable(Change change,
      CodeReviewCommit rev,
      SubmitType type,
      Repository git,
      RevWalk rw,
      Map<String, Ref> refs,
      ObjectId into) throws IOException, OrmException {
    RevFlag canMerge = rw.newFlag("CAN_MERGE");
    try {
      rev.add(canMerge);

      final boolean mergeable;
      if (into.equals(ObjectId.zeroId())) {
        mergeable = true; // Assume yes on new branch.
      } else {
        CodeReviewCommit tip = parse(rw, into);
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
      return mergeable;
    } catch (MergeException | IOException | NoSuchProjectException e) {
      log.error(String.format(
          "Cannot merge test change %d", change.getId().get()), e);
      return false;
    } finally {
      rw.disposeFlag(canMerge);
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

  private static CodeReviewCommit parse(RevWalk rw, PatchSet ps)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    ObjectId id;
    try {
      id = ObjectId.fromString(ps.getRevision().get());
    } catch (IllegalArgumentException e) {
      log.error(String.format(
          "Invalid revision on patch set %d of %d",
          ps.getId().get(),
          ps.getId().getParentKey().get()));
      return null;
    }
    return parse(rw, id);
  }

  private static CodeReviewCommit parse(RevWalk rw, ObjectId id)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    return (CodeReviewCommit) rw.parseCommit(id);
  }
}
