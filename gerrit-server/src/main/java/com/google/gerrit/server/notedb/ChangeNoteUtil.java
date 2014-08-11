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

import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.MergeUtil;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.FooterKey;

import java.util.Date;

public class ChangeNoteUtil {
  static final String GERRIT_PLACEHOLDER_HOST = "gerrit";

  // Change entity fields.
  static final FooterKey FOOTER_CHANGE_KEY = MergeUtil.CHANGE_ID;
  static final FooterKey FOOTER_STATUS = new FooterKey("Status");

  // Child entity fields.
  static final FooterKey FOOTER_LABEL = new FooterKey("Label");
  static final FooterKey FOOTER_PATCH_SET = new FooterKey("Patch-set");
  static final FooterKey FOOTER_SUBMITTED_WITH =
      new FooterKey("Submitted-with");

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

  static PersonIdent newIdent(Account author, Date when,
      PersonIdent serverIdent, String anonymousCowardName) {
    return new PersonIdent(
        new AccountInfo(author).getName(anonymousCowardName),
        author.getId().get() + "@" + GERRIT_PLACEHOLDER_HOST,
        when, serverIdent.getTimeZone());
  }

  private ChangeNoteUtil() {
  }
}
