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
  String patchBase();

  String patchSet();

  String upToChange();

  String openReply();

  String linePrev();

  String lineNext();

  String chunkPrev();

  String chunkNext();

  String commentPrev();

  String commentNext();

  String focusSideA();

  String focusSideB();

  String expandComment();

  String expandAllCommentsOnCurrentLine();

  String toggleSideA();

  String toggleIntraline();

  String showPreferences();

  String toggleReviewed();

  String markAsReviewedAndGoToNext();

  String commentEditorSet();

  String commentInsert();

  String commentSaveDraft();

  String commentCancelEdit();

  String whitespaceIGNORE_NONE();

  String whitespaceIGNORE_TRAILING();

  String whitespaceIGNORE_LEADING_AND_TRAILING();

  String whitespaceIGNORE_ALL();

  String previousFileHelp();

  String nextFileHelp();

  String download();

  String edit();

  String blame();

  String addFileCommentToolTip();

  String cannedReplyDone();

  String sideBySideDiff();

  String unifiedDiff();
}
