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
import com.google.gerrit.server.changedetail.RebaseChange;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.index.ChangeIndexer;
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
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** Factory to create a {@link SubmitStrategy} for a {@link SubmitType}. */
@Singleton
public class SubmitStrategyFactory {
  private static final Logger log = LoggerFactory
      .getLogger(SubmitStrategyFactory.class);

  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final Provider<PersonIdent> myIdent;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final RebaseChange rebaseChange;
  private final ProjectCache projectCache;
  private final ApprovalsUtil approvalsUtil;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeIndexer indexer;

  @Inject
  SubmitStrategyFactory(
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritPersonIdent Provider<PersonIdent> myIdent,
      final ChangeControl.GenericFactory changeControlFactory,
      final PatchSetInfoFactory patchSetInfoFactory,
      final GitReferenceUpdated gitRefUpdated, final RebaseChange rebaseChange,
      final ProjectCache projectCache,
      final ApprovalsUtil approvalsUtil,
      final MergeUtil.Factory mergeUtilFactory,
      final ChangeIndexer indexer) {
    this.identifiedUserFactory = identifiedUserFactory;
    this.myIdent = myIdent;
    this.changeControlFactory = changeControlFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.rebaseChange = rebaseChange;
    this.projectCache = projectCache;
    this.approvalsUtil = approvalsUtil;
    this.mergeUtilFactory = mergeUtilFactory;
    this.indexer = indexer;
  }

  public SubmitStrategy create(final SubmitType submitType, final ReviewDb db,
      final Repository repo, final RevWalk rw, final ObjectInserter inserter,
      final RevFlag canMergeFlag, final Set<RevCommit> alreadyAccepted,
      final Branch.NameKey destBranch)
      throws MergeException, NoSuchProjectException {
    ProjectState project = getProject(destBranch);
    final SubmitStrategy.Arguments args =
        new SubmitStrategy.Arguments(identifiedUserFactory, myIdent, db,
            changeControlFactory, repo, rw, inserter, canMergeFlag,
            alreadyAccepted, destBranch,approvalsUtil,
            mergeUtilFactory.create(project), indexer);
    switch (submitType) {
      case CHERRY_PICK:
        return new CherryPick(args, patchSetInfoFactory, gitRefUpdated);
      case FAST_FORWARD_ONLY:
        return new FastForwardOnly(args);
      case MERGE_ALWAYS:
        return new MergeAlways(args);
      case MERGE_IF_NECESSARY:
        return new MergeIfNecessary(args);
      case REBASE_IF_NECESSARY:
        return new RebaseIfNecessary(args, patchSetInfoFactory, rebaseChange);
      default:
        final String errorMsg = "No submit strategy for: " + submitType;
        log.error(errorMsg);
        throw new MergeException(errorMsg);
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
