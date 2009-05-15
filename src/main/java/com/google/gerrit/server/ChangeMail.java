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

package com.google.gerrit.server;

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.AccountProjectWatch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.StarredChange;
import com.google.gerrit.client.reviewdb.UserIdentity;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gerrit.server.patch.PatchFile;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Repository;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;

public class ChangeMail {
  private final GerritServer server;
  private final javax.mail.Session transport;
  private final Change change;
  private final String projectName;
  private final HashSet<Account.Id> rcptTo = new HashSet<Account.Id>();
  private MimeMessage msg;
  private StringBuilder body;
  private boolean inFooter;

  private String myUrl;
  private Account.Id fromId;
  private PatchSet patchSet;
  private PatchSetInfo patchSetInfo;
  private ChangeMessage message;
  private List<PatchLineComment> comments = Collections.emptyList();
  private final Set<Account.Id> reviewers = new HashSet<Account.Id>();
  private final Set<Account.Id> extraCC = new HashSet<Account.Id>();
  private ReviewDb db;

  public ChangeMail(final GerritServer gs, final Change c) {
    server = gs;
    transport = server.getOutgoingMail();
    change = c;
    projectName = change.getDest().getParentKey().get();
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
    message = cm;
  }

  public void setPatchLineComments(final List<PatchLineComment> plc) {
    comments = plc;
  }

