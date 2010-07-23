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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.ssh.SshInfo;

import com.jcraft.jsch.HostKey;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Sends an email alerting a user to a new change for them to review. */
public abstract class NewChangeSender extends ChangeEmail {
  private final SshInfo sshInfo;
  private final Set<Account.Id> reviewers = new HashSet<Account.Id>();
  private final Set<Account.Id> extraCC = new HashSet<Account.Id>();

  protected NewChangeSender(EmailArguments ea, SshInfo sshInfo, Change c) {
    super(ea, c, "newchange");
    this.sshInfo = sshInfo;
  }

  public void addReviewers(final Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  public void addExtraCC(final Collection<Account.Id> cc) {
    extraCC.addAll(cc);
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    setHeader("Message-ID", getChangeMessageThreadId());

    add(RecipientType.TO, reviewers);
    add(RecipientType.CC, extraCC);
    rcptToAuthors(RecipientType.CC);
  }

  @Override
  protected void formatChange() throws EmailException {
    formatSalutation();
    formatChangeDetail();

    appendText("\n");
    appendText("  " + getPullUrl() + "\n");
  }

  private void formatSalutation() {
    final String changeUrl = getChangeUrl();

    if (reviewers.isEmpty()) {
      formatDest();
      if (changeUrl != null) {
        appendText("\n");
        appendText("    " + changeUrl + "\n");
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
      if (changeUrl != null) {
        appendText("  Please visit\n");
        appendText("\n");
        appendText("    " + changeUrl + "\n");
        appendText("\n");
        appendText("to review the following change:\n");
      }
      appendText("\n");

      formatDest();
      appendText("\n");
    }
  }

  private void formatDest() {
    appendText("Change " + change.getKey().abbreviate());
    appendText(" for ");
    appendText(change.getDest().getShortName());
    appendText(" in ");
    appendText(projectName);
    appendText(":\n");
  }

  private String getPullUrl() {
    final String host = getSshHost();
    if (host == null) {
      return "";
    }

    final StringBuilder r = new StringBuilder();
    r.append("git pull ssh://");
    r.append(host);
    r.append("/");
    r.append(projectName);
    r.append(" ");
    r.append(patchSet.getRefName());
    return r.toString();
  }

  public String getSshHost() {
    final List<HostKey> hostKeys = sshInfo.getHostKeys();
    if (hostKeys.isEmpty()) {
      return null;
    }

    final String host = hostKeys.get(0).getHost();
    if (host.startsWith("*:")) {
      return getGerritHost() + host.substring(1);
    }
    return host;
  }
}
