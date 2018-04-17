// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.common.base.Splitter;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.mail.MailHeader;
import com.google.gerrit.server.mail.send.ProjectWatch.Watchers;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchListObjectTooLargeException;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.james.mime4j.dom.field.FieldName;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends an email to one or more interested parties. */
public abstract class ChangeEmail extends NotificationEmail {
  private static final Logger log = LoggerFactory.getLogger(ChangeEmail.class);

  protected static ChangeData newChangeData(
      EmailArguments ea, Project.NameKey project, Change.Id id) {
    return ea.changeDataFactory.create(ea.db.get(), project, id);
  }

  protected final Change change;
  protected final ChangeData changeData;
  protected ListMultimap<Account.Id, String> stars;
  protected PatchSet patchSet;
  protected PatchSetInfo patchSetInfo;
  protected String changeMessage;
  protected Timestamp timestamp;

  protected ProjectAccessor projectAccessor;
  protected Set<Account.Id> authors;
  protected boolean emailOnlyAuthors;

  protected ChangeEmail(EmailArguments ea, String mc, ChangeData cd) throws OrmException {
    super(ea, mc, cd.change().getDest());
    changeData = cd;
    change = cd.change();
    emailOnlyAuthors = false;
  }

  @Override
  public void setFrom(Account.Id id) {
    super.setFrom(id);

    /** Is the from user in an email squelching group? */
    try {
      IdentifiedUser user = args.identifiedUserFactory.create(id);
      args.permissionBackend.user(user).check(GlobalPermission.EMAIL_REVIEWERS);
    } catch (AuthException | PermissionBackendException e) {
      emailOnlyAuthors = true;
    }
  }

  public void setPatchSet(PatchSet ps) {
    patchSet = ps;
  }

  public void setPatchSet(PatchSet ps, PatchSetInfo psi) {
    patchSet = ps;
    patchSetInfo = psi;
  }

