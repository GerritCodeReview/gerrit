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

import static com.google.gerrit.server.git.MergeUtil.markCleanMerges;
import static com.google.gerrit.server.git.MergeUtil.mergeOneCommit;
import static com.google.gerrit.server.git.MergeUtil.reduceToMinimalMerge;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
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

public class MergeAlways extends SubmitStrategy {

  MergeAlways(final GenericFactory identifiedUserFactory,
      final PersonIdent myIdent, final ReviewDb db, final Repository repo,
      final RevWalk rw, final ObjectInserter inserter,
      final RevFlag canMergeFlag, final Set<RevCommit> alreadyAccepted,
      final Branch.NameKey destBranch, final boolean useContentMerge) {
    super(identifiedUserFactory, myIdent, db, repo, rw, inserter, canMergeFlag,
        alreadyAccepted, destBranch, useContentMerge);
  }

  @Override
  protected CodeReviewCommit _run(final CodeReviewCommit mergeTip,
      final List<CodeReviewCommit> toMerge) throws MergeException {
    reduceToMinimalMerge(mergeSorter, toMerge);

    CodeReviewCommit newMergeTip = mergeTip;
    while (!toMerge.isEmpty()) {
      newMergeTip =
          mergeOneCommit(db, identifiedUserFactory, myIdent, repo, rw,
              inserter, canMergeFlag, useContentMerge, destBranch, mergeTip,
              toMerge.remove(0));
    }

    final PatchSetApproval submitApproval =
        markCleanMerges(db, rw, canMergeFlag, newMergeTip, alreadyAccepted);
    setRefLogIdent(submitApproval);

    return newMergeTip;
  }
}
