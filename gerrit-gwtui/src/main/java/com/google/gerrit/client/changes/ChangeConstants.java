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

  String starredHeading();
  String watchedHeading();
  String draftsHeading();
  String allOpenChanges();
  String allAbandonedChanges();
  String allMergedChanges();

  String changeTableColumnSubject();
  String changeTableColumnSize();
  String changeTableColumnStatus();
  String changeTableColumnOwner();
  String changeTableColumnReviewers();
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
  String expandCollapseDependencies();
  String previousPatchSet();
  String nextPatchSet();
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
  String patchTableColumnDiff();
  String patchTableDiffSideBySide();
  String patchTableDiffUnified();
  String patchTableDownloadPreImage();
  String patchTableDownloadPostImage();
  String patchTableBinary();
  String commitMessage();
  String fileCommentHeader();

  String patchTablePrev();
  String patchTableNext();
  String patchTableOpenDiff();
  String patchTableOpenUnifiedDiff();
  String upToChangeIconLink();
  String prevPatchLinkIcon();
  String nextPatchLinkIcon();

  String changeScreenIncludedIn();
  String changeScreenDependencies();
  String changeScreenDependsOn();
  String changeScreenNeededBy();
  String changeScreenComments();
  String changeScreenAddComment();

  String approvalTableReviewer();
  String approvalTableAddReviewer();
  String approvalTableRemoveNotPermitted();
  String approvalTableCouldNotRemove();
  String approvalTableAddReviewerHint();
  String approvalTableAddManyReviewersConfirmationDialogTitle();

  String changeInfoBlockOwner();
  String changeInfoBlockProject();
  String changeInfoBlockBranch();
  String changeInfoBlockTopic();
  String changeInfoBlockTopicAlterTopicToolTip();
  String changeInfoBlockUploaded();
  String changeInfoBlockUpdated();
  String changeInfoBlockStatus();
  String changeInfoBlockSubmitType();
  String changePermalink();
  String changeInfoBlockCanMerge();
  String changeInfoBlockCanMergeYes();
  String changeInfoBlockCanMergeNo();

  String buttonAlterTopic();
  String buttonAlterTopicBegin();
  String buttonAlterTopicSend();
  String buttonAlterTopicCancel();
  String headingAlterTopicMessage();
  String alterTopicTitle();
  String alterTopicLabel();

  String includedInTableBranch();
  String includedInTableTag();

  String messageNoAuthor();
  String messageExpandMostRecent();
  String messageExpandRecent();
  String messageExpandAll();
  String messageCollapseAll();
  String messageNeedsRebaseOrHasDependency();

  String sideBySide();
  String unifiedDiff();

  String patchSetInfoAuthor();
  String patchSetInfoCommitter();
  String patchSetInfoDownload();
  String patchSetInfoParents();
  String patchSetWithDraftCommentsToolTip();
  String initialCommit();

  String buttonRebaseChange();

  String buttonRevertChangeBegin();
  String buttonRevertChangeSend();
  String headingRevertMessage();
  String revertChangeTitle();

  String buttonCherryPickChangeBegin();
  String buttonCherryPickChangeSend();
  String headingCherryPickBranch();
  String cherryPickCommitMessage();
  String cherryPickTitle();

  String buttonRebaseChangeSend();
  String rebaseConfirmMessage();
  String rebaseNotPossibleMessage();
  String rebasePlaceholderMessage();
  String rebaseTitle();

  String buttonAbandonChangeBegin();
  String buttonAbandonChangeSend();
  String headingAbandonMessage();
  String abandonChangeTitle();
  String referenceVersion();
  String baseDiffItem();
  String autoMerge();
  String firstParent();

  String buttonReview();
  String buttonPublishCommentsSend();
  String buttonPublishCommentsCancel();
  String headingCoverMessage();
  String headingPatchComments();

  String buttonRestoreChangeBegin();
  String restoreChangeTitle();
  String headingRestoreMessage();
  String buttonRestoreChangeSend();

  String buttonPublishPatchSet();

  String buttonDeleteDraftChange();
  String buttonDeleteDraftPatchSet();

  String pagedChangeListPrev();
  String pagedChangeListNext();

  String draftPatchSetLabel();

  String reviewed();
  String submitFailed();
  String buttonClose();

  String diffAllSideBySide();
  String diffAllUnified();

  String votable();

  String pushCertMissing();
  String pushCertBad();
  String pushCertOk();
  String pushCertTrusted();
}
