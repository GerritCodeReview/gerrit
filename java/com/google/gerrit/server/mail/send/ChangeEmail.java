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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.util.AttentionSetUtil.additionsOnly;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeSizeBucket;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.mail.MailHeader;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.send.ProjectWatch.Watchers;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.FilePathAdapter;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.james.mime4j.dom.field.FieldName;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;

/** Sends an email to one or more interested parties. */
public abstract class ChangeEmail extends NotificationEmail {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected static ChangeData newChangeData(
      EmailArguments ea, Project.NameKey project, Change.Id id) {
    return ea.changeDataFactory.create(project, id);
  }

  protected static ChangeData newChangeData(
      EmailArguments ea, Project.NameKey project, Change.Id id, ObjectId metaId) {
    return ea.changeDataFactory.create(ea.changeNotesFactory.createChecked(project, id, metaId));
  }

  private final Set<Account.Id> currentAttentionSet;
  protected final Change change;
  protected final ChangeData changeData;
  protected ListMultimap<Account.Id, String> stars;
  protected PatchSet patchSet;
  protected PatchSetInfo patchSetInfo;
  protected String changeMessage;
  protected Instant timestamp;

  protected ProjectState projectState;
  protected Set<Account.Id> authors;
  protected boolean emailOnlyAuthors;
  protected boolean emailOnlyAttentionSetIfEnabled;

  protected ChangeEmail(EmailArguments args, String messageClass, ChangeData changeData) {
    super(args, messageClass, changeData.change().getDest());
    this.changeData = changeData;
    change = changeData.change();
    emailOnlyAuthors = false;
    emailOnlyAttentionSetIfEnabled = true;
    currentAttentionSet = getAttentionSet();
  }

