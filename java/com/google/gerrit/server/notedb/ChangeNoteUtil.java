// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.config.GerritServerId;
import com.google.inject.Inject;
import java.util.Date;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.FooterKey;

public class ChangeNoteUtil {
  public static final FooterKey FOOTER_ASSIGNEE = new FooterKey("Assignee");
  public static final FooterKey FOOTER_BRANCH = new FooterKey("Branch");
  public static final FooterKey FOOTER_CHANGE_ID = new FooterKey("Change-id");
  public static final FooterKey FOOTER_COMMIT = new FooterKey("Commit");
  public static final FooterKey FOOTER_CURRENT = new FooterKey("Current");
  public static final FooterKey FOOTER_GROUPS = new FooterKey("Groups");
  public static final FooterKey FOOTER_HASHTAGS = new FooterKey("Hashtags");
  public static final FooterKey FOOTER_LABEL = new FooterKey("Label");
  public static final FooterKey FOOTER_PATCH_SET = new FooterKey("Patch-set");
  public static final FooterKey FOOTER_PATCH_SET_DESCRIPTION =
      new FooterKey("Patch-set-description");
  public static final FooterKey FOOTER_PRIVATE = new FooterKey("Private");
  public static final FooterKey FOOTER_READ_ONLY_UNTIL = new FooterKey("Read-only-until");
  public static final FooterKey FOOTER_REAL_USER = new FooterKey("Real-user");
  public static final FooterKey FOOTER_STATUS = new FooterKey("Status");
  public static final FooterKey FOOTER_SUBJECT = new FooterKey("Subject");
  public static final FooterKey FOOTER_SUBMISSION_ID = new FooterKey("Submission-id");
  public static final FooterKey FOOTER_SUBMITTED_WITH = new FooterKey("Submitted-with");
  public static final FooterKey FOOTER_TOPIC = new FooterKey("Topic");
  public static final FooterKey FOOTER_TAG = new FooterKey("Tag");
  public static final FooterKey FOOTER_WORK_IN_PROGRESS = new FooterKey("Work-in-progress");
  public static final FooterKey FOOTER_REVERT_OF = new FooterKey("Revert-of");

  static final String AUTHOR = "Author";
  static final String BASE_PATCH_SET = "Base-for-patch-set";
  static final String COMMENT_RANGE = "Comment-range";
  static final String FILE = "File";
  static final String LENGTH = "Bytes";
  static final String PARENT = "Parent";
  static final String PARENT_NUMBER = "Parent-number";
  static final String PATCH_SET = "Patch-set";
  static final String REAL_AUTHOR = "Real-author";
  static final String REVISION = "Revision";
  static final String UUID = "UUID";
  static final String UNRESOLVED = "Unresolved";
  static final String TAG = FOOTER_TAG.getName();

  private final LegacyChangeNoteRead legacyChangeNoteRead;
  private final ChangeNoteJson changeNoteJson;
  private final String serverId;

  @Inject
  public ChangeNoteUtil(
      ChangeNoteJson changeNoteJson,
      LegacyChangeNoteRead legacyChangeNoteRead,
      @GerritServerId String serverId) {
    this.serverId = serverId;
    this.changeNoteJson = changeNoteJson;
    this.legacyChangeNoteRead = legacyChangeNoteRead;
  }

  public LegacyChangeNoteRead getLegacyChangeNoteRead() {
    return legacyChangeNoteRead;
  }

  public ChangeNoteJson getChangeNoteJson() {
    return changeNoteJson;
  }

  public PersonIdent newIdent(Account.Id authorId, Date when, PersonIdent serverIdent) {
    return new PersonIdent(
        "Gerrit User " + authorId.toString(),
        authorId.get() + "@" + serverId,
        when,
        serverIdent.getTimeZone());
  }

  @VisibleForTesting
  public PersonIdent newIdent(Account author, Date when, PersonIdent serverIdent) {
    return new PersonIdent(
        "Gerrit User " + author.getId(),
        author.getId().get() + "@" + serverId,
        when,
        serverIdent.getTimeZone());
  }
}
