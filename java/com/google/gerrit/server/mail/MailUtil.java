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

package com.google.gerrit.server.mail;

import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.ReviewerSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class MailUtil {

  public static MailRecipients getRecipientsFromReviewers(ReviewerSet reviewers) {
    MailRecipients recipients = new MailRecipients();
    recipients.reviewers.addAll(reviewers.byState(REVIEWER));
    recipients.cc.addAll(reviewers.byState(CC));
    return recipients;
  }

  public static class MailRecipients {
    private final Set<Account.Id> reviewers;
    private final Set<Account.Id> cc;

    public MailRecipients() {
      this.reviewers = new HashSet<>();
      this.cc = new HashSet<>();
    }

    public MailRecipients(Set<Account.Id> reviewers, Set<Account.Id> cc) {
      this.reviewers = new HashSet<>(reviewers);
      this.cc = new HashSet<>(cc);
    }

    public void add(MailRecipients recipients) {
      reviewers.addAll(recipients.reviewers);
      cc.addAll(recipients.cc);
    }

    public void remove(Account.Id toRemove) {
      reviewers.remove(toRemove);
      cc.remove(toRemove);
    }

    public Set<Account.Id> getReviewers() {
      return Collections.unmodifiableSet(reviewers);
    }

    public Set<Account.Id> getCcOnly() {
      final Set<Account.Id> cc = new HashSet<>(this.cc);
      cc.removeAll(reviewers);
      return Collections.unmodifiableSet(cc);
    }

    public Set<Account.Id> getAll() {
      final Set<Account.Id> all = new HashSet<>(reviewers.size() + cc.size());
      all.addAll(reviewers);
      all.addAll(cc);
      return Collections.unmodifiableSet(all);
    }
  }

  /** allow wildcard matching for {@code domains} */
  public static Pattern glob(String[] domains) {
    // if domains is not set, match anything
    if (domains == null || domains.length == 0) {
      return Pattern.compile(".*");
    }

    StringBuilder sb = new StringBuilder();
    for (String domain : domains) {
      String quoted = "\\Q" + domain.replace("\\E", "\\E\\\\E\\Q") + "\\E|";
      sb.append(quoted.replace("*", "\\E.*\\Q"));
    }
    return Pattern.compile(sb.substring(0, sb.length() - 1));
  }
}
