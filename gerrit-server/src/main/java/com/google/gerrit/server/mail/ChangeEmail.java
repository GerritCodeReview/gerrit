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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupInclude;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.SingleGroupUser;
import com.google.gwtorm.server.OrmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

/** Sends an email to one or more interested parties. */
public abstract class ChangeEmail extends OutgoingEmail {
  private static final Logger log = LoggerFactory.getLogger(ChangeEmail.class);

  protected final Change change;
  protected PatchSet patchSet;
  protected PatchSetInfo patchSetInfo;
  protected ChangeMessage changeMessage;

  protected ProjectState projectState;
  protected ChangeData changeData;
  protected Set<Account.Id> authors;
  protected boolean emailOnlyAuthors;

  protected ChangeEmail(EmailArguments ea, final String anonymousCowardName,
      final Change c, final String mc) {
    super(ea, anonymousCowardName, mc);
    change = c;
    changeData = change != null ? new ChangeData(change) : null;
    emailOnlyAuthors = false;
  }

  public void setFrom(final Account.Id id) {
    super.setFrom(id);

    /** Is the from user in an email squelching group? */
    final IdentifiedUser user =  args.identifiedUserFactory.create(id);
    emailOnlyAuthors = !user.getCapabilities().canEmailReviewers();
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
  protected void init() throws EmailException {
    if (args.projectCache != null) {
      projectState = args.projectCache.get(change.getProject());
    } else {
      projectState = null;
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
        patchSetInfo = args.patchSetInfoFactory.get(args.db.get(), patchSet.getId());
      } catch (PatchSetInfoNotAvailableException err) {
        patchSetInfo = null;
      }
    }
    authors = getAuthors();

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

  private void setListIdHeader() throws EmailException {
    // Set a reasonable list id so that filters can be used to sort messages
    setVHeader("Mailing-List", "list $email.listId");
    setVHeader("List-Id", "<$email.listId.replace('@', '.')>");
    if (getSettingsUrl() != null) {
      setVHeader("List-Unsubscribe", "<$email.settingsUrl>");
    }
  }

  public String getListId() throws EmailException {
    return velocify("gerrit-$projectName.replace('/', '-')@$email.gerritHost");
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

  private void setChangeSubjectHeader() throws EmailException {
    setHeader("Subject", velocifyFile("ChangeSubject.vm"));
  }

  /** Get a link to the change; null if the server doesn't know its own address. */
  public String getChangeUrl() {
    if (change != null && getGerritUrl() != null) {
      final StringBuilder r = new StringBuilder();
      r.append(getGerritUrl());
      r.append(change.getChangeId());
      return r.toString();
    }
    return null;
  }

  public String getChangeMessageThreadId() throws EmailException {
    return velocify("<gerrit.${change.createdOn.time}.$change.key.get()" +
                    "@$email.gerritHost>");
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
  public String getCoverLetter() {
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
    appendText(getChangeDetail());
  }

  /** Create the change message and the affected file list. */
  public String getChangeDetail() {
    try {
      StringBuilder detail = new StringBuilder();

      if (patchSetInfo != null) {
        detail.append(patchSetInfo.getMessage().trim() + "\n");
      } else {
        detail.append(change.getSubject().trim() + "\n");
      }

      if (patchSet != null) {
        detail.append("---\n");
        PatchList patchList = getPatchList();
        for (PatchListEntry p : patchList.getPatches()) {
          if (Patch.COMMIT_MSG.equals(p.getNewName())) {
            continue;
          }
          detail.append(p.getChangeType().getCode() + " " + p.getNewName() + "\n");
        }
        detail.append(MessageFormat.format("" //
            + "{0,choice,0#0 files|1#1 file|1<{0} files} changed, " //
            + "{1,choice,0#0 insertions|1#1 insertion|1<{1} insertions}(+), " //
            + "{2,choice,0#0 deletions|1#1 deletion|1<{2} deletions}(-)" //
            + "\n", patchList.getPatches().size() - 1, //
            patchList.getInsertions(), //
            patchList.getDeletions()));
        detail.append("\n");
      }
      return detail.toString();
    } catch (Exception err) {
      log.warn("Cannot format change detail", err);
      return "";
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
  protected Set<AccountGroup.UUID> getProjectOwners() {
    final ProjectState r;

    r = args.projectCache.get(change.getProject());
    return r != null ? r.getOwners() : Collections.<AccountGroup.UUID> emptySet();
  }

  /** TO or CC all vested parties (change owner, patch set uploader, author). */
  protected void rcptToAuthors(final RecipientType rt) {
    for (final Account.Id id : authors) {
      add(rt, id);
    }
  }

  /** BCC any user who has starred this change. */
  protected void bccStarredBy() {
    try {
      // BCC anyone who has starred this change.
      //
      for (StarredChange w : args.db.get().starredChanges().byChange(
          change.getId())) {
        super.add(RecipientType.BCC, w.getAccountId());
      }
    } catch (OrmException err) {
      // Just don't BCC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
      log.warn("Cannot BCC users that starred updated change", err);
    }
  }

  /** BCC users and groups that want notification of events. */
  protected void bccWatches(NotifyType type) {
    try {
      Watchers matching = getWatches(type);
      for (Account.Id user : matching.accounts) {
        add(RecipientType.BCC, user);
      }
      for (Address addr : matching.emails) {
        add(RecipientType.BCC, addr);
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
      log.warn("Cannot BCC watchers for " + type, err);
    }
  }

  /** Returns all watches that are relevant */
  protected final Watchers getWatches(NotifyType type) throws OrmException {
    Watchers matching = new Watchers();
    if (changeData == null) {
      return matching;
    }

    Set<Account.Id> projectWatchers = new HashSet<Account.Id>();

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(change.getProject())) {
      projectWatchers.add(w.getAccountId());
      if (w.isNotify(type)) {
        add(matching, w);
      }
    }

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(args.allProjectsName)) {
      if (!projectWatchers.contains(w.getAccountId()) && w.isNotify(type)) {
        add(matching, w);
      }
    }

    ProjectState state = projectState;
    while (state != null) {
      for (NotifyConfig nc : state.getConfig().getNotifyConfigs()) {
        if (nc.isNotify(type)) {
          try {
            add(matching, nc, state.getProject().getNameKey());
          } catch (QueryParseException e) {
            log.warn(String.format(
                "Project %s has invalid notify %s filter \"%s\": %s",
                state.getProject().getName(), nc.getName(),
                nc.getFilter(), e.getMessage()));
          }
        }
      }
      state = state.getParentState();
    }

    return matching;
  }

  protected static class Watchers {
    protected final Set<Account.Id> accounts = Sets.newHashSet();
    protected final Set<Address> emails = Sets.newHashSet();
  }

  @SuppressWarnings("unchecked")
  private void add(Watchers matching, NotifyConfig nc, Project.NameKey project)
      throws OrmException, QueryParseException {
    for (GroupReference ref : nc.getGroups()) {
      AccountGroup group = args.groupCache.get(ref.getUUID());
      if (group == null) {
        log.warn(String.format(
            "Project %s has invalid group %s in notify section %s",
            project.get(), ref.getName(), nc.getName()));
        continue;
      }

      if (group.getType() != AccountGroup.Type.INTERNAL) {
        log.warn(String.format(
            "Project %s cannot use group %s of type %s in notify section %s",
            project.get(), ref.getName(), group.getType(), nc.getName()));
        continue;
      }

      ChangeQueryBuilder qb = args.queryBuilder.create(new SingleGroupUser(
          args.capabilityControlFactory,
          ref.getUUID()));
      qb.setAllowFile(true);
      Predicate<ChangeData> p = qb.is_visible();
      if (nc.getFilter() != null) {
        p = Predicate.and(qb.parse(nc.getFilter()), p);
        p = args.queryRewriter.get().rewrite(p);
      }
      if (p.match(changeData)) {
        recursivelyAddAllAccounts(matching, group);
      }
    }

    if (!nc.getAddresses().isEmpty()) {
      if (nc.getFilter() != null) {
        ChangeQueryBuilder qb = args.queryBuilder.create(args.anonymousUser);
        qb.setAllowFile(true);
        Predicate<ChangeData> p = qb.parse(nc.getFilter());
        p = args.queryRewriter.get().rewrite(p);
        if (p.match(changeData)) {
          matching.emails.addAll(nc.getAddresses());
        }
      } else {
        matching.emails.addAll(nc.getAddresses());
      }
    }
  }

  private void recursivelyAddAllAccounts(Watchers matching, AccountGroup group)
      throws OrmException {
    Set<AccountGroup.Id> seen = Sets.newHashSet();
    Queue<AccountGroup.Id> scan = Lists.newLinkedList();
    scan.add(group.getId());
    seen.add(group.getId());
    while (!scan.isEmpty()) {
      AccountGroup.Id next = scan.remove();
      for (AccountGroupMember m : args.db.get().accountGroupMembers()
          .byGroup(next)) {
        matching.accounts.add(m.getAccountId());
      }
      for (AccountGroupInclude m : args.db.get().accountGroupIncludes()
          .byGroup(next)) {
        if (seen.add(m.getIncludeId())) {
          scan.add(m.getIncludeId());
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void add(Watchers matching, AccountProjectWatch w)
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
          matching.accounts.add(w.getAccountId());
        }
      } catch (QueryParseException e) {
        // Ignore broken filter expressions.
      }
    } else if (p.match(changeData)) {
      matching.accounts.add(w.getAccountId());
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

  protected void add(final RecipientType rt, final Account.Id to) {
    if (! emailOnlyAuthors || authors.contains(to)) {
      super.add(rt, to);
    }
  }

  protected boolean isVisibleTo(final Account.Id to) throws OrmException {
    return projectState == null
        || change == null
        || projectState.controlFor(args.identifiedUserFactory.create(to))
            .controlFor(change).isVisible(args.db.get());
  }

  /** Find all users who are authors of any part of this change. */
  protected Set<Account.Id> getAuthors() {
    Set<Account.Id> authors = new HashSet<Account.Id>();

    authors.add(change.getOwner());
    if (patchSet != null) {
      authors.add(patchSet.getUploader());
    }
    if (patchSetInfo != null) {
      if (patchSetInfo.getAuthor().getAccount() != null) {
        authors.add(patchSetInfo.getAuthor().getAccount());
      }
      if (patchSetInfo.getCommitter().getAccount() != null) {
        authors.add(patchSetInfo.getCommitter().getAccount());
      }
    }
    return authors;
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("change", change);
    velocityContext.put("changeId", change.getKey());
    velocityContext.put("coverLetter", getCoverLetter());
    velocityContext.put("branch", change.getDest());
    velocityContext.put("fromName", getNameFor(fromId));
    velocityContext.put("projectName", //
        projectState != null ? projectState.getProject().getName() : null);
    velocityContext.put("patchSet", patchSet);
    velocityContext.put("patchSetInfo", patchSetInfo);
  }
}
