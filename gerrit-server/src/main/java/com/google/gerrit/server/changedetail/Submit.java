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

package com.google.gerrit.server.changedetail;

import static com.google.gerrit.reviewdb.client.ApprovalCategory.SUBMIT;

import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ProjectUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class Submit implements Callable<ReviewResult> {

  public interface Factory {
    Submit create(PatchSet.Id patchSetId);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final MergeOp.Factory opFactory;
  private final MergeQueue merger;
  private final ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final IdentifiedUser currentUser;

  private final PatchSet.Id patchSetId;

  @Inject
  Submit(final ChangeControl.Factory changeControlFactory,
      final MergeOp.Factory opFactory, final MergeQueue merger,
      final ReviewDb db, final GitRepositoryManager repoManager,
      final IdentifiedUser currentUser, @Assisted final PatchSet.Id patchSetId) {
    this.changeControlFactory = changeControlFactory;
    this.opFactory = opFactory;
    this.merger = merger;
    this.db = db;
    this.repoManager = repoManager;
    this.currentUser = currentUser;

    this.patchSetId = patchSetId;
  }

  @Override
  public ReviewResult call() throws IllegalStateException,
      InvalidChangeOperationException, NoSuchChangeException, OrmException,
      IOException {
    final ReviewResult result = new ReviewResult();

    final PatchSet patch = db.patchSets().get(patchSetId);
    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    result.setChangeId(changeId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }

    List<SubmitRecord> submitResult = control.canSubmit(db, patchSetId);
    if (submitResult.isEmpty()) {
      throw new IllegalStateException(
          "ChangeControl.canSubmit returned empty list");
    }

    for (SubmitRecord submitRecord : submitResult) {
      switch (submitRecord.status) {
        case OK:
          if (!control.getRefControl().canSubmit()) {
            result.addError(new ReviewResult.Error(
              ReviewResult.Error.Type.SUBMIT_NOT_PERMITTED));
          }
          break;

        case NOT_READY:
          StringBuilder errMsg = new StringBuilder();
          for (SubmitRecord.Label lbl : submitRecord.labels) {
            switch (lbl.status) {
              case OK:
                break;

              case REJECT:
                if (errMsg.length() > 0) errMsg.append("; ");
                errMsg.append("change " + changeId + ": blocked by "
                              + lbl.label);
                break;

              case NEED:
                if (errMsg.length() > 0) errMsg.append("; ");
                errMsg.append("change " + changeId + ": needs " + lbl.label);
                break;

              case IMPOSSIBLE:
                if (errMsg.length() > 0) errMsg.append("; ");
                errMsg.append("change " + changeId + ": needs " + lbl.label
                    + " (check project access)");
                break;

              default:
                throw new IllegalArgumentException(
                    "Unsupported SubmitRecord.Label.status (" + lbl.status
                    + ")");
            }
          }
          result.addError(new ReviewResult.Error(
            ReviewResult.Error.Type.SUBMIT_NOT_READY, errMsg.toString()));
          break;

        case CLOSED:
          result.addError(new ReviewResult.Error(
            ReviewResult.Error.Type.CHANGE_IS_CLOSED));
          break;

        case RULE_ERROR:
          result.addError(new ReviewResult.Error(
            ReviewResult.Error.Type.RULE_ERROR,
            submitResult.get(0).errorMessage));
          break;

        default:
          throw new IllegalStateException(
              "Unsupported SubmitRecord.status + (" + submitRecord.status
              + ")");
      }
    }

    if (!ProjectUtil.branchExists(repoManager, control.getChange().getDest())) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.DEST_BRANCH_NOT_FOUND,
          "Destination branch \"" + control.getChange().getDest().get()
              + "\" not found."));
      return result;
    }

    // Submit the change if we can
    if (result.getErrors().isEmpty()) {
      final List<PatchSetApproval> allApprovals =
          new ArrayList<PatchSetApproval>(db.patchSetApprovals().byPatchSet(
              patchSetId).toList());

      final PatchSetApproval.Key akey =
          new PatchSetApproval.Key(patchSetId, currentUser.getAccountId(),
                                   SUBMIT);

      PatchSetApproval approval = new PatchSetApproval(akey, (short) 1);
      for (final PatchSetApproval candidateApproval : allApprovals) {
        if (akey.equals(candidateApproval.getKey())) {
          candidateApproval.setValue((short) 1);
          candidateApproval.setGranted();
          approval = candidateApproval;
          break;
        }
      }
      db.patchSetApprovals().upsert(Collections.singleton(approval));

      final Change updatedChange = db.changes().atomicUpdate(changeId,
          new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          if (change.getStatus() == Change.Status.NEW) {
            change.setStatus(Change.Status.SUBMITTED);
            ChangeUtil.updated(change);
          }
          return change;
        }
      });

      if (updatedChange.getStatus() == Change.Status.SUBMITTED) {
        merger.merge(opFactory, updatedChange.getDest());
      }
    }
    return result;
  }
}
