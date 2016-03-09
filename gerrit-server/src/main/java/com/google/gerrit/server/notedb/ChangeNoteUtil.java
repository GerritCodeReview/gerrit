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

import static com.google.gerrit.server.notedb.ChangeNotes.parseException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.GerritServerId;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.FooterKey;

import java.util.Date;

public class ChangeNoteUtil {
  static final FooterKey FOOTER_BRANCH = new FooterKey("Branch");
  static final FooterKey FOOTER_CHANGE_ID = new FooterKey("Change-id");
  static final FooterKey FOOTER_COMMIT = new FooterKey("Commit");
  static final FooterKey FOOTER_GROUPS = new FooterKey("Groups");
  static final FooterKey FOOTER_HASHTAGS = new FooterKey("Hashtags");
  static final FooterKey FOOTER_LABEL = new FooterKey("Label");
  static final FooterKey FOOTER_PATCH_SET = new FooterKey("Patch-set");
  static final FooterKey FOOTER_STATUS = new FooterKey("Status");
  static final FooterKey FOOTER_SUBJECT = new FooterKey("Subject");
  static final FooterKey FOOTER_SUBMISSION_ID = new FooterKey("Submission-id");
  static final FooterKey FOOTER_SUBMITTED_WITH =
      new FooterKey("Submitted-with");
  static final FooterKey FOOTER_TOPIC = new FooterKey("Topic");

  public static String changeRefName(Change.Id id) {
    StringBuilder r = new StringBuilder();
    r.append(RefNames.REFS_CHANGES);
    int n = id.get();
    int m = n % 100;
    if (m < 10) {
      r.append('0');
    }
    r.append(m);
    r.append('/');
    r.append(n);
    r.append(RefNames.META_SUFFIX);
    return r.toString();
  }

  private final String serverId;

  @Inject
  ChangeNoteUtil(@GerritServerId String serverId) {
    this.serverId = serverId;
  }

  @VisibleForTesting
  public PersonIdent newIdent(Account author, Date when,
      PersonIdent serverIdent, String anonymousCowardName) {
    return new PersonIdent(
        author.getName(anonymousCowardName),
        author.getId().get() + "@" + serverId,
        when, serverIdent.getTimeZone());
  }

  public Account.Id parseIdent(PersonIdent ident, Change.Id changeId)
      throws ConfigInvalidException {
    String email = ident.getEmailAddress();
    int at = email.indexOf('@');
    if (at >= 0) {
      String host = email.substring(at + 1, email.length());
      if (host.equals(serverId)) {
        Integer id = Ints.tryParse(email.substring(0, at));
        if (id != null) {
          return new Account.Id(id);
        }
      }
    }
    throw parseException(changeId, "invalid identity, expected <id>@%s: %s",
        serverId, email);
  }
}
