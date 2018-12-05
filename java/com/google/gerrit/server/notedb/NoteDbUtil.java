// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.CharMatcher;
import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Account;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;

public class NoteDbUtil {

  /**
   * Returns an AccountId for the given email address. Returns empty if the address isn't on this
   * server.
   */
  public static Optional<Account.Id> parseIdent(PersonIdent ident, String serverId) {
    String email = ident.getEmailAddress();
    int at = email.indexOf('@');
    if (at >= 0) {
      String host = email.substring(at + 1, email.length());
      if (host.equals(serverId)) {
        Integer id = Ints.tryParse(email.substring(0, at));
        if (id != null) {
          return Optional.of(new Account.Id(id));
        }
      } else {
        // Change is coming from a different cluster of servers,
        // not the one we are trying to get the ChangeNotes from.
        Integer id = Ints.tryParse(email.substring(0, at));
        if (id != null) {
          id *= -1;
          return Optional.of(new Account.Id(id));
        }
      }
    }
    return Optional.empty();
  }

  private NoteDbUtil() {}

  public static String formatTime(PersonIdent ident, Timestamp t) {
    GitDateFormatter dateFormatter = new GitDateFormatter(Format.DEFAULT);
    // TODO(dborowitz): Use a ThreadLocal or use Joda.
    PersonIdent newIdent = new PersonIdent(ident, t);
    return dateFormatter.formatDate(newIdent);
  }

  private static final CharMatcher INVALID_FOOTER_CHARS = CharMatcher.anyOf("\r\n\0");

  static String sanitizeFooter(String value) {
    // Remove characters that would confuse JGit's footer parser if they were
    // included in footer values, for example by splitting the footer block into
    // multiple paragraphs.
    //
    // One painful example: RevCommit#getShorMessage() might return a message
    // containing "\r\r", which RevCommit#getFooterLines() will treat as an
    // empty paragraph for the purposes of footer parsing.
    return INVALID_FOOTER_CHARS.trimAndCollapseFrom(value, ' ');
  }
}
