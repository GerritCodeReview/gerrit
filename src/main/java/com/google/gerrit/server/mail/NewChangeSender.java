// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.server.GerritServer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.Message.RecipientType;

/** Sends an email alerting a user to a new change for them to review. */
public abstract class NewChangeSender extends OutgoingEmail {
  private final Set<Account.Id> reviewers = new HashSet<Account.Id>();
  private final Set<Account.Id> extraCC = new HashSet<Account.Id>();

  protected NewChangeSender(GerritServer gs, Change c) {
    super(gs, c, "newchange");
  }

  public void addReviewers(final Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  public void addExtraCC(final Collection<Account.Id> cc) {
    extraCC.addAll(cc);
  }

  @Override
  protected void init() throws MessagingException {
    super.init();

    setHeader(MimeMessage.MESSAGE_ID, getChangeMessageThreadId());

    add(RecipientType.TO, reviewers);
    add(RecipientType.CC, extraCC);
    rcptToAuthors(RecipientType.CC);
  }

  @Override
  protected void format() {
    formatSalutation();
    formatChangeDetail();
  }

  private void formatSalutation() {
    if (reviewers.isEmpty()) {
      formatDest();
      if (getChangeUrl() != null) {
        appendText("\n");
        appendText("    " + getChangeUrl() + "\n");
        appendText("\n");
      }
      appendText("\n");

    } else {
      appendText("Hello");
      for (final Iterator<Account.Id> i = reviewers.iterator(); i.hasNext();) {
        appendText(" ");
        appendText(getNameFor(i.next()));
        appendText(",");
      }
      appendText("\n");
      appendText("\n");

      appendText("I'd like you to do a code review.");
      if (getChangeUrl() != null) {
        appendText("  Please visit\n");
        appendText("\n");
        appendText("    " + getChangeUrl() + "\n");
        appendText("\n");
        appendText("to review the following change:\n");
      }
      appendText("\n");

      formatDest();
      appendText("\n");
    }
  }

  private void formatDest() {
    appendText("Change " + change.getId());
    appendText(" for ");
    appendText(change.getDest().getShortName());
    appendText(" in ");
    appendText(projectName);
    appendText(":\n");
  }
}
