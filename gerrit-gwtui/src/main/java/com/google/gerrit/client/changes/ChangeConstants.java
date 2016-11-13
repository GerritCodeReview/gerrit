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

package com.google.gerrit.client.changes;

import com.google.gwt.i18n.client.Constants;

public interface ChangeConstants extends Constants {
  String statusLongNew();

  String statusLongMerged();

  String statusLongAbandoned();

  String statusLongDraft();

  String submittable();

  String readyToSubmit();

  String mergeConflict();

  String notCurrent();

  String changeEdit();

  String myDashboardTitle();

  String unknownDashboardTitle();

  String incomingReviews();

  String outgoingReviews();

  String recentlyClosed();

  String changeTableColumnSubject();

  String changeTableColumnSize();

  String changeTableColumnStatus();

  String changeTableColumnOwner();

  String changeTableColumnAssignee();

  String changeTableColumnProject();

  String changeTableColumnBranch();

  String changeTableColumnLastUpdate();

  String changeTableColumnID();

  String changeTableNone();

  String changeTableNotMergeable();

  String changeItemHelp();

  String changeTableStar();

  String changeTablePagePrev();

  String changeTablePageNext();

  String upToChangeList();

  String keyReloadChange();

  String keyNextPatchSet();

  String keyPreviousPatchSet();

  String keyReloadSearch();

  String keyPublishComments();

  String keyEditTopic();

  String keyAddReviewers();

  String keyExpandAllMessages();

  String keyCollapseAllMessages();

  String patchTableColumnName();

  String patchTableColumnComments();

  String patchTableColumnSize();

  String commitMessage();

  String mergeList();

  String patchTablePrev();

  String patchTableNext();

  String patchTableOpenDiff();

  String approvalTableEditAssigneeHint();

  String approvalTableAddReviewerHint();

  String approvalTableAddManyReviewersConfirmationDialogTitle();

  String changeInfoBlockUploaded();

  String changeInfoBlockUpdated();

  String messageNoAuthor();

  String sideBySide();

  String unifiedDiff();

  String buttonRevertChangeSend();

  String headingRevertMessage();

  String revertChangeTitle();

  String buttonCherryPickChangeSend();

  String headingCherryPickBranch();

  String cherryPickCommitMessage();

  String cherryPickTitle();

  String buttonRebaseChangeSend();

  String rebaseConfirmMessage();

  String rebaseNotPossibleMessage();

  String rebasePlaceholderMessage();

  String rebaseTitle();

  String baseDiffItem();

  String autoMerge();

  String pagedChangeListPrev();

  String pagedChangeListNext();

  String submitFailed();

  String votable();

  String pushCertMissing();

  String pushCertBad();

  String pushCertOk();

  String pushCertTrusted();
}
