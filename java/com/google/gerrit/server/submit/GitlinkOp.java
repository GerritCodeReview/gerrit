// Copyright (C) 2011 The Android Open Source Project
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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
import java.util.List;
import java.util.Optional;

/** Only used for branches without code review changes */
public class GitlinkOp implements RepoOnlyOp {

  static class Factory {
    private SubmoduleCommits submoduleCommits;
    private SubscriptionGraph subscriptionGraph;

    Factory(SubmoduleCommits submoduleCommits, SubscriptionGraph subscriptionGraph) {
      this.submoduleCommits = submoduleCommits;
      this.subscriptionGraph = subscriptionGraph;
    }

    GitlinkOp create(BranchNameKey branch) {
      return new GitlinkOp(branch, submoduleCommits, getSubscriptions(branch));
    }

    void addOp(BatchUpdate bu, BranchNameKey branch) {
      bu.addRepoOnlyOp(create(branch));
    }

    private List<SubmoduleSubscription> getSubscriptions(BranchNameKey branch) {
      return subscriptionGraph.getSubscriptions(branch).stream()
          .sorted(comparing(SubmoduleSubscription::getPath))
          .collect(toList());
    }
  }

  private final BranchNameKey branch;
  private final SubmoduleCommits commitHelper;
  private final List<SubmoduleSubscription> branchTargets;

  GitlinkOp(
      BranchNameKey branch,
      SubmoduleCommits commitHelper,
      List<SubmoduleSubscription> branchTargets) {
    this.branch = branch;
    this.commitHelper = commitHelper;
    this.branchTargets = branchTargets;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws Exception {
    Optional<CodeReviewCommit> commit = commitHelper.composeGitlinksCommit(branch, branchTargets);
    if (commit.isPresent()) {
      CodeReviewCommit c = commit.get();
      ctx.addRefUpdate(c.getParent(0), c, branch.branch());
      commitHelper.addBranchTip(branch, c);
    }
  }
}
