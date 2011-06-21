// Copyright (C) 2011 The Android Open Source Project
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

public interface TopicConstants extends Constants {
  String previousChangeSet();
  String nextChangeSet();

  String changeSetInfoAuthor();
  String changeSetInfoDownload();

  String buttonRevertTopicBegin();
  String buttonRevertTopicSend();
  String buttonRevertTopicCancel();
  String headingRevertMessage();
  String revertTopicTitle();

  String buttonAbandonTopicBegin();
  String buttonAbandonTopicSend();
  String buttonAbandonTopicCancel();
  String headingAbandonMessage();
  String abandonTopicTitle();

  String buttonRestoreTopicBegin();
  String restoreTopicTitle();
  String buttonRestoreTopicCancel();
  String headingRestoreMessage();
  String buttonRestoreTopicSend();

  String buttonReview();
  String buttonPublishCommentsSend();
  String buttonPublishSubmitSend();
  String buttonPublishCommentsCancel();
  String headingCoverMessage();
  String headingChangeComments();

  String changeSetChangeListPrev();
  String changeSetChangeListNext();
}
