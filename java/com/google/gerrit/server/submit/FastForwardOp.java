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

package com.google.gerrit.server.submit;

import static com.google.gerrit.server.submit.CommitMergeStatus.EMPTY_COMMIT;

import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.update.RepoContext;
import org.eclipse.jgit.lib.ObjectId;

class FastForwardOp extends SubmitStrategyOp {
  private static final ObjectId EMPTY_TREE =
      ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904");

  FastForwardOp(SubmitStrategy.Arguments args, CodeReviewCommit toMerge) {
    super(args, toMerge);
  }

  @Override
  protected void updateRepoImpl(RepoContext ctx) throws IntegrationException {
    if (args.project.is(BooleanProjectConfig.REJECT_EMPTY_COMMIT)) {
      ObjectId oldTreeId =
          toMerge.getParentCount() > 0 ? toMerge.getParent(0).getTree() : EMPTY_TREE;
      if (toMerge.getTree().equals(oldTreeId)) {
        toMerge.setStatusCode(EMPTY_COMMIT);
        return;
      }
    }

    args.mergeTip.moveTipTo(toMerge, toMerge);
  }
}
