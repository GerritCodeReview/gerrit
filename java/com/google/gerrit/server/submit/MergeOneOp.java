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

import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.update.RepoContext;
import java.io.IOException;
import org.eclipse.jgit.lib.PersonIdent;

class MergeOneOp extends SubmitStrategyOp {
  MergeOneOp(SubmitStrategy.Arguments args, CodeReviewCommit toMerge) {
    super(args, toMerge);
  }

  @Override
  public void updateRepoImpl(RepoContext ctx) throws IntegrationException, IOException {
    PersonIdent caller =
        ctx.getIdentifiedUser()
            .newCommitterIdent(args.serverIdent.getWhen(), args.serverIdent.getTimeZone());
    if (args.mergeTip.getCurrentTip() == null) {
      throw new IllegalStateException(
          "cannot merge commit "
              + toMerge.name()
              + " onto a null tip; expected at least one fast-forward prior to"
              + " this operation");
    }
    CodeReviewCommit merged =
        args.mergeUtil.mergeOneCommit(
            caller,
            args.serverIdent,
            args.rw,
            ctx.getInserter(),
            ctx.getRepoView().getConfig(),
            args.destBranch,
            args.mergeTip.getCurrentTip(),
            toMerge);
    if (args.project.is(BooleanProjectConfig.REJECT_EMPTY_COMMIT)
        && merged.getTree().equals(merged.getParent(0).getTree())) {
      toMerge.setStatusCode(EMPTY_COMMIT);
      return;
    }
    args.mergeTip.moveTipTo(amendGitlink(merged), toMerge);
  }
}