  @Override
  public void setFrom(Account.Id id) {
    super.setFrom(id);

    // Is the from user in an email squelching group?
    try {
      args.permissionBackend.absentUser(id).check(GlobalPermission.EMAIL_REVIEWERS);
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

  public void setChangeMessage(String cm, Instant t) {
    changeMessage = cm;
    timestamp = t;
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  @Override
  protected void format() throws EmailException {
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("ChangeHeaderHtml"));
    }
    appendText(textTemplate("ChangeHeader"));
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
    if (args.projectCache != null) {
      projectState = args.projectCache.get(change.getProject()).orElse(null);
    } else {
      projectState = null;
    }

    if (patchSet == null) {
      try {
        patchSet = changeData.currentPatchSet();
      } catch (StorageException err) {
        patchSet = null;
      }
    }

    if (patchSet != null) {
      setHeader(MailHeader.PATCH_SET.fieldName(), patchSet.number() + "");
      if (patchSetInfo == null) {
        try {
          patchSetInfo = args.patchSetInfoFactory.get(changeData.notes(), patchSet.id());
        } catch (PatchSetInfoNotAvailableException | StorageException err) {
          patchSetInfo = null;
        }
      }
    }
    authors = getAuthors();

    try {
      stars = changeData.stars();
    } catch (StorageException e) {
      throw new EmailException("Failed to load stars for change " + change.getChangeId(), e);
    }

    super.init();
    if (timestamp != null) {
      setHeader(FieldName.DATE, timestamp);
    }
    setChangeSubjectHeader();
    setHeader(MailHeader.CHANGE_ID.fieldName(), "" + change.getKey().get());
    setHeader(MailHeader.CHANGE_NUMBER.fieldName(), "" + change.getChangeId());
    setHeader(MailHeader.PROJECT.fieldName(), "" + change.getProject());
    setChangeUrlHeader();
    setCommitIdHeader();

    if (notify.handling().compareTo(NotifyHandling.OWNER_REVIEWERS) >= 0) {
      try {
        addByEmail(
            RecipientType.CC, changeData.reviewersByEmail().byState(ReviewerStateInternal.CC));
        addByEmail(
            RecipientType.CC,
            changeData.reviewersByEmail().byState(ReviewerStateInternal.REVIEWER));
      } catch (StorageException e) {
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
    if (patchSet != null) {
      setHeader(MailHeader.COMMIT.fieldName(), patchSet.commitId().name());
    }
  }

  private void setChangeSubjectHeader() {
    setHeader(FieldName.SUBJECT, textTemplate("ChangeSubject"));
  }

  private int getInsertionsCount() {
    return listModifiedFiles().values().stream()
        .map(FileDiffOutput::insertions)
        .reduce(0, Integer::sum);
  }

  private int getDeletionsCount() {
    return listModifiedFiles().values().stream()
        .map(FileDiffOutput::deletions)
        .reduce(0, Integer::sum);
  }

  /** Get a link to the change; null if the server doesn't know its own address. */
  @Nullable
  public String getChangeUrl() {
    return args.urlFormatter
        .get()
        .getChangeViewUrl(change.getProject(), change.getId())
        .orElse(null);
  }

  public String getChangeMessageThreadId() {
    return "<gerrit."
        + change.getCreatedOn().toEpochMilli()
        + "."
        + change.getKey().get()
        + "@"
        + getGerritHost()
        + ">";
  }

  /** Get the text of the "cover letter". */
  public String getCoverLetter() {
    if (changeMessage != null) {
      return changeMessage.trim();
    }
    return "";
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
        // Sort files by name.
        TreeMap<String, FileDiffOutput> modifiedFiles = new TreeMap<>(listModifiedFiles());
        for (FileDiffOutput fileDiff : modifiedFiles.values()) {
          if (fileDiff.newPath().isPresent() && Patch.isMagic(fileDiff.newPath().get())) {
            continue;
          }
          detail
              .append(fileDiff.changeType().getCode())
              .append(" ")
              .append(
                  FilePathAdapter.getNewPath(
                      fileDiff.oldPath(), fileDiff.newPath(), fileDiff.changeType()))
              .append("\n");
        }
        detail.append(
            MessageFormat.format(
                "" //
                    + "{0,choice,0#0 files|1#1 file|1<{0} files} changed, " //
                    + "{1,choice,0#0 insertions|1#1 insertion|1<{1} insertions}(+), " //
                    + "{2,choice,0#0 deletions|1#1 deletion|1<{2} deletions}(-)" //
                    + "\n",
                modifiedFiles.size() - 1, //
                getInsertionsCount(), //
                getDeletionsCount()));
        detail.append("\n");
      }
      return detail.toString();
    } catch (Exception err) {
      logger.atWarning().withCause(err).log("Cannot format change detail");
      return "";
    }
  }

  /** Get the patch list corresponding to patch set patchSetId of this change. */
  protected Map<String, FileDiffOutput> listModifiedFiles(int patchSetId) {
    try {
      PatchSet ps;
      if (patchSetId == patchSet.number()) {
        ps = patchSet;
      } else {
        ps = args.patchSetUtil.get(changeData.notes(), PatchSet.id(change.getId(), patchSetId));
      }
      return args.diffOperations.listModifiedFilesAgainstParent(
          change.getProject(), ps.commitId(), /* parentNum= */ 0, DiffOptions.DEFAULTS);
    } catch (StorageException | DiffNotAvailableException e) {
      logger.atSevere().withCause(e).log("Failed to get modified files");
      return new HashMap<>();
    }
  }

  /** Get the patch list corresponding to this patch set. */
  protected Map<String, FileDiffOutput> listModifiedFiles() {
    if (patchSet != null) {
      try {
        return args.diffOperations.listModifiedFilesAgainstParent(
            change.getProject(), patchSet.commitId(), /* parentNum= */ 0, DiffOptions.DEFAULTS);
      } catch (DiffNotAvailableException e) {
        logger.atSevere().withCause(e).log("Failed to get modified files");
      }
    } else {
      logger.atSevere().log("no patchSet specified");
    }
    return new HashMap<>();
  }

  /** Get the project entity the change is in; null if its been deleted. */
  protected ProjectState getProjectState() {
    return projectState;
  }

  /** TO or CC all vested parties (change owner, patch set uploader, author). */
  protected void rcptToAuthors(RecipientType rt) {
    for (Account.Id id : authors) {
      add(rt, id);
    }
  }

  /** BCC any user who has starred this change. */
  protected void bccStarredBy() {
    if (!NotifyHandling.ALL.equals(notify.handling())) {
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
        args.accountCache.get(e.getKey()).ifPresent(a -> removeUser(a.account()));
      }
    }
  }

