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

package com.google.gerrit.server.submit;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.submit.MergeOp.CommitStatus;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;

/** Factory to create a {@link SubmitStrategy} for a {@link SubmitType}. */
@Singleton
public class SubmitStrategyFactory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SubmitStrategy.Arguments.Factory argsFactory;

  @Inject
  SubmitStrategyFactory(SubmitStrategy.Arguments.Factory argsFactory) {
    this.argsFactory = argsFactory;
  }

  public SubmitStrategy create(
      SubmitType submitType,
      CodeReviewRevWalk rw,
      RevFlag canMergeFlag,
      Set<RevCommit> alreadyAccepted,
      Set<CodeReviewCommit> incoming,
      BranchNameKey destBranch,
      IdentifiedUser caller,
      MergeTip mergeTip,
      CommitStatus commitStatus,
      RequestId submissionId,
      SubmitInput submitInput,
      SubmoduleOp submoduleOp,
      boolean dryrun)
      throws IntegrationException {
    SubmitStrategy.Arguments args =
        argsFactory.create(
            submitType,
            destBranch,
            commitStatus,
            rw,
            caller,
            mergeTip,
            canMergeFlag,
            alreadyAccepted,
            incoming,
            submissionId,
            submitInput,
            submoduleOp,
            dryrun);
    switch (submitType) {
      case CHERRY_PICK:
        return new CherryPick(args);
      case FAST_FORWARD_ONLY:
        return new FastForwardOnly(args);
      case MERGE_ALWAYS:
        return new MergeAlways(args);
      case MERGE_IF_NECESSARY:
        return new MergeIfNecessary(args);
      case REBASE_IF_NECESSARY:
        return new RebaseIfNecessary(args);
      case REBASE_ALWAYS:
        return new RebaseAlways(args);
      case INHERIT:
      default:
        String errorMsg = "No submit strategy for: " + submitType;
        logger.atSevere().log(errorMsg);
        throw new IntegrationException(errorMsg);
    }
  }
}
