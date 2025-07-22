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

package com.google.gerrit.server.notedb;

import org.eclipse.jgit.revwalk.FooterKey;

/** Footers, that can be set in NoteDb commits. */
public class ChangeNoteFooters {
  public static final FooterKey FOOTER_ATTENTION = new FooterKey("Attention");
  public static final FooterKey FOOTER_BASE = new FooterKey("Base");
  public static final FooterKey FOOTER_BRANCH = new FooterKey("Branch");
  public static final FooterKey FOOTER_CHANGE_ID = new FooterKey("Change-id");
  public static final FooterKey FOOTER_COMMIT = new FooterKey("Commit");
  public static final FooterKey FOOTER_CONTAINS_CONFLICTS = new FooterKey("Contains-Conflicts");
  public static final FooterKey FOOTER_CURRENT = new FooterKey("Current");
  public static final FooterKey FOOTER_CUSTOM_KEYED_VALUE = new FooterKey("Custom-Keyed-Value");
  public static final FooterKey FOOTER_GROUPS = new FooterKey("Groups");
  public static final FooterKey FOOTER_HASHTAGS = new FooterKey("Hashtags");
  public static final FooterKey FOOTER_LABEL = new FooterKey("Label");
  public static final FooterKey FOOTER_COPIED_LABEL = new FooterKey("Copied-Label");
  public static final FooterKey FOOTER_MERGE_STRATEGY = new FooterKey("Merge-Strategy");
  public static final FooterKey FOOTER_NO_BASE_REASON = new FooterKey("No-Base-Reason");
  public static final FooterKey FOOTER_OURS = new FooterKey("Ours");
  public static final FooterKey FOOTER_PATCH_SET = new FooterKey("Patch-set");
  public static final FooterKey FOOTER_PATCH_SET_DESCRIPTION =
      new FooterKey("Patch-set-description");
  public static final FooterKey FOOTER_PRIVATE = new FooterKey("Private");
  public static final FooterKey FOOTER_REAL_USER = new FooterKey("Real-user");
  public static final FooterKey FOOTER_STATUS = new FooterKey("Status");
  public static final FooterKey FOOTER_SUBJECT = new FooterKey("Subject");
  public static final FooterKey FOOTER_SUBMISSION_ID = new FooterKey("Submission-id");
  public static final FooterKey FOOTER_SUBMITTED_WITH = new FooterKey("Submitted-with");
  public static final FooterKey FOOTER_TOPIC = new FooterKey("Topic");
  public static final FooterKey FOOTER_TAG = new FooterKey("Tag");
  public static final FooterKey FOOTER_THEIRS = new FooterKey("Theirs");
  public static final FooterKey FOOTER_WORK_IN_PROGRESS = new FooterKey("Work-in-progress");
  public static final FooterKey FOOTER_REVERT_OF = new FooterKey("Revert-of");
  public static final FooterKey FOOTER_CHERRY_PICK_OF = new FooterKey("Cherry-pick-of");
}