  @Override
  protected final Watchers getWatchers(NotifyType type, boolean includeWatchersFromNotifyConfig) {
    if (!NotifyHandling.ALL.equals(notify.handling())) {
      return new Watchers();
    }

    ProjectWatch watch = new ProjectWatch(args, branch.project(), projectState, changeData);
    return watch.getWatchers(type, includeWatchersFromNotifyConfig);
  }

  /** Any user who has published comments on this change. */
  protected void ccAllApprovals() {
    if (!NotifyHandling.ALL.equals(notify.handling())
        && !NotifyHandling.OWNER_REVIEWERS.equals(notify.handling())) {
      return;
    }

    try {
      for (Account.Id id : changeData.reviewers().all()) {
        add(RecipientType.CC, id);
      }
    } catch (StorageException err) {
      logger.atWarning().withCause(err).log("Cannot CC users that reviewed updated change");
    }
  }

  /** Users who were added as reviewers to this change. */
  protected void ccExistingReviewers() {
    if (!NotifyHandling.ALL.equals(notify.handling())
        && !NotifyHandling.OWNER_REVIEWERS.equals(notify.handling())) {
      return;
    }

    try {
      for (Account.Id id : changeData.reviewers().byState(ReviewerStateInternal.REVIEWER)) {
        add(RecipientType.CC, id);
      }
    } catch (StorageException err) {
      logger.atWarning().withCause(err).log("Cannot CC users that commented on updated change");
    }
  }

  @Override
  protected void add(RecipientType rt, Account.Id to) {
    addRecipient(rt, to, /* isWatcher= */ false);
  }

  /** This bypasses the EmailStrategy.ATTENTION_SET_ONLY strategy when adding the recipient. */
  @Override
  protected void addWatcher(RecipientType rt, Account.Id to) {
    addRecipient(rt, to, /* isWatcher= */ true);
  }

  private void addRecipient(RecipientType rt, Account.Id to, boolean isWatcher) {
    if (!isWatcher) {
      Optional<AccountState> accountState = args.accountCache.get(to);
      if (emailOnlyAttentionSetIfEnabled
          && accountState.isPresent()
          && accountState.get().generalPreferences().getEmailStrategy()
              == EmailStrategy.ATTENTION_SET_ONLY
          && !currentAttentionSet.contains(to)) {
        return;
      }
    }
    if (emailOnlyAuthors && !authors.contains(to)) {
      return;
    }
    super.add(rt, to);
  }

  @Override
  protected boolean isVisibleTo(Account.Id to) throws PermissionBackendException {
    if (!projectState.statePermitsRead()) {
      return false;
    }
    return args.permissionBackend.absentUser(to).change(changeData).test(ChangePermission.READ);
  }

