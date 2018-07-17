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

package com.google.gerrit.server.restapi.change;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.MergeabilityCache;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.BranchOrderSection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

public class Mergeable implements RestReadView<RevisionResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Option(
      name = "--other-branches",
      aliases = {"-o"},
      usage = "test mergeability for other branches too")
  private boolean otherBranches;

  private final GitRepositoryManager gitManager;
  private final ProjectCache projectCache;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeData.Factory changeDataFactory;
  private final Provider<ReviewDb> db;
  private final ChangeIndexer indexer;
  private final MergeabilityCache cache;
  private final SubmitRuleEvaluator submitRuleEvaluator;

  @Inject
  Mergeable(
      GitRepositoryManager gitManager,
      ProjectCache projectCache,
      MergeUtil.Factory mergeUtilFactory,
      ChangeData.Factory changeDataFactory,
      Provider<ReviewDb> db,
      ChangeIndexer indexer,
      MergeabilityCache cache,
      SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory) {
    this.gitManager = gitManager;
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
    this.changeDataFactory = changeDataFactory;
    this.db = db;
    this.indexer = indexer;
    this.cache = cache;
    submitRuleEvaluator = submitRuleEvaluatorFactory.create(SubmitRuleOptions.defaults());
  }

  public void setOtherBranches(boolean otherBranches) {
    this.otherBranches = otherBranches;
  }

  @Override
  public MergeableInfo apply(RevisionResource resource)
      throws AuthException, ResourceConflictException, BadRequestException, OrmException,
          IOException {
    Change change = resource.getChange();
    PatchSet ps = resource.getPatchSet();
    MergeableInfo result = new MergeableInfo();

    if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    } else if (!ps.getId().equals(change.currentPatchSetId())) {
      // Only the current revision is mergeable. Others always fail.
      return result;
    }

    ChangeData cd = changeDataFactory.create(db.get(), resource.getNotes());
    result.submitType = getSubmitType(cd);

    try (Repository git = gitManager.openRepository(change.getProject())) {
      ObjectId commit = toId(ps);
      Ref ref = git.getRefDatabase().exactRef(change.getDest().get());
      ProjectState projectState = projectCache.get(change.getProject());
      String strategy = mergeUtilFactory.create(projectState).mergeStrategyName();
      result.strategy = strategy;
      result.mergeable = isMergable(git, change, commit, ref, result.submitType, strategy);

      if (otherBranches) {
        result.mergeableInto = new ArrayList<>();
        BranchOrderSection branchOrder = projectState.getBranchOrderSection();
        if (branchOrder != null) {
          int prefixLen = Constants.R_HEADS.length();
          String[] names = branchOrder.getMoreStable(ref.getName());
          Map<String, Ref> refs = git.getRefDatabase().exactRef(names);
          for (String n : names) {
            Ref other = refs.get(n);
            if (other == null) {
              continue;
            }
            if (cache.get(commit, other, SubmitType.CHERRY_PICK, strategy, change.getDest(), git)) {
              result.mergeableInto.add(other.getName().substring(prefixLen));
            }
          }
        }
      }
    }
    return result;
  }

  private SubmitType getSubmitType(ChangeData cd) throws OrmException {
    SubmitTypeRecord rec = submitRuleEvaluator.getSubmitType(cd);
    if (rec.status != SubmitTypeRecord.Status.OK) {
      throw new OrmException("Submit type rule failed: " + rec);
    }
    return rec.type;
  }

  private boolean isMergable(
      Repository git,
      Change change,
      ObjectId commit,
      Ref ref,
      SubmitType submitType,
      String strategy)
      throws OrmException {
    if (commit == null) {
      return false;
    }

    Boolean old = cache.getIfPresent(commit, ref, submitType, strategy);
    if (old != null) {
      return old;
    }
    return refresh(change, commit, ref, submitType, strategy, git, old);
  }

  private static ObjectId toId(PatchSet ps) {
    try {
      return ObjectId.fromString(ps.getRevision().get());
    } catch (IllegalArgumentException e) {
      logger.atSevere().log("Invalid revision on patch set %s", ps);
      return null;
    }
  }

  private boolean refresh(
      final Change change,
      ObjectId commit,
      final Ref ref,
      SubmitType type,
      String strategy,
      Repository git,
      Boolean old)
      throws OrmException {
    final boolean mergeable = cache.get(commit, ref, type, strategy, change.getDest(), git);
    if (!Objects.equals(mergeable, old)) {
      invalidateETag(change.getId(), db.get());

      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError = indexer.indexAsync(change.getProject(), change.getId());
    }
    return mergeable;
  }

  private static void invalidateETag(Change.Id id, ReviewDb db) throws OrmException {
    // Empty update of Change to bump rowVersion, changing its ETag.
    // TODO(dborowitz): Include cache info in ETag somehow instead.
    db = ReviewDbUtil.unwrapDb(db);
    Change c = db.changes().get(id);
    if (c != null) {
      db.changes().update(Collections.singleton(c));
    }
  }
}
