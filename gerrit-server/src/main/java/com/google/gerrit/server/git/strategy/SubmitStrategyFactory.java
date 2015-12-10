// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.git.strategy;

import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** Factory to create a {@link SubmitStrategy} for a {@link SubmitType}. */
@Singleton
public class SubmitStrategyFactory {
  private static final Logger log = LoggerFactory
      .getLogger(SubmitStrategyFactory.class);

  private final Provider<PersonIdent> myIdent;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final RebaseChangeOp.Factory rebaseFactory;
  private final ProjectCache projectCache;
  private final ApprovalsUtil approvalsUtil;
  private final MergeUtil.Factory mergeUtilFactory;

  @Inject
  SubmitStrategyFactory(
      @GerritPersonIdent Provider<PersonIdent> myIdent,
      final BatchUpdate.Factory batchUpdateFactory,
      final ChangeControl.GenericFactory changeControlFactory,
      final PatchSetInfoFactory patchSetInfoFactory,
      final RebaseChangeOp.Factory rebaseFactory,
      final ProjectCache projectCache,
      final ApprovalsUtil approvalsUtil,
      final MergeUtil.Factory mergeUtilFactory) {
    this.myIdent = myIdent;
    this.batchUpdateFactory = batchUpdateFactory;
    this.changeControlFactory = changeControlFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.rebaseFactory = rebaseFactory;
    this.projectCache = projectCache;
    this.approvalsUtil = approvalsUtil;
    this.mergeUtilFactory = mergeUtilFactory;
  }

  public SubmitStrategy create(SubmitType submitType, ReviewDb db,
      Repository repo, CodeReviewRevWalk rw, ObjectInserter inserter,
      RevFlag canMergeFlag, Set<RevCommit> alreadyAccepted,
      Branch.NameKey destBranch, IdentifiedUser caller)
      throws IntegrationException, NoSuchProjectException {
    ProjectState project = getProject(destBranch);
    SubmitStrategy.Arguments args = new SubmitStrategy.Arguments(
        myIdent, db, batchUpdateFactory, changeControlFactory,
        repo, rw, inserter, canMergeFlag, alreadyAccepted,
        destBranch,approvalsUtil, mergeUtilFactory.create(project), caller);
    switch (submitType) {
      case CHERRY_PICK:
        return new CherryPick(args, patchSetInfoFactory);
      case FAST_FORWARD_ONLY:
        return new FastForwardOnly(args);
      case MERGE_ALWAYS:
        return new MergeAlways(args);
      case MERGE_IF_NECESSARY:
        return new MergeIfNecessary(args);
      case REBASE_IF_NECESSARY:
        return new RebaseIfNecessary(args, patchSetInfoFactory, rebaseFactory);
      default:
        final String errorMsg = "No submit strategy for: " + submitType;
        log.error(errorMsg);
        throw new IntegrationException(errorMsg);
    }
  }

  private ProjectState getProject(Branch.NameKey branch)
      throws NoSuchProjectException {
    final ProjectState p = projectCache.get(branch.getParentKey());
    if (p == null) {
      throw new NoSuchProjectException(branch.getParentKey());
    }
    return p;
  }
}
