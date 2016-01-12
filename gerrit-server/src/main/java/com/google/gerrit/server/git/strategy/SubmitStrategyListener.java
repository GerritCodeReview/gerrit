// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.base.CharMatcher;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeOp.CommitStatus;

import java.util.Collection;

public class SubmitStrategyListener extends BatchUpdate.Listener {
  private final Collection<SubmitStrategy> strategies;
  private final CommitStatus commits;

  public SubmitStrategyListener(Collection<SubmitStrategy> strategies,
      CommitStatus commits) {
    this.strategies = strategies;
    this.commits = commits;
  }

  @Override
  public void afterUpdateRepo() throws ResourceConflictException {
    markCleanMerges();
    checkCommitStatus();
  }

  private void markCleanMerges() throws ResourceConflictException {
    try {
      for (SubmitStrategy strategy : strategies) {
        SubmitStrategy.Arguments args = strategy.args;
        args.mergeUtil.markCleanMerges(args.rw, args.canMergeFlag,
            args.mergeTip.getCurrentTip(), args.alreadyAccepted);
      }
    } catch (IntegrationException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    }
  }

  private void checkCommitStatus() throws ResourceConflictException {
    for (Change.Id id : commits.getChanges().ids()) {
      CodeReviewCommit commit = commits.get(id);
      CommitMergeStatus s = commit != null ? commit.getStatusCode() : null;
      if (s == null) {
        commits.problem(id,
            "internal error: change not processed by merge strategy");
        return;
      }
      switch (s) {
        case CLEAN_MERGE:
        case CLEAN_REBASE:
        case CLEAN_PICK:
        case ALREADY_MERGED:
          break; // Merge strategy accepted this change.

        case PATH_CONFLICT:
        case REBASE_MERGE_CONFLICT:
        case MANUAL_RECURSIVE_MERGE:
        case CANNOT_CHERRY_PICK_ROOT:
        case NOT_FAST_FORWARD:
          // TODO(dborowitz): Reformat these messages to be more appropriate for
          // short problem descriptions.
          commits.problem(id,
              CharMatcher.is('\n').collapseFrom(s.getMessage(), ' '));
          break;

        case MISSING_DEPENDENCY:
          commits.problem(id, "depends on change that was not submitted");
          break;

        default:
          commits.problem(id, "unspecified merge failure: " + s);
          break;
      }
    }
    commits.maybeFailVerbose();
  }

  @Override
  public void afterUpdateChange() throws ResourceConflictException {
    commits.maybeFail("Error updating status");
  }
}
