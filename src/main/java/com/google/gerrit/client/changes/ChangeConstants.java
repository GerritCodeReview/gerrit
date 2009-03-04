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
  String draftsHeading();
  String allOpenChanges();
  String allAbandonedChanges();
  String allMergedChanges();

  String changeTableColumnID();
  String changeTableColumnSubject();
  String changeTableColumnOwner();
  String changeTableColumnReviewers();
  String changeTableColumnProject();
  String changeTableColumnLastUpdate();
  String changeTableNone();

  String patchTableColumnName();
  String patchTableColumnComments();
  String patchTableColumnDiff();
  String patchTableDiffSideBySide();
  String patchTableDiffUnified();

  String changeScreenDescription();
  String changeScreenDependencies();
  String changeScreenDependsOn();
  String changeScreenNeededBy();
  String changeScreenApprovals();
  String changeScreenMessages();

  String approvalTableReviewer();
  String approvalTableAddReviewer();

  String changeInfoBlockOwner();
  String changeInfoBlockProject();
  String changeInfoBlockBranch();
  String changeInfoBlockUploaded();
  String changeInfoBlockUpdated();
  String changeInfoBlockStatus();
  String changePermalink();

  String messageNoAuthor();
  String messageExpandRecent();
  String messageExpandAll();
  String messageCollapseAll();

  String patchSetInfoAuthor();
  String patchSetInfoCommitter();
  String patchSetInfoDownload();

  String buttonAbandonChangeBegin();
  String buttonAbandonChangeSend();
  String buttonAbandonChangeCancel();
  String headingAbandonMessage();

  String buttonPublishCommentsBegin();
  String buttonPublishCommentsSend();
  String buttonPublishCommentsCancel();
  String headingCoverMessage();
  String headingPatchComments();

  String pagedChangeListPrev();
  String pagedChangeListNext();
}
