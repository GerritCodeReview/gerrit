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
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountProjectWatch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSetApproval;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.StarredChange;
import com.google.gerrit.client.reviewdb.UserIdentity;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.Nullable;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.util.SystemReader;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Sends an email to one or more interested parties. */
public abstract class OutgoingEmail {
  private static final String HDR_TO = "To";
  private static final String HDR_CC = "CC";

  private static final Random RNG = new Random();
  private final String messageClass;
  protected final Change change;
  protected String projectName;
  private final HashSet<Account.Id> rcptTo = new HashSet<Account.Id>();
  private final Map<String, EmailHeader> headers;
  private final List<Address> smtpRcptTo = new ArrayList<Address>();
  private Address smtpFromAddress;
  private StringBuilder body;
  private boolean inFooter;

  protected Account.Id fromId;
  protected PatchSet patchSet;
  protected PatchSetInfo patchSetInfo;
  protected ChangeMessage changeMessage;
  protected ReviewDb db;

  @Inject
  protected GerritServer server;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AccountCache accountCache;

  @Inject
  private EmailSender emailSender;

  @Inject
  private PatchSetInfoFactory patchSetInfoFactory;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  @CanonicalWebUrl
  @Nullable
  private Provider<String> urlProvider;
  private ProjectState projectState;

  protected OutgoingEmail(final Change c, final String mc) {
    change = c;
    messageClass = mc;
    headers = new LinkedHashMap<String, EmailHeader>();
  }

  protected OutgoingEmail(final String mc) {
    this(null, mc);
  }

  public void setFrom(final Account.Id id) {
    fromId = id;
  }

  public void setPatchSet(final PatchSet ps) {
    patchSet = ps;
  }

  public void setPatchSet(final PatchSet ps, final PatchSetInfo psi) {
    patchSet = ps;
    patchSetInfo = psi;
  }

  public void setChangeMessage(final ChangeMessage cm) {
    changeMessage = cm;
  }

  public void setReviewDb(final ReviewDb d) {
    db = d;
  }

