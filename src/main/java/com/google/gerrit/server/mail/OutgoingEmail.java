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

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountProjectWatch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.StarredChange;
import com.google.gerrit.client.reviewdb.UserIdentity;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.pgm.Version;
import com.google.gerrit.server.GerritServer;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.PersonIdent;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;

/** Sends an email to one or more interested parties. */
public abstract class OutgoingEmail {
  private final String messageClass;
  protected final GerritServer server;
  private final javax.mail.Session transport;
  protected final Change change;
  protected final String projectName;
  private final HashSet<Account.Id> rcptTo = new HashSet<Account.Id>();
  private MimeMessage msg;
  private StringBuilder body;
  private boolean inFooter;

  private String myUrl;
  protected Account.Id fromId;
  protected PatchSet patchSet;
  protected PatchSetInfo patchSetInfo;
  protected ChangeMessage changeMessage;
  protected ReviewDb db;

  protected OutgoingEmail(final GerritServer gs, final Change c, final String mc) {
    server = gs;
    transport = server.getOutgoingMail();
    change = c;
    projectName = change.getDest().getParentKey().get();
    messageClass = mc;
  }

  public void setFrom(final Account.Id id) {
    fromId = id;
  }

  public void setHttpServletRequest(final HttpServletRequest req) {
    myUrl = GerritServer.serverUrl(req);
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

  /** Format and enqueue the message for delivery. */
  public void send() throws MessagingException {
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

      msg.setText(body.toString(), "UTF-8");
      Transport.send(msg);
    }
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected abstract void format();

  /** Setup the message headers and envelope (TO, CC, BCC). */
  protected void init() throws MessagingException {
    msg = new MimeMessage(transport);
    if (changeMessage != null && changeMessage.getWrittenOn() != null) {
      msg.setSentDate(new Date(changeMessage.getWrittenOn().getTime()));
    } else {
      msg.setSentDate(new Date());
    }
    msg.setFrom(computeFrom());
    setHeader("User-Agent", "Gerrit/" + Version.getVersion());
    setHeader("X-Gerrit-ChangeId", "" + change.getChangeId());
    setHeader("X-Gerrit-MessageType", messageClass);
    setListIdHeader();
    setChangeUrlHeader();
    setCommitIdHeader();
    setSubjectHeader();
    body = new StringBuilder();
    inFooter = false;

    if (db != null) {
      if (patchSet == null) {
        try {
          patchSet = db.patchSets().get(change.currentPatchSetId());
        } catch (OrmException err) {
          patchSet = null;
        }
      }

      if (patchSet != null && patchSetInfo == null) {
        try {
          patchSetInfo = db.patchSetInfo().get(patchSet.getId());
        } catch (OrmException err) {
          patchSetInfo = null;
        }
      }
    }
  }

  private Address computeFrom() throws AddressException {
    if (fromId != null) {
      return toAddress(fromId);
    }
    final PersonIdent pi = server.newGerritPersonIdent();
    try {
      return new InternetAddress(pi.getName(), pi.getEmailAddress());
    } catch (UnsupportedEncodingException e) {
      return new InternetAddress(pi.getEmailAddress());
    }
  }

  private void setListIdHeader() throws MessagingException {
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

  private void setChangeUrlHeader() throws MessagingException {
    final String u = getChangeUrl();
    if (u != null) {
      setHeader("X-Gerrit-ChangeURL", "<" + u + ">");
    }
  }

  private void setCommitIdHeader() throws MessagingException {
    if (patchSet != null && patchSet.getRevision() != null
        && patchSet.getRevision().get() != null
        && patchSet.getRevision().get().length() > 0) {
      setHeader("X-Gerrit-Commit", patchSet.getRevision().get());
    }
  }

  private void setSubjectHeader() throws MessagingException {
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
    msg.setSubject(subj.toString());
  }

  private String getGerritHost() {
    if (server.getCanonicalURL() != null) {
      try {
        return new URL(server.getCanonicalURL()).getHost();
      } catch (MalformedURLException e) {
        // Try something else.
      }
    }

    if (myUrl != null) {
      try {
        return new URL(myUrl).getHost();
      } catch (MalformedURLException e) {
        // Try something else.
      }
    }

    // Fall back onto whatever the local operating system thinks
    // this server is called. We hopefully didn't get here as a
    // good admin would have configured the canonical url.
    //
    try {
      return InetAddress.getLocalHost().getCanonicalHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }

  /** Get a link to the change; null if the server doesn't know its own address. */
  protected String getChangeUrl() {
    if (getGerritUrl() != null) {
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

  private String getGerritUrl() {
    if (server.getCanonicalURL() != null) {
      return server.getCanonicalURL();
    }
    return myUrl;
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
  protected void setHeader(final String name, final String value)
      throws MessagingException {
    msg.setHeader(name, value);
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

    final Account userAccount = Common.getAccountCache().get(accountId);
    if (userAccount == null) {
      return "Anonymous Coward #" + accountId;
    }

    String name = userAccount.getFullName();
    if (name == null) {
      name = userAccount.getPreferredEmail();
    }
    if (name == null) {
      name = "Anonymous Coward #" + accountId;
    }
    return name;
  }

  private boolean shouldSendMessage() {
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
  protected Project getProject() {
    final ProjectCache.Entry r;

    r = Common.getProjectCache().get(change.getDest().getParentKey());
    return r != null ? r.getProject() : null;
  }

  /** Schedule this message for delivery to the listed accounts. */
  protected void add(final RecipientType rt, final Collection<Account.Id> list)
      throws MessagingException {
    for (final Account.Id id : list) {
      add(rt, id);
    }
  }

  /** TO or CC all vested parties (change owner, patch set uploader, author). */
  protected void rcptToAuthors(final RecipientType rt)
      throws MessagingException {
    add(rt, change.getOwner());
    if (patchSet != null) {
      add(rt, patchSet.getUploader());
    }
    if (patchSetInfo != null) {
      add(rt, patchSetInfo.getAuthor());
      add(rt, patchSetInfo.getCommitter());
    }
  }

  private void add(final RecipientType rt, final UserIdentity who)
      throws MessagingException {
    if (who != null && who.getAccount() != null) {
      add(rt, who.getAccount());
    }
  }

  /** BCC any user who has starred this change. */
  protected void bccStarredBy() throws MessagingException {
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
  protected void bccWatchesNotifyAllComments() throws MessagingException {
    if (db != null) {
      try {
        // BCC anyone else who has interest in this project's changes
        //
        final Project project = getProject();
        if (project != null) {
          for (AccountProjectWatch w : db.accountProjectWatches()
              .notifyAllComments(project.getId())) {
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
  protected void ccAllApprovals() throws MessagingException {
    ccApprovals(true);
  }

  /** Users who have non-zero approval codes on the change. */
  protected void ccExistingReviewers() throws MessagingException {
    ccApprovals(false);
  }

  private void ccApprovals(final boolean includeZero) throws MessagingException {
    if (db != null) {
      try {
        // CC anyone else who has posted an approval mark on this change
        //
        for (ChangeApproval ap : db.changeApprovals().byChange(change.getId())) {
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
  protected void add(final RecipientType rt, final Account.Id to)
      throws MessagingException {
    if (rcptTo.add(to)) {
      final Address addr = toAddress(to);
      if (addr != null) {
        msg.addRecipient(rt, addr);
      }
    }
  }

  private Address toAddress(final Account.Id id) throws AddressException {
    final Account a = Common.getAccountCache().get(id);
    if (a == null) {
      return null;
    }

    final String e = a.getPreferredEmail();
    if (e == null) {
      return null;
    }

    try {
      final String an = a.getFullName();
      return new InternetAddress(e, an);
    } catch (UnsupportedEncodingException e1) {
      return new InternetAddress(e);
    }
  }
}