  public void addReviewers(final Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  public void addExtraCC(final Collection<Account.Id> cc) {
    extraCC.addAll(cc);
  }

  public void setReviewDb(final ReviewDb d) {
    db = d;
  }

  public void sendNewChange() throws MessagingException {
    begin("newchange");
    newChangeTo();
    if (!haveRcptTo()) {
      // No destinations at this point makes it very moot to mail,
      // nobody was interested in the change or was told to look
      // at it by the caller.
      //
      return;
    }
    newChangeCc();

    body.append("New change ");
    body.append(change.getChangeId());
    body.append(" for ");
    body.append(change.getDest().getShortName());
    body.append(":\n\n");

    newChangePatchSetInfo();
    newChangeFooter();

    msg.setMessageID(changeMessageThreadId());
    send();
  }

  private void newChangePatchSetInfo() {
    if (changeUrl() != null) {
      body.append("  ");
      body.append(changeUrl());
      body.append("\n\n");
    }

    if (patchSetInfo != null) {
      body.append(patchSetInfo.getMessage().trim());
      body.append("\n\n");
    } else {
      body.append(change.getSubject().trim());
      body.append("\n\n");
    }

    if (db != null && patchSet != null) {
      body.append("---\n");
      try {
        for (Patch p : db.patches().byPatchSet(patchSet.getId())) {
          body.append(p.getChangeType().getCode());
          body.append(' ');
          body.append(p.getFileName());
          body.append('\n');
        }
      } catch (OrmException e) {
        // Don't bother including the files if we get a failure,
        // ensure we at least send the notification message.
      }
      body.append("\n");
    }
  }

  private void newChangeFooter() {
    appendChangeRequestAndFooter();
  }

  private void appendChangeRequestAndFooter() {
    if (changeUrl() != null) {
      body.append("To perform this review, please visit\n\n   ");
      body.append(changeUrl());
      body.append("\n\n");
      openFooter();
    }
  }

  public void sendNewPatchSet() throws MessagingException {
    begin("newpatchset");
    newChangeTo();
    if (!haveRcptTo()) {
      // No destinations at this point makes it very moot to mail,
      // nobody was interested in the change or was told to look
      // at it by the caller.
      //
      return;
    }
    newChangeCc();
    starredTo();

    body.append("Uploaded replacement patch set ");
    body.append(patchSet.getPatchSetId());
    body.append(" for change ");
    body.append(change.getChangeId());
    body.append(":\n\n");

    newChangePatchSetInfo();
    newChangeFooter();
    initInReplyToChange();
    send();
  }

  public void sendComment() throws MessagingException {
    begin("comment");
    if (message != null) {
      body.append(message.getMessage().trim());
      if (body.length() > 0) {
        body.append("\n\n");
      }
    }

    Map<Patch.Key, Patch> patches = Collections.emptyMap();
    Repository repo = null;

    if (!comments.isEmpty()) {
      try {
        final PatchSet.Id psId = patchSet.getId();
        patches = db.patches().toMap(db.patches().byPatchSet(psId));
      } catch (OrmException e) {
        // Can't read the patch table? Don't quote file lines.
        patches = Collections.emptyMap();
      }
      try {
        repo = server.getRepositoryCache().get(projectName);
      } catch (InvalidRepositoryException e) {
        repo = null;
        patches = Collections.emptyMap();
      }

      body.append("Comments on Patch Set ");
      body.append(patchSet.getPatchSetId());
      body.append(":\n\n");
    }

    Patch.Key currentFile = null;
    PatchFile file = null;
    for (final PatchLineComment c : comments) {
      final Patch.Key pk = c.getKey().getParentKey();
      final int lineNbr = c.getLine();
      final short side = c.getSide();

      if (!pk.equals(currentFile)) {
        body.append("....................................................\n");
        body.append("File ");
        body.append(pk.get());
        body.append("\n");
        currentFile = pk;

        final Patch p = patches.get(pk);
        if (p != null && repo != null) {
          try {
            file = new PatchFile(repo, patchSet.getRevision(), p);
          } catch (Throwable e) {
            // Don't quote the line if we can't load it.
          }
        } else {
          file = null;
        }
      }

      body.append("Line ");
      body.append(lineNbr);
      if (file != null) {
        try {
          final String lineStr = file.getLine(side, lineNbr);
          body.append(": ");
          body.append(lineStr);
        } catch (Throwable cce) {
          // Don't quote the line if we can't safely convert it.
        }
      }
      body.append("\n");

      body.append(c.getMessage().trim());
      body.append("\n\n");
    }

    if (body.length() == 0) {
      // If we have no body, don't bother generating an email.
      //
      return;
    }

    appendChangeRequestAndFooter();

    initInReplyToChange();
    commentTo();
    starredTo();
    send();
  }

  public void sendRequestReview() throws MessagingException {
    begin("requestReview");
    final Account a = Common.getAccountCache().get(fromId);
    if (a == null || a.getFullName() == null || a.getFullName().length() == 0) {
      body.append("A Gerrit user");
    } else {
      body.append(a.getFullName());
    }
    body.append(" has requested that you review a change:\n\n");
    body.append(change.getChangeId());
    body.append(" - ");
    body.append(change.getSubject());
    body.append("\n\n");

    appendChangeRequestAndFooter();

    initInReplyToChange();
    add(RecipientType.TO, reviewers);
    add(RecipientType.CC, extraCC);
    if (fromId != null) {
      add(RecipientType.CC, fromId);
    }
    send();
  }

  public void sendMerged() throws MessagingException {
    begin("merged");
    body.append("Change ");
    body.append(change.getChangeId());
    if (patchSetInfo != null && patchSetInfo.getAuthor() != null
        && patchSetInfo.getAuthor().getName() != null) {
      body.append(" by ");
      body.append(patchSetInfo.getAuthor().getName());
    }
    body.append(" submitted to ");
    body.append(change.getDest().getShortName());
    body.append(".\n\n");

    newChangePatchSetInfo();

    if (changeUrl() != null) {
      openFooter();
      body.append("To view visit ");
      body.append(changeUrl());
      body.append("\n");
    }

    initInReplyToChange();
    submittedTo();
    starredTo();
    send();
  }

  public void sendMergeFailed() throws MessagingException {
    begin("comment");
    body.append("Change ");
    body.append(change.getChangeId());
    if (patchSetInfo != null && patchSetInfo.getAuthor() != null
        && patchSetInfo.getAuthor().getName() != null) {
      body.append(" by ");
      body.append(patchSetInfo.getAuthor().getName());
    }
    body.append(" FAILED to submit to ");
    body.append(change.getDest().getShortName());
    body.append(".\n\n");

    if (message != null) {
      body.append("Error message:\n");
      body.append("....................................................\n");
      body.append(message.getMessage().trim());
      if (body.length() > 0) {
        body.append("\n\n");
      }
    }

    if (changeUrl() != null) {
      openFooter();
      body.append("To view visit ");
      body.append(changeUrl());
      body.append("\n");
    }

    initInReplyToChange();
    submittedTo();
    starredTo();
    send();
  }

  public void sendAbandoned() throws MessagingException {
    begin("abandon");
    final Account a = Common.getAccountCache().get(fromId);
    if (a == null || a.getFullName() == null || a.getFullName().length() == 0) {
      body.append("A Gerrit user");
    } else {
      body.append(a.getFullName());
    }

    body.append(" has abandoned a change:\n\n");
    body.append(change.getChangeId());
    body.append(" - ");
    body.append(change.getSubject());
    body.append("\n\n");

    if (message != null) {
      body.append(message.getMessage().trim());
      if (body.length() > 0) {
        body.append("\n\n");
      }
    }

    if (changeUrl() != null) {
      openFooter();
      body.append("To view visit ");
      body.append(changeUrl());
      body.append("\n");
    }

    initInReplyToChange();
    commentTo();
    starredTo();
    send();
  }

  private void newChangeTo() throws MessagingException {
    add(RecipientType.TO, reviewers);
    add(RecipientType.CC, extraCC);
    if (patchSetInfo != null) {
      // Make sure the author/committer get notice of a change that
      // they will be blamed later on for writing.
      //
      add(RecipientType.CC, patchSetInfo.getAuthor());
      add(RecipientType.CC, patchSetInfo.getCommitter());
    }

    final ProjectCache.Entry cacheEntry =
        Common.getProjectCache().get(change.getDest().getParentKey());
    if (cacheEntry == null) {
      return;
    }
    try {
      // Try to mark interested owners with a TO and not a BCC line.
      //
      final Set<Account.Id> toNotBCC = new HashSet<Account.Id>();
      for (AccountGroupMember m : db.accountGroupMembers().byGroup(
          cacheEntry.getProject().getOwnerGroupId())) {
        toNotBCC.add(m.getAccountId());
      }

      // BCC anyone who has interest in this project's changes
      //
      for (AccountProjectWatch w : db.accountProjectWatches().notifyNewChanges(
          cacheEntry.getProject().getId())) {
        if (toNotBCC.contains(w.getAccountId())) {
          add(RecipientType.TO, w.getAccountId());
        } else {
          add(RecipientType.BCC, w.getAccountId());
        }
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
    }
  }

  private void newChangeCc() throws MessagingException {
    // CC the owner/uploader, but in truth these should always match
    // the sender too. add will strip duplicates (if any).
    //
    add(RecipientType.CC, change.getOwner());
    if (patchSet != null) {
      add(RecipientType.CC, patchSet.getUploader());
    }
    ccSender();
  }

  private void commentTo() throws MessagingException {
    // Always to the owner/uploader/author/committer. These people
    // have a vested interest in the change and any remarks made.
    //
    add(RecipientType.TO, change.getOwner());
    if (patchSet != null) {
      add(RecipientType.TO, patchSet.getUploader());
    }
    if (patchSetInfo != null) {
      add(RecipientType.TO, patchSetInfo.getAuthor());
      add(RecipientType.TO, patchSetInfo.getCommitter());
    }
    add(RecipientType.CC, reviewers);
    add(RecipientType.CC, extraCC);

    if (db == null) {
      // We need a database handle to fetch the interest list.
      //
      return;
    }

    try {
      // CC anyone else who has posted an approval mark on this change
      //
      for (ChangeApproval ap : db.changeApprovals().byChange(change.getId())) {
        add(RecipientType.CC, ap.getAccountId());
      }

      // BCC anyone else who has interest in this project's changes
      //
      final Project.Id projectId = projectId();
      if (projectId != null) {
        for (AccountProjectWatch w : db.accountProjectWatches()
            .notifyAllComments(projectId)) {
          add(RecipientType.BCC, w.getAccountId());
        }
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
    }
  }

  private void submittedTo() throws MessagingException {
    // Always to the owner/uploader/author/committer. These people
    // have a vested interest in the change.
    //
    add(RecipientType.TO, change.getOwner());
    if (patchSet != null) {
      add(RecipientType.TO, patchSet.getUploader());
    }
    if (patchSetInfo != null) {
      add(RecipientType.TO, patchSetInfo.getAuthor());
      add(RecipientType.TO, patchSetInfo.getCommitter());
    }
    add(RecipientType.CC, reviewers);
    add(RecipientType.CC, extraCC);

    if (db == null) {
      // We need a database handle to fetch the interest list.
      //
      return;
    }

    try {
      // CC anyone else who has posted an approval mark on this change
      //
      for (ChangeApproval ap : db.changeApprovals().byChange(change.getId())) {
        add(RecipientType.CC, ap.getAccountId());
      }

      // BCC anyone else who has interest in this project's changes
      //
      final Project.Id projectId = projectId();
      if (projectId != null) {
        for (AccountProjectWatch w : db.accountProjectWatches()
            .notifySubmittedChanges(projectId)) {
          add(RecipientType.BCC, w.getAccountId());
        }
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
    }
  }

  private void starredTo() throws MessagingException {
    try {
      // BCC anyone else who has starred this change.
      //
      for (StarredChange w : db.starredChanges().byChange(change.getId())) {
        add(RecipientType.BCC, w.getAccountId());
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
    }
  }

  private void begin(final String messageClass) throws MessagingException {
    msg = new MimeMessage(transport);
    if (message != null && message.getWrittenOn() != null) {
      msg.setSentDate(new Date(message.getWrittenOn().getTime()));
    } else {
      msg.setSentDate(new Date());
    }
    initFrom();
    initUserAgent();
    initListId();
    initChangeUrl();
    initChangeId();
    initCommitId();
    initMessageType(messageClass);
    initSubject();
    body = new StringBuilder();
    inFooter = false;
  }

  private void initFrom() throws MessagingException, AddressException {
    Address addr;
    if (fromId != null) {
      addr = toAddress(fromId);
    } else {
      final PersonIdent pi = server.newGerritPersonIdent();
      try {
        addr = new InternetAddress(pi.getName(), pi.getEmailAddress());
      } catch (UnsupportedEncodingException e) {
        addr = new InternetAddress(pi.getEmailAddress());
      }
    }
    msg.setFrom(addr);
  }

  private void initUserAgent() throws MessagingException {
    msg.setHeader("User-Agent", "Gerrit/2");
  }

  private void initListId() throws MessagingException {
    // Set a reasonable list id so that filters can be used to sort messages
    //
    final StringBuilder listid = new StringBuilder();
    listid.append("gerrit-");
    listid.append(projectName.replace('/', '-'));
    listid.append("@");
    listid.append(gerritHost());

    final String listidStr = listid.toString();
    msg.setHeader("Mailing-List", "list " + listidStr);
    msg.setHeader("List-Id", "<" + listidStr.replace('@', '.') + ">");
    if (settingsUrl() != null) {
      msg.setHeader("List-Unsubscribe", "<" + settingsUrl() + ">");
    }
  }

  private void initChangeUrl() throws MessagingException {
    final String u = changeUrl();
    if (u != null) {
      msg.setHeader("X-Gerrit-ChangeURL", "<" + u + ">");
    }
  }

  private void initChangeId() throws MessagingException {
    msg.setHeader("X-Gerrit-ChangeId", "" + change.getChangeId());
  }

  private void initCommitId() throws MessagingException {
    if (patchSet != null && patchSet.getRevision() != null
        && patchSet.getRevision().get() != null
        && patchSet.getRevision().get().length() > 0) {
      msg.setHeader("X-Gerrit-Commit", patchSet.getRevision().get());
    }
  }

  private void initMessageType(final String messageClass)
      throws MessagingException {
    msg.setHeader("X-Gerrit-MessageType", messageClass);
  }

  private void initInReplyToChange() throws MessagingException {
    final String id = changeMessageThreadId();
    msg.setHeader("In-Reply-To", id);
    msg.setHeader("References", id);
  }

  private void initSubject() throws MessagingException {
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

  private String gerritHost() {
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

  private String changeUrl() {
    if (gerritUrl() != null) {
      final StringBuilder r = new StringBuilder();
      r.append(gerritUrl());
      r.append(change.getChangeId());
      return r.toString();
    }
    return null;
  }

  private String settingsUrl() {
    if (gerritUrl() != null) {
      final StringBuilder r = new StringBuilder();
      r.append(gerritUrl());
      r.append("settings");
      return r.toString();
    }
    return null;
  }

  private String gerritUrl() {
    if (server.getCanonicalURL() != null) {
      return server.getCanonicalURL();
    }
    return myUrl;
  }

  private String changeMessageThreadId() {
    final StringBuilder r = new StringBuilder();
    r.append('<');
    r.append("gerrit");
    r.append('.');
    r.append(change.getCreatedOn().getTime());
    r.append('.');
    r.append(change.getChangeId());
    r.append('@');
    r.append(gerritHost());
    r.append('>');
    return r.toString();
  }

  private void openFooter() {
    if (!inFooter) {
      inFooter = true;
      body.append("-- \n");
    }
  }

  private void send() throws MessagingException {
    if (haveRcptTo()) {
      ccSender();
      if (settingsUrl() != null) {
        openFooter();
        body.append("To unsubscribe, visit ");
        body.append(settingsUrl());
        body.append("\n");
      }
      msg.setText(body.toString(), "UTF-8");
      Transport.send(msg);
    }
  }

  private boolean haveRcptTo() {
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

  private Project.Id projectId() {
    final ProjectCache.Entry r;

    r = Common.getProjectCache().get(change.getDest().getParentKey());
    return r != null ? r.getProject().getId() : null;
  }

  private void ccSender() throws MessagingException {
    if (fromId != null) {
      // If we are impersonating a user, make sure they receive a CC of
      // this message so they can always review and audit what we sent
      // on their behalf to others.
      //
      add(RecipientType.CC, fromId);
    }
  }

  private void add(final RecipientType rt, final Collection<Account.Id> list)
      throws MessagingException {
    for (final Account.Id id : list) {
      add(rt, id);
    }
  }

  private void add(final RecipientType rt, final UserIdentity who)
      throws MessagingException {
    if (who != null && who.getAccount() != null) {
      add(rt, who.getAccount());
    }
  }

  private void add(final RecipientType rt, final Account.Id to)
      throws MessagingException {
    if (!rcptTo.add(to)) {
      return;
    }

    final Address addr = toAddress(to);
    if (addr != null) {
      msg.addRecipient(rt, addr);
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