  /**
   * Format and enqueue the message for delivery.
   *
   * @throws EmailException
   */
  public void send() throws EmailException {
    if (!emailSender.isEnabled()) {
      // Server has explicitly disabled email sending.
      //
      return;
    }

    init();
    format();
    if (shouldSendMessage()) {
      if (fromId != null) {
        // If we are impersonating a user, make sure they receive a CC of
        // this message so they can always review and audit what we sent
        // on their behalf to others.
        //
        add(RecipientType.CC, fromId);
      }
      if (change != null) {
        if (getChangeUrl() != null) {
          openFooter();
          appendText("To view visit ");
          appendText(getChangeUrl());
          appendText("\n");
        }
        if (getSettingsUrl() != null) {
          openFooter();
          appendText("To unsubscribe, visit ");
          appendText(getSettingsUrl());
          appendText("\n");
        }

        if (inFooter) {
          appendText("\n");
        } else {
          openFooter();
        }
        appendText("Gerrit-MessageType: " + messageClass + "\n");
        appendText("Gerrit-Project: " + projectName + "\n");
        appendText("Gerrit-Branch: " + change.getDest().getShortName() + "\n");
      }

      if (headers.get("Message-ID").isEmpty()) {
        final StringBuilder rndid = new StringBuilder();
        rndid.append("<");
        rndid.append(System.currentTimeMillis());
        rndid.append("-");
        rndid.append(Integer.toString(RNG.nextInt(999999), 36));
        rndid.append("@");
        rndid.append(SystemReader.getInstance().getHostname());
        rndid.append(">");
        setHeader("Message-ID", rndid.toString());
      }

      emailSender.send(smtpFromAddress, smtpRcptTo, headers, body.toString());
    }
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected abstract void format();

  /** Setup the message headers and envelope (TO, CC, BCC). */
  protected void init() {
    if (change != null && projectCache != null) {
      projectState = projectCache.get(change.getProject());
      projectName =
          projectState != null ? projectState.getProject().getName() : null;
    } else {
      projectState = null;
      projectName = null;
    }

    smtpFromAddress = computeFrom();
    if (changeMessage != null && changeMessage.getWrittenOn() != null) {
      setHeader("Date", new Date(changeMessage.getWrittenOn().getTime()));
    } else {
      setHeader("Date", new Date());
    }
    headers.put("From", new EmailHeader.AddressList(smtpFromAddress));
    headers.put(HDR_TO, new EmailHeader.AddressList());
    headers.put(HDR_CC, new EmailHeader.AddressList());
    if (change != null) {
      setChangeSubjectHeader();
    }
    setHeader("Message-ID", "");
    setHeader("X-Gerrit-MessageType", messageClass);
    if (change != null) {
      setHeader("X-Gerrit-ChangeId", "" + change.getChangeId());
      setListIdHeader();
      setChangeUrlHeader();
      setCommitIdHeader();
    }
    body = new StringBuilder();
    inFooter = false;

    if (change != null && db != null) {
      if (patchSet == null) {
        try {
          patchSet = db.patchSets().get(change.currentPatchSetId());
        } catch (OrmException err) {
          patchSet = null;
        }
      }

      if (patchSet != null && patchSetInfo == null) {
        try {
          patchSetInfo = patchSetInfoFactory.get(patchSet.getId());
        } catch (PatchSetInfoNotAvailableException err) {
          patchSetInfo = null;
        }
      }
    }
  }

  private Address computeFrom() {
    if (fromId != null) {
      return toAddress(fromId);
    }

    final PersonIdent pi = server.newGerritPersonIdent();
    return new Address(pi.getName(), pi.getEmailAddress());
  }

  private void setListIdHeader() {
    // Set a reasonable list id so that filters can be used to sort messages
    //
    final StringBuilder listid = new StringBuilder();
    listid.append("gerrit-");
    listid.append(projectName.replace('/', '-'));
    listid.append("@");
    listid.append(getGerritHost());

    final String listidStr = listid.toString();
    setHeader("Mailing-List", "list " + listidStr);
    setHeader("List-Id", "<" + listidStr.replace('@', '.') + ">");
    if (getSettingsUrl() != null) {
      setHeader("List-Unsubscribe", "<" + getSettingsUrl() + ">");
    }
  }

  private void setChangeUrlHeader() {
    final String u = getChangeUrl();
    if (u != null) {
      setHeader("X-Gerrit-ChangeURL", "<" + u + ">");
    }
  }

  private void setCommitIdHeader() {
    if (patchSet != null && patchSet.getRevision() != null
        && patchSet.getRevision().get() != null
        && patchSet.getRevision().get().length() > 0) {
      setHeader("X-Gerrit-Commit", patchSet.getRevision().get());
    }
  }

  private void setChangeSubjectHeader() {
    final StringBuilder subj = new StringBuilder();
    subj.append("[");
    subj.append(change.getDest().getShortName());
    subj.append("] ");
    subj.append("Change ");
    subj.append(change.getChangeId());
    subj.append(": (");
    subj.append(projectName);
    subj.append(") ");
    if (change.getSubject().length() > 60) {
      subj.append(change.getSubject().substring(0, 60));
      subj.append("...");
    } else {
      subj.append(change.getSubject());
    }
    setHeader("Subject", subj.toString());
  }

  protected String getGerritHost() {
    if (getGerritUrl() != null) {
      try {
        return new URL(getGerritUrl()).getHost();
      } catch (MalformedURLException e) {
        // Try something else.
      }
    }

    // Fall back onto whatever the local operating system thinks
    // this server is called. We hopefully didn't get here as a
    // good admin would have configured the canonical url.
    //
    return SystemReader.getInstance().getHostname();
  }

  /** Get a link to the change; null if the server doesn't know its own address. */
  protected String getChangeUrl() {
    if (change != null && getGerritUrl() != null) {
      final StringBuilder r = new StringBuilder();
      r.append(getGerritUrl());
      r.append(change.getChangeId());
      return r.toString();
    }
    return null;
  }

  private String getSettingsUrl() {
    if (getGerritUrl() != null) {
      final StringBuilder r = new StringBuilder();
      r.append(getGerritUrl());
      r.append("settings");
      return r.toString();
    }
    return null;
  }

  protected String getGerritUrl() {
    return urlProvider.get();
  }

  protected String getChangeMessageThreadId() {
    final StringBuilder r = new StringBuilder();
    r.append('<');
    r.append("gerrit");
    r.append('.');
    r.append(change.getCreatedOn().getTime());
    r.append('.');
    r.append(change.getChangeId());
    r.append('@');
    r.append(getGerritHost());
    r.append('>');
    return r.toString();
  }

  /** Set a header in the outgoing message. */
  protected void setHeader(final String name, final String value) {
    headers.put(name, new EmailHeader.String(value));
  }

  protected void setHeader(final String name, final Date date) {
    headers.put(name, new EmailHeader.Date(date));
  }

  /** Append text to the outgoing email body. */
  protected void appendText(final String text) {
    if (text != null) {
      body.append(text);
    }
  }

  private void openFooter() {
    if (!inFooter) {
      inFooter = true;
      appendText("-- \n");
    }
  }

  /** Format the sender's "cover letter", {@link #getCoverLetter()}. */
  protected void formatCoverLetter() {
    final String cover = getCoverLetter();
    if (!"".equals(cover)) {
      appendText(cover);
      appendText("\n\n");
    }
  }

  /** Get the text of the "cover letter", from {@link ChangeMessage}. */
  protected String getCoverLetter() {
    if (changeMessage != null) {
      final String txt = changeMessage.getMessage();
      if (txt != null) {
        return txt.trim();
      }
    }
    return "";
  }

  /** Format the change message and the affected file list. */
  protected void formatChangeDetail() {
    if (patchSetInfo != null) {
      appendText(patchSetInfo.getMessage().trim());
      appendText("\n");
    } else {
      appendText(change.getSubject().trim());
      appendText("\n");
    }

    if (db != null && patchSet != null) {
      appendText("---\n");
      try {
        for (Patch p : db.patches().byPatchSet(patchSet.getId())) {
          appendText(p.getChangeType().getCode() + " " + p.getFileName() + "\n");
        }
      } catch (OrmException e) {
        // Don't bother including the files if we get a failure,
        // ensure we at least send the notification message.
      }
      appendText("\n");
    }
  }

  /** Lookup a human readable name for an account, usually the "full name". */
  protected String getNameFor(final Account.Id accountId) {
    if (accountId == null) {
      return "Anonymous Coward";
    }

    final Account userAccount = accountCache.get(accountId).getAccount();
    String name = userAccount.getFullName();
    if (name == null) {
      name = userAccount.getPreferredEmail();
    }
    if (name == null) {
      name = "Anonymous Coward #" + accountId;
    }
    return name;
  }

  protected boolean shouldSendMessage() {
    if (body.length() == 0) {
      // If we have no message body, don't send.
      //
      return false;
    }

    if (rcptTo.isEmpty()) {
      // If we have nobody to send this message to, then all of our
      // selection filters previously for this type of message were
      // unable to match a destination. Don't bother sending it.
      //
      return false;
    }

    if (rcptTo.size() == 1 && rcptTo.contains(fromId)) {
      // If the only recipient is also the sender, don't bother.
      //
      return false;
    }

    return true;
  }

  /** Get the project entity the change is in; null if its been deleted. */
  protected ProjectState getProjectState() {
    return projectState;
  }

  /** Get the groups which own the project. */
  protected Set<AccountGroup.Id> getProjectOwners() {
    final ProjectState r;

    r = projectCache.get(change.getProject());
    return r != null ? r.getOwners() : Collections.<AccountGroup.Id> emptySet();
  }

  /** Schedule this message for delivery to the listed accounts. */
  protected void add(final RecipientType rt, final Collection<Account.Id> list) {
    for (final Account.Id id : list) {
      add(rt, id);
    }
  }

  /** TO or CC all vested parties (change owner, patch set uploader, author). */
  protected void rcptToAuthors(final RecipientType rt) {
    add(rt, change.getOwner());
    if (patchSet != null) {
      add(rt, patchSet.getUploader());
    }
    if (patchSetInfo != null) {
      add(rt, patchSetInfo.getAuthor());
      add(rt, patchSetInfo.getCommitter());
    }
  }

  private void add(final RecipientType rt, final UserIdentity who) {
    if (who != null && who.getAccount() != null) {
      add(rt, who.getAccount());
    }
  }

  /** BCC any user who has starred this change. */
  protected void bccStarredBy() {
    if (db != null) {
      try {
        // BCC anyone who has starred this change.
        //
        for (StarredChange w : db.starredChanges().byChange(change.getId())) {
          add(RecipientType.BCC, w.getAccountId());
        }
      } catch (OrmException err) {
        // Just don't BCC everyone. Better to send a partial message to those
        // we already have queued up then to fail deliver entirely to people
        // who have a lower interest in the change.
      }
    }
  }

  /** BCC any user who has set "notify all comments" on this project. */
  protected void bccWatchesNotifyAllComments() {
    if (db != null) {
      try {
        // BCC anyone else who has interest in this project's changes
        //
        final ProjectState ps = getProjectState();
        if (ps != null) {
          for (final AccountProjectWatch w : db.accountProjectWatches()
              .notifyAllComments(ps.getProject().getNameKey())) {
            add(RecipientType.BCC, w.getAccountId());
          }
        }
      } catch (OrmException err) {
        // Just don't CC everyone. Better to send a partial message to those
        // we already have queued up then to fail deliver entirely to people
        // who have a lower interest in the change.
      }
    }
  }

  /** Any user who has published comments on this change. */
  protected void ccAllApprovals() {
    ccApprovals(true);
  }

  /** Users who have non-zero approval codes on the change. */
  protected void ccExistingReviewers() {
    ccApprovals(false);
  }

  private void ccApprovals(final boolean includeZero) {
    if (db != null) {
      try {
        // CC anyone else who has posted an approval mark on this change
        //
        for (PatchSetApproval ap : db.patchSetApprovals().byChange(
            change.getId())) {
          if (!includeZero && ap.getValue() == 0) {
            continue;
          }
          add(RecipientType.CC, ap.getAccountId());
        }
      } catch (OrmException err) {
      }
    }
  }

  /** Schedule delivery of this message to the given account. */
  protected void add(final RecipientType rt, final Account.Id to) {
    if (!rcptTo.contains(to) && isVisibleTo(to)) {
      rcptTo.add(to);
      add(rt, toAddress(to));
    }
  }

  private boolean isVisibleTo(final Account.Id to) {
    return projectState == null
        || change == null
        || projectState.controlFor(identifiedUserFactory.create(to))
            .controlFor(change).isVisible();
  }

  /** Schedule delivery of this message to the given account. */
  protected void add(final RecipientType rt, final Address addr) {
    if (addr != null && addr.email != null && addr.email.length() > 0) {
      smtpRcptTo.add(addr);
      switch (rt) {
        case TO:
          ((EmailHeader.AddressList) headers.get(HDR_TO)).add(addr);
          break;
        case CC:
          ((EmailHeader.AddressList) headers.get(HDR_CC)).add(addr);
          break;
      }
    }
  }

  private Address toAddress(final Account.Id id) {
    final Account a = accountCache.get(id).getAccount();
    final String e = a.getPreferredEmail();
    if (e == null) {
      return null;
    }
    return new Address(a.getFullName(), e);
  }
}
