// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.StarredChange;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Sends an email to one or more interested parties. */
public abstract class ChangeEmail extends OutgoingEmail {
  protected final Change change;
  protected String projectName;
  protected PatchSet patchSet;
  protected PatchSetInfo patchSetInfo;
  protected ChangeMessage changeMessage;

  private ProjectState projectState;
  protected ChangeData changeData;

  protected ChangeEmail(EmailArguments ea, final Change c, final String mc) {
    super(ea, mc);
    change = c;
    changeData = change != null ? new ChangeData(change) : null;
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

  /** Format the message body by calling {@link #appendText(String)}. */
  protected void format() throws EmailException {
    formatChange();
    appendText(velocifyFile("ChangeFooter.vm"));
    try {
      HashSet<Account.Id> reviewers = new HashSet<Account.Id>();
      for (PatchSetApproval p : args.db.get().patchSetApprovals().byChange(
          change.getId())) {
        reviewers.add(p.getAccountId());
      }

      TreeSet<String> names = new TreeSet<String>();
      for (Account.Id who : reviewers) {
        names.add(getNameEmailFor(who));
      }

      for (String name : names) {
        appendText("Gerrit-Reviewer: " + name + "\n");
      }
    } catch (OrmException e) {
    }
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected abstract void formatChange() throws EmailException;

  /** Setup the message headers and envelope (TO, CC, BCC). */
  protected void init() {
    if (args.projectCache != null) {
      projectState = args.projectCache.get(change.getProject());
      projectName =
          projectState != null ? projectState.getProject().getName() : null;
    } else {
      projectState = null;
      projectName = null;
    }

    if (patchSet == null) {
      try {
        patchSet = args.db.get().patchSets().get(change.currentPatchSetId());
      } catch (OrmException err) {
        patchSet = null;
      }
    }

    if (patchSet != null && patchSetInfo == null) {
      try {
        patchSetInfo = args.patchSetInfoFactory.get(patchSet.getId());
      } catch (PatchSetInfoNotAvailableException err) {
        patchSetInfo = null;
      }
    }

    super.init();

    if (changeMessage != null && changeMessage.getWrittenOn() != null) {
      setHeader("Date", new Date(changeMessage.getWrittenOn().getTime()));
    }
    setChangeSubjectHeader();
    setHeader("X-Gerrit-Change-Id", "" + change.getKey().get());
    setListIdHeader();
    setChangeUrlHeader();
    setCommitIdHeader();
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
    subj.append(change.getKey().abbreviate());
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

  protected String getChangeMessageThreadId() {
    final StringBuilder r = new StringBuilder();
    r.append('<');
    r.append("gerrit");
    r.append('.');
    r.append(change.getCreatedOn().getTime());
    r.append('.');
    r.append(change.getKey().get());
    r.append('@');
    r.append(getGerritHost());
    r.append('>');
    return r.toString();
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

    if (patchSet != null) {
      appendText("---\n");
      for (PatchListEntry p : getPatchList().getPatches()) {
        appendText(p.getChangeType().getCode() + " " + p.getNewName() + "\n");
      }
      appendText("\n");
    }
  }

  /** Get the patch list corresponding to this patch set. */
  protected PatchList getPatchList() {
    if (patchSet != null) {
      return args.patchListCache.get(change, patchSet);
    }
    return null;
  }

  /** Get the project entity the change is in; null if its been deleted. */
  protected ProjectState getProjectState() {
    return projectState;
  }

  /** Get the groups which own the project. */
  protected Set<AccountGroup.Id> getProjectOwners() {
    final ProjectState r;

    r = args.projectCache.get(change.getProject());
    return r != null ? r.getOwners() : Collections.<AccountGroup.Id> emptySet();
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

  /** BCC any user who has starred this change. */
  protected void bccStarredBy() {
    try {
      // BCC anyone who has starred this change.
      //
      for (StarredChange w : args.db.get().starredChanges().byChange(
          change.getId())) {
        add(RecipientType.BCC, w.getAccountId());
      }
    } catch (OrmException err) {
      // Just don't BCC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
    }
  }

  /** BCC any user who has set "notify all comments" on this project. */
  protected void bccWatchesNotifyAllComments() {
    try {
      // BCC anyone else who has interest in this project's changes
      //
      for (final AccountProjectWatch w : getWatches()) {
        if (w.isNotifyAllComments()) {
          add(RecipientType.BCC, w.getAccountId());
        }
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
    }
  }

  /** Returns all watches that are relevant */
  protected final List<AccountProjectWatch> getWatches() throws OrmException {
    if (changeData == null) {
      return Collections.emptyList();
    }

    List<AccountProjectWatch> matching = new ArrayList<AccountProjectWatch>();
    Set<Account.Id> projectWatchers = new HashSet<Account.Id>();

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(change.getProject())) {
      projectWatchers.add(w.getAccountId());
      add(matching, w);
    }

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(args.wildProject)) {
      if (!projectWatchers.contains(w.getAccountId())) {
        add(matching, w);
      }
    }

    return Collections.unmodifiableList(matching);
  }

  @SuppressWarnings("unchecked")
  private void add(List<AccountProjectWatch> matching, AccountProjectWatch w)
      throws OrmException {
    IdentifiedUser user =
        args.identifiedUserFactory.create(args.db, w.getAccountId());
    ChangeQueryBuilder qb = args.queryBuilder.create(user);
    Predicate<ChangeData> p = qb.is_visible();
    if (w.getFilter() != null) {
      try {
        qb.setAllowFile(true);
        p = Predicate.and(qb.parse(w.getFilter()), p);
        p = args.queryRewriter.get().rewrite(p);
        if (p.match(changeData)) {
          matching.add(w);
        }
      } catch (QueryParseException e) {
        // Ignore broken filter expressions.
      }
    } else if (p.match(changeData)) {
      matching.add(w);
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
    try {
      // CC anyone else who has posted an approval mark on this change
      //
      for (PatchSetApproval ap : args.db.get().patchSetApprovals().byChange(
          change.getId())) {
        if (!includeZero && ap.getValue() == 0) {
          continue;
        }
        add(RecipientType.CC, ap.getAccountId());
      }
    } catch (OrmException err) {
    }
  }

  protected boolean isVisibleTo(final Account.Id to) {
    return projectState == null
        || change == null
        || projectState.controlFor(args.identifiedUserFactory.create(to))
            .controlFor(change).isVisible();
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("change", change);
    velocityContext.put("branch", change.getDest());
    velocityContext.put("projectName", projectName);
    velocityContext.put("patchSet", patchSet);
    velocityContext.put("patchSetInfo", patchSetInfo);
  }
}
