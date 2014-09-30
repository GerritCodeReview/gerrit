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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.git.BranchOrderSection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
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
import java.util.Objects;

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
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeData.Factory changeDataFactory;
  private final Provider<ReviewDb> db;
  private final ChangeIndexer indexer;
  private final MergeabilityCache cache;

  private boolean force;

  @Inject
  Mergeable(GitRepositoryManager gitManager,
      ProjectCache projectCache,
      MergeUtil.Factory mergeUtilFactory,
      ChangeData.Factory changeDataFactory,
      Provider<ReviewDb> db,
      ChangeIndexer indexer,
      MergeabilityCache cache) {
    this.gitManager = gitManager;
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
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

    Repository git = gitManager.openRepository(change.getProject());
    try {
      ObjectId commit = toId(ps);
      if (commit == null) {
        result.mergeable = false;
        return result;
      }

      Ref ref = git.getRef(change.getDest().get());
      ObjectId into = toId(ref);
      ProjectState projectState = projectCache.get(change.getProject());
      String strategy = mergeUtilFactory.create(projectState)
          .mergeStrategyName();
      Boolean old =
          cache.getIfPresent(commit, into, result.submitType, strategy);

      if (force || old == null) {
        result.mergeable = refresh(change, commit, into, result.submitType,
            strategy, git, old);
      }

      if (otherBranches) {
        result.mergeableInto = new ArrayList<>();
        BranchOrderSection branchOrder = projectState.getBranchOrderSection();
        if (branchOrder != null) {
          int prefixLen = Constants.R_HEADS.length();
          for (String n : branchOrder.getMoreStable(ref.getName())) {
            Ref other = git.getRef(n);
            if (other == null) {
              continue;
            }
            if (cache.get(commit, toId(other), SubmitType.CHERRY_PICK, strategy,
                change.getDest(), git)) {
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

  private static ObjectId toId(Ref ref) {
    return ref != null && ref.getObjectId() != null
        ? ref.getObjectId()
        : ObjectId.zeroId();
  }

  private static ObjectId toId(PatchSet ps) {
    try {
      return ObjectId.fromString(ps.getRevision().get());
    } catch (IllegalArgumentException e) {
      log.error("Invalid revision on patch set " + ps);
      return null;
    }
  }

  private boolean refresh(final Change change, ObjectId commit,
      final ObjectId into, SubmitType type, String strategy, Repository git,
      Boolean old) throws OrmException, IOException {
    final boolean mergeable =
        cache.get(commit, into, type, strategy, change.getDest(), git);
    if (!Objects.equals(mergeable, old)) {
      // TODO(dborowitz): Include cache info in ETag somehow instead.
      ChangeUtil.bumpRowVersionNotLastUpdatedOn(change.getId(), db.get());
      indexer.index(db.get(), change);
    }
    return mergeable;
  }
}
