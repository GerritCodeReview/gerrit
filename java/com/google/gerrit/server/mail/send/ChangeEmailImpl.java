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

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.BranchNameKey;
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
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.send.ProjectWatch.Watchers;
import com.google.gerrit.server.mail.send.ProjectWatch.Watchers.WatcherList;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

/** Populates an email for change related notifications. */
@AutoFactory
public final class ChangeEmailImpl implements ChangeEmail {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Available after construction
  private final EmailArguments args;
  private final Set<Account.Id> currentAttentionSet;
  private final Change change;
  private final ChangeData changeData;
  private final BranchNameKey branch;
  private final ChangeEmailDecorator changeEmailDecorator;

  // Available after init or after being explicitly set.
  private OutgoingEmail email;
  private List<Account.Id> stars;
  private PatchSet patchSet;
  private PatchSetInfo patchSetInfo;
  private String changeMessage;
  private String changeMessageThreadId;
  private Instant timestamp;
  private ProjectState projectState;
  private Set<Account.Id> authors;
  private boolean emailOnlyAuthors;
  private boolean emailOnlyAttentionSetIfEnabled;
  // Watchers ignore attention set rules.
  private Set<Account.Id> watcherAccounts = new HashSet<>();
  // Watcher can only be an email if it's specified in notify section of ProjectConfig.
  private Set<Address> watcherEmails = new HashSet<>();
  private boolean isThreadReply = false;

  public ChangeEmailImpl(
      @Provided EmailArguments args,
      Project.NameKey project,
      Change.Id changeId,
      ChangeEmailDecorator changeEmailDecorator) {
    this.args = args;
    this.changeData = args.newChangeData(project, changeId);
    change = changeData.change();
    emailOnlyAuthors = false;
    emailOnlyAttentionSetIfEnabled = true;
    currentAttentionSet = getAttentionSet();
    branch = changeData.change().getDest();
    this.changeEmailDecorator = changeEmailDecorator;
  }

  public ChangeEmailImpl(
      @Provided EmailArguments args,
      ChangeData changeData,
      ChangeEmailDecorator changeEmailDecorator) {
    this.args = args;
    this.changeData = changeData;
    change = changeData.change();
    emailOnlyAuthors = false;
    emailOnlyAttentionSetIfEnabled = true;
    currentAttentionSet = getAttentionSet();
    branch = changeData.change().getDest();
    this.changeEmailDecorator = changeEmailDecorator;
  }

  @Override
  public void markAsReply() {
    isThreadReply = true;
  }

  @Override
  public Change getChange() {
    return change;
  }

  @Override
  public ChangeData getChangeData() {
    return changeData;
  }

  @Override
  @Nullable
  public Instant getTimestamp() {
    return timestamp;
  }

  @Override
  public void setPatchSet(PatchSet ps) {
    patchSet = ps;
  }

  @Override
  @Nullable
  public PatchSet getPatchSet() {
    return patchSet;
  }

  @Override
  public void setPatchSet(PatchSet ps, PatchSetInfo psi) {
    patchSet = ps;
    patchSetInfo = psi;
  }

  @Override
  public void setChangeMessage(String cm, Instant t) {
    changeMessage = cm;
    timestamp = t;
  }

  @Override
  public void setEmailOnlyAttentionSetIfEnabled(boolean value) {
    emailOnlyAttentionSetIfEnabled = value;
  }

  @Override
  public boolean shouldSendMessage() {
    return changeEmailDecorator.shouldSendMessage();
  }

  @Override
  public void init(OutgoingEmail email) throws EmailException {
    this.email = email;

    changeMessageThreadId =
        String.format(
            "<gerrit.%s.%s@%s>",
            change.getCreatedOn().toEpochMilli(), change.getKey().get(), email.getGerritHost());

    if (email.getFrom() != null) {
      // Is the from user in an email squelching group?
      try {
        args.permissionBackend.absentUser(email.getFrom()).check(GlobalPermission.EMAIL_REVIEWERS);
      } catch (AuthException | PermissionBackendException e) {
        emailOnlyAuthors = true;
      }
    }

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
      email.setHeader(MailHeader.PATCH_SET.fieldName(), patchSet.number() + "");
      if (patchSetInfo == null) {
        try {
          patchSetInfo = args.patchSetInfoFactory.get(changeData.notes(), patchSet.id());
        } catch (PatchSetInfoNotAvailableException | StorageException err) {
          patchSetInfo = null;
        }
      }
    }

