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

import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MailUtil {

  public static MailRecipients getRecipientsFromFooters(
      AccountResolver accountResolver, boolean draftPatchSet,
      List<FooterLine> footerLines) throws OrmException {
    MailRecipients recipients = new MailRecipients();
    if (!draftPatchSet) {
      for (FooterLine footerLine : footerLines) {
        try {
          if (isReviewer(footerLine)) {
            recipients.reviewers.add(toAccountId(accountResolver, footerLine
                .getValue().trim()));
          } else if (footerLine.matches(FooterKey.CC)) {
            recipients.cc.add(toAccountId(accountResolver, footerLine
                .getValue().trim()));
          }
        } catch (NoSuchAccountException e) {
          continue;
        }
      }
    }
    return recipients;
  }

  public static MailRecipients getRecipientsFromReviewers(
      ReviewerSet reviewers) {
    MailRecipients recipients = new MailRecipients();
    recipients.reviewers.addAll(reviewers.byState(REVIEWER));
    recipients.cc.addAll(reviewers.byState(CC));
    return recipients;
  }

  private static Account.Id toAccountId(final AccountResolver accountResolver,
      final String nameOrEmail) throws OrmException, NoSuchAccountException {
    final Account a = accountResolver.findByNameOrEmail(nameOrEmail);
    if (a == null) {
      throw new NoSuchAccountException("\"" + nameOrEmail
          + "\" is not registered");
    }
    return a.getId();
  }

  private static boolean isReviewer(final FooterLine candidateFooterLine) {
    return candidateFooterLine.matches(FooterKey.SIGNED_OFF_BY)
        || candidateFooterLine.matches(FooterKey.ACKED_BY)
        || candidateFooterLine.matches(FooterConstants.REVIEWED_BY)
        || candidateFooterLine.matches(FooterConstants.TESTED_BY);
  }

  public static class MailRecipients {
    private final Set<Account.Id> reviewers;
    private final Set<Account.Id> cc;

    public MailRecipients() {
      this.reviewers = new HashSet<>();
      this.cc = new HashSet<>();
    }

    public MailRecipients(final Set<Account.Id> reviewers,
        final Set<Account.Id> cc) {
      this.reviewers = new HashSet<>(reviewers);
      this.cc = new HashSet<>(cc);
    }

    public void add(final MailRecipients recipients) {
      reviewers.addAll(recipients.reviewers);
      cc.addAll(recipients.cc);
    }

    public void remove(final Account.Id toRemove) {
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
}
