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

import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;

import com.google.common.collect.Multimap;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.ProjectWatch.Watchers;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Sends an email to one or more interested parties. */
public abstract class ChangeEmail extends NotificationEmail {
  private static final Logger log = LoggerFactory.getLogger(ChangeEmail.class);

  protected static ChangeData newChangeData(EmailArguments ea,
      Project.NameKey project, Change.Id id) {
    return ea.changeDataFactory.create(ea.db.get(), project, id);
  }

  protected final Change change;
  protected final ChangeData changeData;
  protected PatchSet patchSet;
  protected PatchSetInfo patchSetInfo;
  protected String changeMessage;
  protected Timestamp timestamp;

  protected ProjectState projectState;
  protected Set<Account.Id> authors;
  protected boolean emailOnlyAuthors;

  protected ChangeEmail(EmailArguments ea, String mc, ChangeData cd)
      throws OrmException {
    super(ea, mc, cd.change().getDest());
    changeData = cd;
    change = cd.change();
    emailOnlyAuthors = false;
  }

  @Override
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

  @Deprecated
  public void setChangeMessage(final ChangeMessage cm) {
    setChangeMessage(cm.getMessage(), cm.getWrittenOn());
  }

  public void setChangeMessage(String cm, Timestamp t) {
    changeMessage = cm;
    timestamp = t;
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  @Override
  protected void format() throws EmailException {
    formatChange();
    appendText(velocifyFile("ChangeFooter.vm"));
    try {
      TreeSet<String> names = new TreeSet<>();
      for (Account.Id who : changeData.reviewers().all()) {
        names.add(getNameEmailFor(who));
      }
      for (String name : names) {
        appendText("Gerrit-Reviewer: " + name + "\n");
      }
    } catch (OrmException e) {
      log.warn("Cannot get change reviewers", e);
    }
    formatFooter();
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected abstract void formatChange() throws EmailException;

  /**
   * Format the message footer by calling {@link #appendText(String)}.
   *
   * @throws EmailException if an error occurred.
   */
  protected void formatFooter() throws EmailException {
  }

  /** Setup the message headers and envelope (TO, CC, BCC). */
  @Override
  protected void init() throws EmailException {
    if (args.projectCache != null) {
      projectState = args.projectCache.get(change.getProject());
    } else {
      projectState = null;
    }

    if (patchSet == null) {
      try {
        patchSet = changeData.currentPatchSet();
      } catch (OrmException err) {
        patchSet = null;
      }
    }

    if (patchSet != null && patchSetInfo == null) {
      try {
        patchSetInfo = args.patchSetInfoFactory.get(
            args.db.get(), changeData.notes(), patchSet.getId());
      } catch (PatchSetInfoNotAvailableException | OrmException err) {
        patchSetInfo = null;
      }
    }
    authors = getAuthors();

    super.init();
    if (timestamp != null) {
      setHeader("Date", new Date(timestamp.getTime()));
    }
    setChangeSubjectHeader();
    setHeader("X-Gerrit-Change-Id", "" + change.getKey().get());
    setChangeUrlHeader();
    setCommitIdHeader();
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
    if (getGerritUrl() != null) {
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

  /** Get the text of the "cover letter". */
  public String getCoverLetter() {
    if (changeMessage != null) {
      return changeMessage.trim();
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
        detail.append(patchSetInfo.getMessage().trim()).append("\n");
      } else {
        detail.append(change.getSubject().trim()).append("\n");
      }

      if (patchSet != null) {
        detail.append("---\n");
        PatchList patchList = getPatchList();
        for (PatchListEntry p : patchList.getPatches()) {
          if (Patch.COMMIT_MSG.equals(p.getNewName())) {
            continue;
          }
          detail.append(p.getChangeType().getCode())
                .append(" ").append(p.getNewName()).append("\n");
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
  protected PatchList getPatchList() throws PatchListNotAvailableException {
    if (patchSet != null) {
      return args.patchListCache.get(change, patchSet);
    }
    throw new PatchListNotAvailableException("no patchSet specified");
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
    if (!NotifyHandling.ALL.equals(notify)) {
      return;
    }

    try {
      // BCC anyone who has starred this change
      // and remove anyone who has ignored this change.
      //
      Multimap<Account.Id, String> stars =
          args.starredChangesUtil.byChangeFromIndex(change.getId());
      for (Map.Entry<Account.Id, Collection<String>> e :
          stars.asMap().entrySet()) {
        if (e.getValue().contains(StarredChangesUtil.DEFAULT_LABEL)) {
          super.add(RecipientType.BCC, e.getKey());
        }
        if (e.getValue().contains(StarredChangesUtil.IGNORE_LABEL)) {
          AccountState accountState = args.accountCache.get(e.getKey());
          if (accountState != null) {
            removeUser(accountState.getAccount());
          }
        }
      }
    } catch (OrmException | NoSuchChangeException err) {
      // Just don't BCC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
      log.warn("Cannot BCC users that starred updated change", err);
    }
  }

  @Override
  protected final Watchers getWatchers(NotifyType type) throws OrmException {
    if (!NotifyHandling.ALL.equals(notify)) {
      return new Watchers();
    }

    ProjectWatch watch = new ProjectWatch(
        args, branch.getParentKey(), projectState, changeData);
    return watch.getWatchers(type);
  }

  /** Any user who has published comments on this change. */
  protected void ccAllApprovals() {
    if (!NotifyHandling.ALL.equals(notify)
        && !NotifyHandling.OWNER_REVIEWERS.equals(notify)) {
      return;
    }

    try {
      for (Account.Id id : changeData.reviewers().all()) {
        add(RecipientType.CC, id);
      }
    } catch (OrmException err) {
      log.warn("Cannot CC users that reviewed updated change", err);
    }
  }

  /** Users who have non-zero approval codes on the change. */
  protected void ccExistingReviewers() {
    if (!NotifyHandling.ALL.equals(notify)
        && !NotifyHandling.OWNER_REVIEWERS.equals(notify)) {
      return;
    }

    try {
      for (Account.Id id : changeData.reviewers().byState(REVIEWER)) {
        add(RecipientType.CC, id);
      }
    } catch (OrmException err) {
      log.warn("Cannot CC users that commented on updated change", err);
    }
  }

  @Override
  protected void add(final RecipientType rt, final Account.Id to) {
    if (! emailOnlyAuthors || authors.contains(to)) {
      super.add(rt, to);
    }
  }

  @Override
  protected boolean isVisibleTo(final Account.Id to) throws OrmException {
    return projectState == null
        || projectState.controlFor(args.identifiedUserFactory.create(to))
            .controlFor(args.db.get(), change).isVisible(args.db.get());
  }

  /** Find all users who are authors of any part of this change. */
  protected Set<Account.Id> getAuthors() {
    Set<Account.Id> authors = new HashSet<>();

    switch (notify) {
      case NONE:
        break;
      case ALL:
      default:
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
        //$FALL-THROUGH$
      case OWNER_REVIEWERS:
      case OWNER:
        authors.add(change.getOwner());
        break;
    }

    return authors;
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("change", change);
    velocityContext.put("changeId", change.getKey());
    velocityContext.put("coverLetter", getCoverLetter());
    velocityContext.put("fromName", getNameFor(fromId));
    velocityContext.put("patchSet", patchSet);
    velocityContext.put("patchSetInfo", patchSetInfo);
  }

  public boolean getIncludeDiff() {
    return args.settings.includeDiff;
  }

  private static int HEAP_EST_SIZE = 32 * 1024;

  /** Show patch set as unified difference. */
  public String getUnifiedDiff() {
    PatchList patchList;
    try {
      patchList = getPatchList();
      if (patchList.getOldId() == null) {
        // Octopus merges are not well supported for diff output by Gerrit.
        // Currently these always have a null oldId in the PatchList.
        return "[Octopus merge; cannot be formatted as a diff.]\n";
      }
    } catch (PatchListNotAvailableException e) {
      log.error("Cannot format patch", e);
      return "";
    }

    int maxSize = args.settings.maximumDiffSize;
    TemporaryBuffer.Heap buf =
        new TemporaryBuffer.Heap(Math.min(HEAP_EST_SIZE, maxSize), maxSize);
    try (DiffFormatter fmt = new DiffFormatter(buf)) {
      try (Repository git = args.server.openRepository(change.getProject())) {
        try {
          fmt.setRepository(git);
          fmt.setDetectRenames(true);
          fmt.format(patchList.getOldId(), patchList.getNewId());
          return RawParseUtils.decode(buf.toByteArray());
        } catch (IOException e) {
          if (JGitText.get().inMemoryBufferLimitExceeded.equals(e.getMessage())) {
            return "";
          }
          log.error("Cannot format patch", e);
          return "";
        }
      } catch (IOException e) {
        log.error("Cannot open repository to format patch", e);
        return "";
      }
    }
  }
}