  /** Find all users who are authors of any part of this change. */
  protected Set<Account.Id> getAuthors() {
    Set<Account.Id> authors = new HashSet<>();

    switch (notify.handling()) {
      case NONE:
        break;
      case ALL:
      default:
        if (patchSet != null) {
          authors.add(patchSet.uploader());
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
    soyContext.put("diffLines", getDiffTemplateData(getUnifiedDiff()));

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
    changeData.put(
        "sizeBucket",
        ChangeSizeBucket.getChangeSizeBucket(getInsertionsCount() + getDeletionsCount()));
    soyContext.put("change", changeData);

    Map<String, Object> patchSetData = new HashMap<>();
    patchSetData.put("patchSetId", patchSet.number());
    patchSetData.put("refName", patchSet.refName());
    soyContext.put("patchSet", patchSetData);

    Map<String, Object> patchSetInfoData = new HashMap<>();
    patchSetInfoData.put("authorName", patchSetInfo.getAuthor().getName());
    patchSetInfoData.put("authorEmail", patchSetInfo.getAuthor().getEmail());
    soyContext.put("patchSetInfo", patchSetInfoData);

    footers.add(MailHeader.CHANGE_ID.withDelimiter() + change.getKey().get());
    footers.add(MailHeader.CHANGE_NUMBER.withDelimiter() + change.getChangeId());
    footers.add(MailHeader.PATCH_SET.withDelimiter() + patchSet.number());
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
    for (Account.Id attentionUser : currentAttentionSet) {
      footers.add(MailHeader.ATTENTION.withDelimiter() + getNameEmailFor(attentionUser));
    }
    // Since this would be user visible, only show it if attention set is enabled
    if (args.settings.isAttentionSetEnabled && !currentAttentionSet.isEmpty()) {
      // We need names rather than account ids / emails to make it user readable.
      soyContext.put(
          "attentionSet",
          currentAttentionSet.stream().map(this::getNameFor).sorted().collect(toImmutableList()));
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
    } catch (StorageException e) {
      logger.atWarning().withCause(e).log("Cannot get change reviewers");
    }
    return reviewers;
  }

  private Set<Account.Id> getAttentionSet() {
    Set<Account.Id> attentionSet = new TreeSet<>();
    try {
      attentionSet =
          additionsOnly(changeData.attentionSet()).stream()
              .map(AttentionSetUpdate::account)
              .collect(Collectors.toSet());
    } catch (StorageException e) {
      logger.atWarning().withCause(e).log("Cannot get change attention set");
    }
    return attentionSet;
  }

  public boolean getIncludeDiff() {
    return args.settings.includeDiff;
  }

  private static final int HEAP_EST_SIZE = 32 * 1024;

  /** Show patch set as unified difference. */
  public String getUnifiedDiff() {
    Map<String, FileDiffOutput> modifiedFiles;
    modifiedFiles = listModifiedFiles();
    if (modifiedFiles.isEmpty()) {
      // Octopus merges are not well supported for diff output by Gerrit.
      // Currently these always have a null oldId in the PatchList.
      return "[Empty change (potentially Octopus merge); cannot be formatted as a diff.]\n";
    }

    int maxSize = args.settings.maximumDiffSize;
    TemporaryBuffer.Heap buf = new TemporaryBuffer.Heap(Math.min(HEAP_EST_SIZE, maxSize), maxSize);
    try (DiffFormatter fmt = new DiffFormatter(buf)) {
      try (Repository git = args.server.openRepository(change.getProject())) {
        try {
          ObjectId oldId = modifiedFiles.values().iterator().next().oldCommitId();
          ObjectId newId = modifiedFiles.values().iterator().next().newCommitId();
          if (oldId.equals(ObjectId.zeroId())) {
            // DiffOperations returns ObjectId.zeroId if newCommit is a root commit, i.e. has no
            // parents.
            oldId = null;
          }
          fmt.setRepository(git);
          fmt.setDetectRenames(true);
          fmt.format(oldId, newId);
          return RawParseUtils.decode(buf.toByteArray());
        } catch (IOException e) {
          if (JGitText.get().inMemoryBufferLimitExceeded.equals(e.getMessage())) {
            return "";
          }
          logger.atSevere().withCause(e).log("Cannot format patch");
          return "";
        }
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("Cannot open repository to format patch");
        return "";
      }
    }
  }

  /**
   * Generate a list of maps representing each line of the unified diff. The line maps will have a
   * 'type' key which maps to one of 'common', 'add' or 'remove' and a 'text' key which maps to the
   * line's content.
   *
   * @param sourceDiff the unified diff that we're converting to the map.
   * @return map of 'type' to a line's content.
   */
  protected static ImmutableList<ImmutableMap<String, String>> getDiffTemplateData(
      String sourceDiff) {
    ImmutableList.Builder<ImmutableMap<String, String>> result = ImmutableList.builder();
    Splitter lineSplitter = Splitter.on(System.getProperty("line.separator"));
    for (String diffLine : lineSplitter.split(sourceDiff)) {
      ImmutableMap.Builder<String, String> lineData = ImmutableMap.builder();
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
      result.add(lineData.build());
    }
    return result.build();
  }
}