  public void setChangeMessage(String cm, Timestamp t) {
    changeMessage = cm;
    timestamp = t;
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  @Override
  protected void format() throws EmailException {
    formatChange();
    appendText(textTemplate("ChangeFooter"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("ChangeFooterHtml"));
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
  protected void formatFooter() throws EmailException {}

  /** Setup the message headers and envelope (TO, CC, BCC). */
  @Override
  protected void init() throws EmailException {
    if (args.projectAccessorFactory != null) {
      try {
        projectAccessor = args.projectAccessorFactory.create(change.getProject());
      } catch (NoSuchProjectException | IOException e) {
        // TODO(dborowitz): Ignoring exception to avoid behavior change only.
        projectAccessor = null;
      }
    } else {
      projectAccessor = null;
    }

    if (patchSet == null) {
      try {
        patchSet = changeData.currentPatchSet();
      } catch (OrmException err) {
        patchSet = null;
      }
    }

    if (patchSet != null) {
      setHeader(MailHeader.PATCH_SET.fieldName(), patchSet.getPatchSetId() + "");
      if (patchSetInfo == null) {
        try {
          patchSetInfo =
              args.patchSetInfoFactory.get(args.db.get(), changeData.notes(), patchSet.getId());
        } catch (PatchSetInfoNotAvailableException | OrmException err) {
          patchSetInfo = null;
        }
      }
    }
    authors = getAuthors();

    try {
      stars = changeData.stars();
    } catch (OrmException e) {
      throw new EmailException("Failed to load stars for change " + change.getChangeId(), e);
    }

    super.init();
    if (timestamp != null) {
      setHeader(FieldName.DATE, new Date(timestamp.getTime()));
    }
    setChangeSubjectHeader();
    setHeader(MailHeader.CHANGE_ID.fieldName(), "" + change.getKey().get());
    setHeader(MailHeader.CHANGE_NUMBER.fieldName(), "" + change.getChangeId());
    setChangeUrlHeader();
    setCommitIdHeader();

    if (notify.ordinal() >= NotifyHandling.OWNER_REVIEWERS.ordinal()) {
      try {
        addByEmail(
            RecipientType.CC, changeData.reviewersByEmail().byState(ReviewerStateInternal.CC));
        addByEmail(
            RecipientType.CC,
            changeData.reviewersByEmail().byState(ReviewerStateInternal.REVIEWER));
      } catch (OrmException e) {
        throw new EmailException("Failed to add unregistered CCs " + change.getChangeId(), e);
      }
    }
  }

  private void setChangeUrlHeader() {
    final String u = getChangeUrl();
    if (u != null) {
      setHeader(MailHeader.CHANGE_URL.fieldName(), "<" + u + ">");
    }
  }

  private void setCommitIdHeader() {
    if (patchSet != null
        && patchSet.getRevision() != null
        && patchSet.getRevision().get() != null
        && patchSet.getRevision().get().length() > 0) {
      setHeader(MailHeader.COMMIT.fieldName(), patchSet.getRevision().get());
    }
  }

  private void setChangeSubjectHeader() {
    setHeader(FieldName.SUBJECT, textTemplate("ChangeSubject"));
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

  public String getChangeMessageThreadId() {
    return "<gerrit."
        + change.getCreatedOn().getTime()
        + "."
        + change.getKey().get()
        + "@"
        + this.getGerritHost()
        + ">";
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
          if (Patch.isMagic(p.getNewName())) {
            continue;
          }
          detail
              .append(p.getChangeType().getCode())
              .append(" ")
              .append(p.getNewName())
              .append("\n");
        }
        detail.append(
            MessageFormat.format(
                "" //
                    + "{0,choice,0#0 files|1#1 file|1<{0} files} changed, " //
                    + "{1,choice,0#0 insertions|1#1 insertion|1<{1} insertions}(+), " //
                    + "{2,choice,0#0 deletions|1#1 deletion|1<{2} deletions}(-)" //
                    + "\n",
                patchList.getPatches().size() - 1, //
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
    return projectAccessor.getProjectState();
  }

  /** Get the groups which own the project. */
  protected Set<AccountGroup.UUID> getProjectOwners() {
    try {
      return args.projectAccessorFactory.create(change.getProject()).getOwners();
    } catch (NoSuchProjectException | IOException e) {
      // TODO(dborowitz): Ignoring exception for now to avoid behavior change, but not a good idea.
      return Collections.emptySet();
    }
  }

  /** TO or CC all vested parties (change owner, patch set uploader, author). */
  protected void rcptToAuthors(RecipientType rt) {
    for (Account.Id id : authors) {
      add(rt, id);
    }
  }

  /** BCC any user who has starred this change. */
  protected void bccStarredBy() {
    if (!NotifyHandling.ALL.equals(notify)) {
      return;
    }

    for (Map.Entry<Account.Id, Collection<String>> e : stars.asMap().entrySet()) {
      if (e.getValue().contains(StarredChangesUtil.DEFAULT_LABEL)) {
        super.add(RecipientType.BCC, e.getKey());
      }
    }
  }

  protected void removeUsersThatIgnoredTheChange() {
    for (Map.Entry<Account.Id, Collection<String>> e : stars.asMap().entrySet()) {
      if (e.getValue().contains(StarredChangesUtil.IGNORE_LABEL)) {
        args.accountCache.get(e.getKey()).ifPresent(a -> removeUser(a.getAccount()));
      }
    }
  }

  @Override
  protected final Watchers getWatchers(NotifyType type, boolean includeWatchersFromNotifyConfig)
      throws OrmException {
    if (!NotifyHandling.ALL.equals(notify)) {
      return new Watchers();
    }

    ProjectWatch watch =
        new ProjectWatch(args, branch.getParentKey(), getProjectState(), changeData);
    return watch.getWatchers(type, includeWatchersFromNotifyConfig);
  }

  /** Any user who has published comments on this change. */
  protected void ccAllApprovals() {
    if (!NotifyHandling.ALL.equals(notify) && !NotifyHandling.OWNER_REVIEWERS.equals(notify)) {
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
    if (!NotifyHandling.ALL.equals(notify) && !NotifyHandling.OWNER_REVIEWERS.equals(notify)) {
      return;
    }

    try {
      for (Account.Id id : changeData.reviewers().byState(ReviewerStateInternal.REVIEWER)) {
        add(RecipientType.CC, id);
      }
    } catch (OrmException err) {
      log.warn("Cannot CC users that commented on updated change", err);
    }
  }

  @Override
  protected void add(RecipientType rt, Account.Id to) {
    if (!emailOnlyAuthors || authors.contains(to)) {
      super.add(rt, to);
    }
  }

  @Override
  protected boolean isVisibleTo(Account.Id to) throws PermissionBackendException {
    return getProjectState().statePermitsRead()
        && args.permissionBackend
            .absentUser(to)
            .change(changeData)
            .database(args.db)
            .test(ChangePermission.READ);
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
        // $FALL-THROUGH$
      case OWNER_REVIEWERS:
      case OWNER:
        authors.add(change.getOwner());
        break;
    }

    return authors;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();

    soyContext.put("changeId", change.getKey().get());
    soyContext.put("coverLetter", getCoverLetter());
    soyContext.put("fromName", getNameFor(fromId));
    soyContext.put("fromEmail", getNameEmailFor(fromId));
    soyContext.put("diffLines", getDiffTemplateData());

    soyContextEmailData.put("unifiedDiff", getUnifiedDiff());
    soyContextEmailData.put("changeDetail", getChangeDetail());
    soyContextEmailData.put("changeUrl", getChangeUrl());
    soyContextEmailData.put("includeDiff", getIncludeDiff());

    Map<String, String> changeData = new HashMap<>();

    String subject = change.getSubject();
    String originalSubject = change.getOriginalSubject();
    changeData.put("subject", subject);
    changeData.put("originalSubject", originalSubject);
    changeData.put("shortSubject", shortenSubject(subject));
    changeData.put("shortOriginalSubject", shortenSubject(originalSubject));

    changeData.put("ownerName", getNameFor(change.getOwner()));
    changeData.put("ownerEmail", getNameEmailFor(change.getOwner()));
    changeData.put("changeNumber", Integer.toString(change.getChangeId()));
    soyContext.put("change", changeData);

    Map<String, Object> patchSetData = new HashMap<>();
    patchSetData.put("patchSetId", patchSet.getPatchSetId());
    patchSetData.put("refName", patchSet.getRefName());
    soyContext.put("patchSet", patchSetData);

    Map<String, Object> patchSetInfoData = new HashMap<>();
    patchSetInfoData.put("authorName", patchSetInfo.getAuthor().getName());
    patchSetInfoData.put("authorEmail", patchSetInfo.getAuthor().getEmail());
    soyContext.put("patchSetInfo", patchSetInfoData);

    footers.add(MailHeader.CHANGE_ID.withDelimiter() + change.getKey().get());
    footers.add(MailHeader.CHANGE_NUMBER.withDelimiter() + Integer.toString(change.getChangeId()));
    footers.add(MailHeader.PATCH_SET.withDelimiter() + patchSet.getPatchSetId());
    footers.add(MailHeader.OWNER.withDelimiter() + getNameEmailFor(change.getOwner()));
    if (change.getAssignee() != null) {
      footers.add(MailHeader.ASSIGNEE.withDelimiter() + getNameEmailFor(change.getAssignee()));
    }
    for (String reviewer : getEmailsByState(ReviewerStateInternal.REVIEWER)) {
      footers.add(MailHeader.REVIEWER.withDelimiter() + reviewer);
    }
    for (String reviewer : getEmailsByState(ReviewerStateInternal.CC)) {
      footers.add(MailHeader.CC.withDelimiter() + reviewer);
    }
  }

  /**
   * A shortened subject is the subject limited to 72 characters, with an ellipsis if it exceeds
   * that limit.
   */
  private static String shortenSubject(String subject) {
    if (subject.length() < 73) {
      return subject;
    }
    return subject.substring(0, 69) + "...";
  }

  private Set<String> getEmailsByState(ReviewerStateInternal state) {
    Set<String> reviewers = new TreeSet<>();
    try {
      for (Account.Id who : changeData.reviewers().byState(state)) {
        reviewers.add(getNameEmailFor(who));
      }
    } catch (OrmException e) {
      log.warn("Cannot get change reviewers", e);
    }
    return reviewers;
  }

  public boolean getIncludeDiff() {
    return args.settings.includeDiff;
  }

  private static final int HEAP_EST_SIZE = 32 * 1024;

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
    } catch (PatchListObjectTooLargeException e) {
      log.warn("Cannot format patch " + e.getMessage());
      return "";
    } catch (PatchListNotAvailableException e) {
      log.error("Cannot format patch", e);
      return "";
    }

    int maxSize = args.settings.maximumDiffSize;
    TemporaryBuffer.Heap buf = new TemporaryBuffer.Heap(Math.min(HEAP_EST_SIZE, maxSize), maxSize);
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

  /**
   * Generate a Soy list of maps representing each line of the unified diff. The line maps will have
   * a 'type' key which maps to one of 'common', 'add' or 'remove' and a 'text' key which maps to
   * the line's content.
   */
  private SoyListData getDiffTemplateData() {
    SoyListData result = new SoyListData();
    Splitter lineSplitter = Splitter.on(System.getProperty("line.separator"));
    for (String diffLine : lineSplitter.split(getUnifiedDiff())) {
      SoyMapData lineData = new SoyMapData();
      lineData.put("text", diffLine);

      // Skip empty lines and lines that look like diff headers.
      if (diffLine.isEmpty() || diffLine.startsWith("---") || diffLine.startsWith("+++")) {
        lineData.put("type", "common");
      } else {
        switch (diffLine.charAt(0)) {
          case '+':
            lineData.put("type", "add");
            break;
          case '-':
            lineData.put("type", "remove");
            break;
          default:
            lineData.put("type", "common");
            break;
        }
      }
      result.add(lineData);
    }
    return result;
  }
}
