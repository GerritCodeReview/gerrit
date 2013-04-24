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

package com.google.gerrit.client.patches;

import com.google.gwt.i18n.client.Constants;

public interface PatchConstants extends Constants {
  String draft();

  String buttonReply();
  String buttonEdit();
  String buttonSave();
  String buttonCancel();
  String buttonDiscard();

  String noDifference();
  String patchBase();
  String patchBaseAutoMerge();
  String patchHeaderPatchSet();
  String patchHeaderOld();
  String patchHeaderNew();
  String patchSet();

  String patchHistoryTitle();
  String disabledOnLargeFiles();
  String intralineFailure();
  String intralineTimeout();
  String illegalNumberOfColumns();

  String upToChange();
  String linePrev();
  String lineNext();
  String chunkPrev();
  String chunkNext();
  String commentPrev();
  String commentNext();
  String fileList();
  String expandComment();

  String toggleReviewed();
  String markAsReviewedAndGoToNext();

  String commentEditorSet();
  String commentInsert();
  String commentSaveDraft();
  String commentCancelEdit();

  String whitespaceIGNORE_NONE();
  String whitespaceIGNORE_SPACE_AT_EOL();
  String whitespaceIGNORE_SPACE_CHANGE();
  String whitespaceIGNORE_ALL_SPACE();

  String previousFileHelp();
  String nextFileHelp();

  String reviewedAnd();
  String next();
  String download();
  String addFileCommentToolTip();
  String addFileCommentByDoubleClick();

  String buttonReplyDone();
  String cannedReplyDone();

  String fileTypeSymlink();
  String fileTypeGitlink();

  String patchSkipRegionStart();
  String patchSkipRegionEnd();
}
