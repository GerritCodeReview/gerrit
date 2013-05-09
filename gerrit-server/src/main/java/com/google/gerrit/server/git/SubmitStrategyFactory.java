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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.changedetail.RebaseChange;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import javax.annotation.Nullable;

/** Factory to create a {@link SubmitStrategy} for a {@link SubmitType}. */
public class SubmitStrategyFactory {
  private static final Logger log = LoggerFactory
      .getLogger(SubmitStrategyFactory.class);

  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final PersonIdent myIdent;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final RebaseChange rebaseChange;
  private final ProjectCache projectCache;
  private final MergeUtil.Factory mergeUtilFactory;

  @Inject
  SubmitStrategyFactory(
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritPersonIdent final PersonIdent myIdent,
      final PatchSetInfoFactory patchSetInfoFactory,
      @CanonicalWebUrl @Nullable final Provider<String> urlProvider,
      final GitReferenceUpdated gitRefUpdated, final RebaseChange rebaseChange,
      final ProjectCache projectCache,
      final MergeUtil.Factory mergeUtilFactory) {
    this.identifiedUserFactory = identifiedUserFactory;
    this.myIdent = myIdent;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.rebaseChange = rebaseChange;
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
  }

  public SubmitStrategy create(final SubmitType submitType, final ReviewDb db,
      final Repository repo, final RevWalk rw, final ObjectInserter inserter,
      final RevFlag canMergeFlag, final Set<RevCommit> alreadyAccepted,
      final Branch.NameKey destBranch)
      throws MergeException, NoSuchProjectException {
    ProjectState project = getProject(destBranch);
    final SubmitStrategy.Arguments args =
        new SubmitStrategy.Arguments(identifiedUserFactory, myIdent, db, repo,
            rw, inserter, canMergeFlag, alreadyAccepted, destBranch,
            mergeUtilFactory.create(project));
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
        return new RebaseIfNecessary(args, rebaseChange, myIdent);
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
