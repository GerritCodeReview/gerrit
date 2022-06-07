// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change.utils;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Repository;

public class CommitUtils {

  public static Change.Id insertPatchSet(
      BatchUpdate bu,
      Repository git,
      PatchSetInserter.Factory patchSetInserterFactory,
      ChangeNotes destNotes,
      CodeReviewCommit commit,
      String topic,
      @Nullable Boolean workInProgress,
      Map<String, String> validationOptions)
      throws IOException {
    Change destChange = destNotes.getChange();
    PatchSet.Id psId = ChangeUtil.nextPatchSetId(git, destChange.currentPatchSetId());
    PatchSetInserter inserter = patchSetInserterFactory.create(destNotes, psId, commit);
    inserter.setMessage("Uploaded patch set " + inserter.getPatchSetId().get() + ".");
    inserter.setTopic(topic);
    if (workInProgress != null) {
      inserter.setWorkInProgress(workInProgress);
    }
    if (shouldSetToReady(commit, destNotes, workInProgress)) {
      inserter.setWorkInProgress(false);
    }
    inserter.setValidationOptions(getValidateOptionsAsMultimap(validationOptions));
    bu.addOp(destChange.getId(), inserter);

    return destChange.getId();
  }


  public static Change.Id createNewChange(
      BatchUpdate bu,
      CodeReviewCommit commit,
      ChangeNotes.Factory changeNotesFactory,
      ChangeInserter.Factory changeInserterFactory,
      Provider<IdentifiedUser> user,
      ApprovalsUtil approvalsUtil,
      Sequences seq,
      Provider<InternalChangeQuery> queryProvider,
      String destBranchRefName,
      String commitMessage,
      String topic,
      Project.NameKey project,
      Map<String, String> validationOptions,
      @Nullable Change.Id idForNewChange,
      @Nullable Boolean workInProgress,
      @Nullable String baseCommitSha1,
      @Nullable Change cherryPickSourceChange,
      boolean keepReviewersForCherryPick,
      @Nullable Change.Id revertOf)
      throws IOException, InvalidChangeOperationException {
    Change.Id changeId = idForNewChange != null ? idForNewChange : Change.id(seq.nextChangeId());
    ChangeInserter ins = changeInserterFactory.create(changeId, commit, destBranchRefName);
    ins
        .setMessage(commitMessage)
        .setTopic(topic)
        .setRevertOf(revertOf)
        .setWorkInProgress(workInProgress)
        .setValidationOptions(getValidateOptionsAsMultimap(validationOptions));

    if (cherryPickSourceChange != null) {
      PatchSet.Id sourcePatchSetId = cherryPickSourceChange.currentPatchSetId();
      ins.setCherryPickOf(sourcePatchSetId);
      if (keepReviewersForCherryPick) {
        ReviewerSet reviewerSet =
            approvalsUtil.getReviewers(changeNotesFactory.createChecked(cherryPickSourceChange));
        Set<Account.Id> reviewers =
            new HashSet<>(reviewerSet.byState(ReviewerStateInternal.REVIEWER));
        reviewers.add(cherryPickSourceChange.getOwner());
        reviewers.remove(user.get().getAccountId());
        Set<Account.Id> ccs = new HashSet<>(reviewerSet.byState(ReviewerStateInternal.CC));
        ccs.remove(user.get().getAccountId());
        ins.setReviewersAndCcs(reviewers, ccs);
      }
    }
    // If there is a base, and the base is not merged, the groups will be overridden by the base's
    // groups.
    ins.setGroups(GroupCollector.getDefaultGroups(commit.getId()));
    if (baseCommitSha1 != null) {
      List<ChangeData> changes =
          queryProvider.get().setLimit(2)
              .byBranchCommitOpen(project.get(), destBranchRefName, baseCommitSha1);
      if (changes.size() > 1) {
        throw new InvalidChangeOperationException(
            "Several changes with key "
                + baseCommitSha1
                + " reside on the same branch. "
                + "Cannot cherry-pick on target branch.");
      }
      if (changes.size() == 1) {
        Change change = changes.get(0).change();
        ins.setGroups(changeNotesFactory.createChecked(change).getCurrentPatchSet().groups());
      }
    }
    bu.insertChange(ins);
    return changeId;
  }

  /**
   * We should set the change to be "ready for review" if: 1. workInProgress is not already set on
   * this request. 2. The patch-set doesn't have any git conflict markers. 3. The change used to be
   * work in progress (because of a previous patch-set).
   */
  private static boolean shouldSetToReady(
      CodeReviewCommit cherryPickCommit,
      ChangeNotes destChangeNotes,
      @Nullable Boolean workInProgress) {
    return workInProgress == null
        && cherryPickCommit.getFilesWithGitConflicts().isEmpty()
        && destChangeNotes.getChange().isWorkInProgress();
  }

  private static ImmutableListMultimap<String, String> getValidateOptionsAsMultimap(
      @Nullable Map<String, String> validationOptions) {
    if (validationOptions == null) {
      return ImmutableListMultimap.of();
    }

    ImmutableListMultimap.Builder<String, String> validationOptionsBuilder =
        ImmutableListMultimap.builder();
    validationOptions
        .entrySet()
        .forEach(e -> validationOptionsBuilder.put(e.getKey(), e.getValue()));
    return validationOptionsBuilder.build();
  }
}
