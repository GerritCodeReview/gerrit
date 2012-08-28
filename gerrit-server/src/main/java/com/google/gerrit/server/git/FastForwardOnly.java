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

import static com.google.gerrit.server.git.MergeUtil.getFirstFastForward;
import static com.google.gerrit.server.git.MergeUtil.reduceToMinimalMerge;

import com.google.gerrit.reviewdb.client.Branch.NameKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.util.List;
import java.util.Set;

public class FastForwardOnly extends SubmitStrategy {

  FastForwardOnly(final GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final ReviewDb db, final Repository repo,
      final RevWalk rw, final ObjectInserter inserter, final RevFlag CAN_MERGE,
      final Set<RevCommit> alreadyAccepted, final NameKey destBranch,
      final boolean useContentMerge) {
    super(identifiedUserFactory, myIdent, db, repo, rw, inserter, CAN_MERGE,
        alreadyAccepted, destBranch, useContentMerge);
  }

  @Override
  protected CodeReviewCommit _run(final CodeReviewCommit mergeTip,
      final List<CodeReviewCommit> toMerge) throws MergeException {
    reduceToMinimalMerge(new MergeSorter(rw, alreadyAccepted, CAN_MERGE), toMerge);
    final CodeReviewCommit newMergeTip = getFirstFastForward(mergeTip, rw, toMerge);

    // This project only permits fast-forwards, abort everything else.
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);
      n.statusCode = CommitMergeStatus.NOT_FAST_FORWARD;
    }

    markCleanMerges(newMergeTip);
    return newMergeTip;
  }
}
