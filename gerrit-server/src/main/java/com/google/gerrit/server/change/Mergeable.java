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

import com.google.gerrit.common.data.SubmitTypeRecord;
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
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

  private final GitRepositoryManager gitManager;
  private final ProjectCache projectCache;
  private final ChangeData.Factory changeDataFactory;
  private final Provider<ReviewDb> db;
  private final ChangeIndexer indexer;
  private final MergeabilityCache cache;

  private boolean force;

  @Inject
  Mergeable(GitRepositoryManager gitManager,
      ProjectCache projectCache,
      ChangeData.Factory changeDataFactory,
      Provider<ReviewDb> db,
      ChangeIndexer indexer,
      MergeabilityCache cache) {
    this.gitManager = gitManager;
    this.projectCache = projectCache;
    this.changeDataFactory = changeDataFactory;
    this.db = db;
    this.indexer = indexer;
    this.cache = cache;
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

    ChangeData cd = changeDataFactory.create(db.get(), resource.getControl());
    SubmitTypeRecord rec = new SubmitRuleEvaluator(cd)
        .setPatchSet(ps)
        .getSubmitType();
    if (rec.status != SubmitTypeRecord.Status.OK) {
      throw new OrmException("Submit type rule failed: " + rec);
    }
    result.submitType = rec.type;
    result.mergeable = change.isMergeable();

    Repository git = gitManager.openRepository(change.getProject());
    try {
      Ref ref = git.getRef(change.getDest().get());
      if (force || isStale(change, ref)) {
        result.mergeable = refresh(change, ps, ref, result.submitType, git);
      }

      if (otherBranches) {
        result.mergeableInto = new ArrayList<>();
        BranchOrderSection branchOrder =
            projectCache.get(change.getProject()).getBranchOrderSection();
        if (branchOrder != null) {
          int prefixLen = Constants.R_HEADS.length();
          for (String n : branchOrder.getMoreStable(ref.getName())) {
            Ref other = git.getRef(n);
            if (other == null) {
              continue;
            }
            if (isMergeable(change, ps, other, SubmitType.CHERRY_PICK, git)) {
              result.mergeableInto.add(other.getName().substring(prefixLen));
            }
          }
        }
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

  private boolean refresh(final Change change, final PatchSet ps,
      final Ref ref, SubmitType type, Repository git)
      throws OrmException, IOException {
    final boolean mergeable = isMergeable(change, ps, ref, type, git);

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
  }

  private boolean isMergeable(Change change, PatchSet ps, Ref ref,
      SubmitType type, Repository git) {
    ObjectId commit;
    try {
      commit = ObjectId.fromString(ps.getRevision().get());
    } catch (IllegalArgumentException e) {
      log.error("Invalid revision on patch set " + ps);
      return false;
    }
    ObjectId into = ref != null && ref.getObjectId() != null
        ? ref.getObjectId()
        : ObjectId.zeroId();
    return cache.load(commit, into, type, change.getDest(), git);
  }
}
