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
  String statusLongSubmitted();
  String statusLongMerged();
  String statusLongAbandoned();

  String changesRecentlyClosed();

  String starredHeading();
  String watchedHeading();
  String draftsHeading();
  String allOpenChanges();
  String allAbandonedChanges();
  String allMergedChanges();

  String changeTableColumnID();
  String changeTableColumnSubject();
  String changeTableColumnOwner();
  String changeTableColumnReviewers();
  String changeTableColumnProject();
  String changeTableColumnBranch();
  String changeTableColumnLastUpdate();
  String changeTableNone();

  String changeTablePrev();
  String changeTableNext();
  String changeTableOpen();
  String changeTableStar();
  String changeTablePagePrev();
  String changeTablePageNext();
  String upToChangeList();
  String expandCollapseDependencies();
  String previousPatchSet();
  String nextPatchSet();
  String keyPublishComments();

  String patchTableColumnName();
  String patchTableColumnComments();
  String patchTableColumnSize();
  String patchTableColumnDiff();
  String patchTableDiffSideBySide();
  String patchTableDiffUnified();
  String patchTableDownloadPreImage();
  String patchTableDownloadPostImage();
  String commitMessage();

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
  String changeInfoBlockUploaded();
  String changeInfoBlockUpdated();
  String changeInfoBlockStatus();
  String changePermalink();
  String changeInfoBlockMergeTest();
  String changeInfoBlockTestPassed();
  String changeInfoBlockTestFailed();

  String includedInTableBranch();
  String includedInTableTag();

  String messageNoAuthor();
  String messageExpandRecent();
  String messageExpandAll();
  String messageCollapseAll();
  String messageFailsMergeTest();

  String patchSetInfoAuthor();
  String patchSetInfoCommitter();
  String patchSetInfoDownload();
  String patchSetInfoParents();
  String initialCommit();

  String buttonRevertChangeBegin();
  String buttonRevertChangeSend();
  String buttonRevertChangeCancel();
  String headingRevertMessage();
  String revertChangeTitle();

  String buttonAbandonChangeBegin();
  String buttonAbandonChangeSend();
  String buttonAbandonChangeCancel();
  String headingAbandonMessage();
  String abandonChangeTitle();
  String oldVersionHistory();
  String baseDiffItem();

  String buttonReview();
  String buttonPublishCommentsSend();
  String buttonPublishSubmitSend();
  String buttonPublishCommentsCancel();
  String headingCoverMessage();
  String headingPatchComments();

  String buttonRestoreChangeBegin();
  String restoreChangeTitle();
  String buttonRestoreChangeCancel();
  String headingRestoreMessage();
  String buttonRestoreChangeSend();

  String pagedChangeListPrev();
  String pagedChangeListNext();

  String reviewed();
  String submitFailed();
  String buttonClose();

  String buttonDiffAllSideBySide();
  String buttonDiffAllUnified();
}