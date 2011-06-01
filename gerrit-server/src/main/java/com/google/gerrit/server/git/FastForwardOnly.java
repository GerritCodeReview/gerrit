// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.SchemaFactory;

import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;
import java.util.Iterator;

public class FastForwardOnly extends MergeOp {

  FastForwardOnly(final GitRepositoryManager grm, final SchemaFactory<ReviewDb> sf,
      final ProjectCache pc, final FunctionState.Factory fs,
      final ReplicationQueue rq, final MergedSender.Factory msf,
      final MergeFailSender.Factory mfsf,
      final ApprovalTypes approvalTypes, final PatchSetInfoFactory psif,
      final IdentifiedUser.GenericFactory iuf,
      final PersonIdent myIdent,
      final MergeQueue mergeQueue, final Branch.NameKey branch,
      final ChangeHookRunner hooks, final AccountCache accountCache,
      final CreateCodeReviewNotes.Factory crnf, final Project destProject) {

    super(grm, sf, pc, fs, rq, msf, mfsf, approvalTypes, psif, iuf,
          myIdent, mergeQueue, branch, hooks, accountCache, crnf, destProject);
  }

  @Override
  public void runMerge() throws MergeException {
    reduceToMinimalMerge();

    for (final Iterator<CodeReviewCommit> i = toMerge.iterator(); i.hasNext();) {
      try {
        final CodeReviewCommit n = i.next();
        if (mergeTip == null || rw.isMergedInto(mergeTip, n)) {
          mergeTip = n;
          i.remove();
          break;
        }
      } catch (IOException e) {
        throw new MergeException("Cannot fast-forward test during merge", e);
      }
    }

    // If this project only permits fast-forwards, abort everything else.
    //
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);
      n.statusCode = CommitMergeStatus.NOT_FAST_FORWARD;
    }

    markCleanMerges();
  }
}