    try {
      stars = changeData.stars();
    } catch (StorageException e) {
      throw new EmailException("Failed to load stars for change " + change.getChangeId(), e);
    }

    BranchEmailUtils.setListIdHeader(email, branch);
    if (timestamp != null) {
      email.setHeader(FieldName.DATE, timestamp);
    }
    email.setHeader(MailHeader.CHANGE_ID.fieldName(), "" + change.getKey().get());
    email.setHeader(MailHeader.CHANGE_NUMBER.fieldName(), "" + change.getChangeId());
    email.setHeader(MailHeader.PROJECT.fieldName(), "" + change.getProject());
    setChangeUrlHeader();
    setCommitIdHeader();

    changeEmailDecorator.init(email, this);
  }

  private void setChangeUrlHeader() {
    final String u = getChangeUrl();
    if (u != null) {
      email.setHeader(MailHeader.CHANGE_URL.fieldName(), "<" + u + ">");
    }
  }

  private void setCommitIdHeader() {
    if (patchSet != null) {
      email.setHeader(MailHeader.COMMIT.fieldName(), patchSet.commitId().name());
    }
  }

  private void setChangeSubjectHeader() {
    email.setHeader(FieldName.SUBJECT, email.textTemplate("ChangeSubject"));
  }

  private int getInsertionsCount() {
    return listModifiedFiles().entrySet().stream()
        .filter(e -> !Patch.COMMIT_MSG.equals(e.getKey()))
        .map(Map.Entry::getValue)
        .map(FileDiffOutput::insertions)
        .reduce(0, Integer::sum);
  }

  private int getDeletionsCount() {
    return listModifiedFiles().values().stream()
        .map(FileDiffOutput::deletions)
        .reduce(0, Integer::sum);
  }

  /**
   * Get a link to the change; null if the server doesn't know its own address or if the address is
   * malformed. The link will contain a usp parameter set to "email" to inform the frontend on
   * clickthroughs where the link came from.
   */
  @Nullable
  private String getChangeUrl() {
    return args.urlFormatter
        .get()
        .getChangeViewUrl(change.getProject(), change.getId())
        .map(EmailArguments::addUspParam)
        .orElse(null);
  }

  /** Sets headers for conversation grouping */
  private void setThreadHeaders() {
    if (isThreadReply) {
      email.setHeader("In-Reply-To", changeMessageThreadId);
    }
    email.setHeader("References", changeMessageThreadId);
  }

  /** Get the text of the "cover letter". */
  @Override
  public String getCoverLetter() {
    if (changeMessage != null) {
      return changeMessage.trim();
    }
    return "";
  }

  /** Create the change message and the affected file list. */
  private String getChangeDetail() {
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
                modifiedFiles.size() - 1, // -1 to account for the commit message
                getInsertionsCount(),
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
  @Override
  public Map<String, FileDiffOutput> listModifiedFiles(int patchSetId) {
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
  @Override
  public Map<String, FileDiffOutput> listModifiedFiles() {
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
  @Override
  public ProjectState getProjectState() {
    return projectState;
  }

  /** TO or CC all vested parties (change owner, patch set uploader, author). */
  @Override
  public void addAuthors(RecipientType rt) {
    for (Account.Id id : getAuthors()) {
      email.addByAccountId(rt, id);
    }
  }

  /** BCC any user who has starred this change. */
  @Override
  public void bccStarredBy() {
    if (!NotifyHandling.ALL.equals(email.getNotify().handling())) {
      return;
    }

    stars.forEach(accountId -> email.addByAccountId(RecipientType.BCC, accountId));
  }

  /** Include users and groups that want notification of events. */
  @Override
  public void includeWatchers(NotifyType type) {
    includeWatchers(type, true);
  }

  /** Include users and groups that want notification of events. */
  @Override
  public void includeWatchers(NotifyType type, boolean includeWatchersFromNotifyConfig) {
    try {
      Watchers matching = getWatchers(type, includeWatchersFromNotifyConfig);
      addWatchers(RecipientType.TO, matching.to);
      addWatchers(RecipientType.CC, matching.cc);
      addWatchers(RecipientType.BCC, matching.bcc);
    } catch (StorageException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
      logger.atWarning().withCause(err).log("Cannot BCC watchers for %s", type);
    }
  }

  /** Add users or email addresses to the TO, CC, or BCC list. */
  private void addWatchers(RecipientType type, WatcherList watcherList) {
    watcherAccounts.addAll(watcherList.accounts);
    for (Account.Id user : watcherList.accounts) {
      email.addByAccountId(type, user);
    }

    watcherEmails.addAll(watcherList.emails);
    for (Address addr : watcherList.emails) {
      email.addByEmail(type, addr);
    }
  }

  private final Watchers getWatchers(NotifyType type, boolean includeWatchersFromNotifyConfig) {
    if (!NotifyHandling.ALL.equals(email.getNotify().handling())) {
      return new Watchers();
    }

    ProjectWatch watch = new ProjectWatch(args, branch.project(), projectState, changeData);
    return watch.getWatchers(type, includeWatchersFromNotifyConfig);
  }

  /** Any user who has published comments on this change. */
  @Override
  public void ccAllApprovals() {
    if (!NotifyHandling.ALL.equals(email.getNotify().handling())
        && !NotifyHandling.OWNER_REVIEWERS.equals(email.getNotify().handling())) {
      return;
    }

    try {
      for (Account.Id id : changeData.reviewers().all()) {
        email.addByAccountId(RecipientType.CC, id);
      }
    } catch (StorageException err) {
      logger.atWarning().withCause(err).log("Cannot CC users that reviewed updated change");
    }
  }

  /** Users who were added as reviewers to this change. */
  @Override
  public void ccExistingReviewers() {
    if (!NotifyHandling.ALL.equals(email.getNotify().handling())
        && !NotifyHandling.OWNER_REVIEWERS.equals(email.getNotify().handling())) {
      return;
    }

    try {
      for (Account.Id id : changeData.reviewers().byState(ReviewerStateInternal.REVIEWER)) {
        email.addByAccountId(RecipientType.CC, id);
      }
    } catch (StorageException err) {
      logger.atWarning().withCause(err).log("Cannot CC users that commented on updated change");
    }
  }

  @Override
  public boolean isRecipientAllowed(Address addr) throws PermissionBackendException {
    if (!projectState.statePermitsRead()) {
      return false;
    }
    if (emailOnlyAuthors) {
      return false;
    }

    // If the email is a watcher email, skip permission check. An email can only be a watcher if
    // it is specified in notify section of ProjectConfig, so we trust that the recipient is
    // allowed.
    if (watcherEmails.contains(addr)) {
      return true;
    }
    return args.permissionBackend
        .user(args.anonymousUser.get())
        .change(changeData)
        .test(ChangePermission.READ);
  }

  @Override
  public boolean isRecipientAllowed(Account.Id to) throws PermissionBackendException {
    if (!projectState.statePermitsRead()) {
      return false;
    }
    if (emailOnlyAuthors && !getAuthors().contains(to)) {
      return false;
    }
    // Watchers ignore AttentionSet rules.
    if (!watcherAccounts.contains(to)) {
      Optional<AccountState> accountState = args.accountCache.get(to);
      if (emailOnlyAttentionSetIfEnabled
          && accountState.isPresent()
          && accountState.get().generalPreferences().getEmailStrategy()
              == EmailStrategy.ATTENTION_SET_ONLY
          && !currentAttentionSet.contains(to)) {
        return false;
      }
    }
    return args.permissionBackend.absentUser(to).change(changeData).test(ChangePermission.READ);
  }

  /** Lazily finds all users who are authors of any part of this change. */
  private Set<Account.Id> getAuthors() {
    if (this.authors != null) {
      return this.authors;
    }
    Set<Account.Id> authors = new HashSet<>();

    switch (email.getNotify().handling()) {
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

    return this.authors = authors;
  }

  @Override
  public void populateEmailContent() throws EmailException {
    BranchEmailUtils.addBranchData(email, args, branch);
    setThreadHeaders();

    email.addSoyParam("changeId", change.getKey().get());
    email.addSoyParam("coverLetter", getCoverLetter());
    email.addSoyParam("fromName", email.getNameFor(email.getFrom()));
    email.addSoyParam("fromEmail", email.getNameEmailFor(email.getFrom()));
    email.addSoyParam("diffLines", ChangeEmail.getDiffTemplateData(getUnifiedDiff()));

    email.addSoyEmailDataParam("unifiedDiff", getUnifiedDiff());
    email.addSoyEmailDataParam("changeDetail", getChangeDetail());
    email.addSoyEmailDataParam("changeUrl", getChangeUrl());
    email.addSoyEmailDataParam("includeDiff", getIncludeDiff());

    Map<String, String> changeData = new HashMap<>();

    String subject = change.getSubject();
    String originalSubject = change.getOriginalSubject();
    changeData.put("subject", subject);
    changeData.put("originalSubject", originalSubject);
    changeData.put("shortSubject", shortenSubject(subject));
    changeData.put("shortOriginalSubject", shortenSubject(originalSubject));

    changeData.put("ownerName", email.getNameFor(change.getOwner()));
    changeData.put("ownerEmail", email.getNameEmailFor(change.getOwner()));
    changeData.put("changeNumber", Integer.toString(change.getChangeId()));
    changeData.put(
        "sizeBucket",
        ChangeSizeBucket.getChangeSizeBucket(getInsertionsCount() + getDeletionsCount()));
    email.addSoyParam("change", changeData);

    Map<String, Object> patchSetData = new HashMap<>();
    patchSetData.put("patchSetId", patchSet.number());
    patchSetData.put("refName", patchSet.refName());
    email.addSoyParam("patchSet", patchSetData);

    Map<String, Object> patchSetInfoData = new HashMap<>();
    patchSetInfoData.put("authorName", patchSetInfo.getAuthor().getName());
    patchSetInfoData.put("authorEmail", patchSetInfo.getAuthor().getEmail());
    email.addSoyParam("patchSetInfo", patchSetInfoData);

    email.addFooter(MailHeader.CHANGE_ID.withDelimiter() + change.getKey().get());
    email.addFooter(MailHeader.CHANGE_NUMBER.withDelimiter() + change.getChangeId());
    email.addFooter(MailHeader.PATCH_SET.withDelimiter() + patchSet.number());
    email.addFooter(MailHeader.OWNER.withDelimiter() + email.getNameEmailFor(change.getOwner()));
    for (String reviewer : getEmailsByState(ReviewerStateInternal.REVIEWER)) {
      email.addFooter(MailHeader.REVIEWER.withDelimiter() + reviewer);
    }
    for (String reviewer : getEmailsByState(ReviewerStateInternal.CC)) {
      email.addFooter(MailHeader.CC.withDelimiter() + reviewer);
    }
    for (Account.Id attentionUser : currentAttentionSet) {
      email.addFooter(MailHeader.ATTENTION.withDelimiter() + email.getNameEmailFor(attentionUser));
    }
    if (!currentAttentionSet.isEmpty()) {
      // We need names rather than account ids / emails to make it user readable.
      email.addSoyParam(
          "attentionSet",
          currentAttentionSet.stream().map(email::getNameFor).sorted().collect(toImmutableList()));
    }

    setChangeSubjectHeader();
    if (email.getNotify().handling().equals(NotifyHandling.OWNER_REVIEWERS)
        || email.getNotify().handling().equals(NotifyHandling.ALL)) {
      try {
        this.changeData.reviewersByEmail().byState(ReviewerStateInternal.CC).stream()
            .forEach(address -> email.addByEmail(RecipientType.CC, address));
        this.changeData.reviewersByEmail().byState(ReviewerStateInternal.REVIEWER).stream()
            .forEach(address -> email.addByEmail(RecipientType.CC, address));
      } catch (StorageException e) {
        throw new EmailException("Failed to add unregistered CCs " + change.getChangeId(), e);
      }
    }

    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("ChangeHeaderHtml"));
    }
    email.appendText(email.textTemplate("ChangeHeader"));
    changeEmailDecorator.populateEmailContent();
    email.appendText(email.textTemplate("ChangeFooter"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("ChangeFooterHtml"));
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
        reviewers.add(email.getNameEmailFor(who));
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

  private boolean getIncludeDiff() {
    return args.settings.includeDiff;
  }

  private static final int HEAP_EST_SIZE = 32 * 1024;

  /** Show patch set as unified difference. */
  @Override
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
}
