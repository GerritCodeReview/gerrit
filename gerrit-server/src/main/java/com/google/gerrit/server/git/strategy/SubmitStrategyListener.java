// Copyright (C) 2016 The Android Open Source Project
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
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.Submit.TestSubmitInput;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeOp.CommitStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.revwalk.RevCommit;

public class SubmitStrategyListener extends BatchUpdate.Listener {
  private final Collection<SubmitStrategy> strategies;
  private final CommitStatus commits;
  private final boolean failAfterRefUpdates;

  public SubmitStrategyListener(
      SubmitInput input, Collection<SubmitStrategy> strategies, CommitStatus commits) {
    this.strategies = strategies;
    this.commits = commits;
    if (input instanceof TestSubmitInput) {
      failAfterRefUpdates = ((TestSubmitInput) input).failAfterRefUpdates;
    } else {
      failAfterRefUpdates = false;
    }
  }

  @Override
  public void afterUpdateRepos() throws ResourceConflictException {
    try {
      markCleanMerges();
      List<Change.Id> alreadyMerged = checkCommitStatus();
      findUnmergedChanges(alreadyMerged);
    } catch (IntegrationException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    }
  }

  @Override
  public void afterRefUpdates() throws ResourceConflictException {
    if (failAfterRefUpdates) {
      throw new ResourceConflictException("Failing after ref updates");
    }
  }

  private void findUnmergedChanges(List<Change.Id> alreadyMerged)
      throws ResourceConflictException, IntegrationException {
    for (SubmitStrategy strategy : strategies) {
      if (strategy instanceof CherryPick) {
        // Might have picked a subset of changes, can't do this sanity check.
        continue;
      }
      SubmitStrategy.Arguments args = strategy.args;
      Set<Change.Id> unmerged =
          args.mergeUtil.findUnmergedChanges(
              args.commits.getChangeIds(args.destBranch),
              args.rw,
              args.canMergeFlag,
              args.mergeTip.getInitialTip(),
              args.mergeTip.getCurrentTip(),
              alreadyMerged);
      for (Change.Id id : unmerged) {
        commits.problem(id, "internal error: change not reachable from new branch tip");
      }
    }
    commits.maybeFailVerbose();
  }

  private void markCleanMerges() throws IntegrationException {
    for (SubmitStrategy strategy : strategies) {
      SubmitStrategy.Arguments args = strategy.args;
      RevCommit initialTip = args.mergeTip.getInitialTip();
      args.mergeUtil.markCleanMerges(
          args.rw,
          args.canMergeFlag,
          args.mergeTip.getCurrentTip(),
          initialTip == null ? ImmutableSet.<RevCommit>of() : ImmutableSet.of(initialTip));
    }
  }

  private List<Change.Id> checkCommitStatus() throws ResourceConflictException {
    List<Change.Id> alreadyMerged = new ArrayList<>(commits.getChangeIds().size());
    for (Change.Id id : commits.getChangeIds()) {
      CodeReviewCommit commit = commits.get(id);
      CommitMergeStatus s = commit != null ? commit.getStatusCode() : null;
      if (s == null) {
        commits.problem(id, "internal error: change not processed by merge strategy");
        continue;
      }
      switch (s) {
        case CLEAN_MERGE:
        case CLEAN_REBASE:
        case CLEAN_PICK:
        case SKIPPED_IDENTICAL_TREE:
          break; // Merge strategy accepted this change.

        case ALREADY_MERGED:
          // Already an ancestor of tip.
          alreadyMerged.add(commit.getPatchsetId().getParentKey());
          break;

        case PATH_CONFLICT:
        case REBASE_MERGE_CONFLICT:
        case MANUAL_RECURSIVE_MERGE:
        case CANNOT_CHERRY_PICK_ROOT:
        case CANNOT_REBASE_ROOT:
        case NOT_FAST_FORWARD:
          // TODO(dborowitz): Reformat these messages to be more appropriate for
          // short problem descriptions.
          commits.problem(id, CharMatcher.is('\n').collapseFrom(s.getMessage(), ' '));
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
    return alreadyMerged;
  }

  @Override
  public void afterUpdateChanges() throws ResourceConflictException {
    commits.maybeFail("Error updating status");
  }
}
