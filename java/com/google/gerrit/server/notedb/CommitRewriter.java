// Copyright (C) 2021 The Android Open Source Project
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
package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ASSIGNEE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ATTENTION;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_REAL_USER;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.util.AccountTemplateUtil.ACCOUNT_TEMPLATE_PATTERN;
import static com.google.gerrit.server.util.AccountTemplateUtil.ACCOUNT_TEMPLATE_REGEX;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.notedb.ChangeNoteUtil.AttentionStatusInNoteDb;
import com.google.gerrit.server.notedb.ChangeNoteUtil.CommitMessageRange;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.PackInserter;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Rewrites ('backfills') commit history of change in NoteDb to not contain user data. Only fixes
 * known cases, rewriting commits case by case.
 *
 * <p>The cases where we used to put user data in NoteDb can be found by
 * https://gerrit-review.googlesource.com/q/hashtag:user-data-cleanup
 *
 * <p>As opposed to {@link NoteDbRewriter} implementations, which target a specific change and are
 * used by REST endpoints, this rewriter is used as standalone tool, that bulk backfills changes by
 * project.
 */
@UsedAt(UsedAt.Project.GOOGLE)
@Singleton
public class CommitRewriter {
  /** Options to run {@link #backfillProject}. */
  public static class RunOptions {
    /** Whether to rewrite the commit history or only find refs that need to be fixed. */
    public boolean dryRun = true;
    /**
     * Whether to verify that resulting commits contain user data for the accounts that are linked
     * to a change, see {@link #verifyCommit}, {@link #collectAccounts}.
     */
    public boolean verifyCommits = true;
    /** Whether to compute and output the diff of the commit history for the backfilled refs. */
    public boolean outputDiff = true;
  }

  /** Result of the backfill run for a project. */
  public static class BackfillResult {

    /** If the run for the project was successful. */
    public boolean ok;

    /**
     * Refs that were fixed by the run/ would be fixed if in --dry-run, together with their commit
     * history diff. Diff is empty if --output-diff is false.
     */
    public Map<String, List<CommitDiff>> fixedRefDiff = new HashMap<>();

    /**
     * Refs that still contain user data after the backfill run. Only filled if --verify-commits,
     * see {@link #verifyCommit}
     */
    public List<String> refsStillInvalidAfterFix = new ArrayList<>();

    /** Refs, failed to backfill by the run. */
    public List<String> refsFailedToFix = new ArrayList<>();
  }

  /** Diff result of a single commit rewrite */
  @AutoValue
  public abstract static class CommitDiff {
    public static CommitDiff create(ObjectId oldSha1, String commitDiff) {
      return new AutoValue_CommitRewriter_CommitDiff(oldSha1, commitDiff);
    }

    /** SHA1 of the overwritten commit */
    public abstract ObjectId oldSha1();

    /** Diff applied to the commit with {@link #oldSha1} */
    public abstract String diff();
  }

  public static final String DEFAULT_ACCOUNT_REPLACEMENT = "Gerrit Account";

  private static final Pattern NON_REPLACE_ACCOUNT_PATTERN =
      Pattern.compile(DEFAULT_ACCOUNT_REPLACEMENT + "|" + ACCOUNT_TEMPLATE_REGEX);

  private static final Pattern OK_ACCOUNT_NAME_PATTERN =
      Pattern.compile("(?i:someone|someone else|anonymous)|" + ACCOUNT_TEMPLATE_REGEX);

  /** Patterns to match change messages that need to be fixed. */
  private static final Pattern ASSIGNEE_DELETED_PATTERN = Pattern.compile("Assignee deleted: (.*)");

  private static final Pattern ASSIGNEE_ADDED_PATTERN = Pattern.compile("Assignee added: (.*)");
  private static final Pattern ASSIGNEE_CHANGED_PATTERN =
      Pattern.compile("Assignee changed from: (.*) to: (.*)");

  private static final Pattern REMOVED_REVIEWER_PATTERN =
      Pattern.compile("Removed (cc|reviewer) (.*)(\\.| with the following votes)");

  private static final Pattern REMOVED_VOTE_PATTERN = Pattern.compile("Removed (.*) by (.*)");

  private static final Pattern REMOVED_CHANGE_MESSAGE_PATTERN =
      Pattern.compile("Change message removed by: (.*)(\nReason: .*)?");

  private static final Pattern SUBMITTED_PATTERN =
      Pattern.compile("Change has been successfully (.*) by (.*)");

  private static final Pattern ON_CODE_OWNER_ADD_REVIEWER_PATTERN =
      Pattern.compile("(.*) who was added as reviewer owns the following files");
  private static final String ON_CODE_OWNER_APPROVAL_REGEX = "code-owner approved by (.*):";
  private static final String ON_CODE_OWNER_OVERRIDE_REGEX =
      "code-owners submit requirement .* overridden by (.*)";

  private static final Pattern ON_CODE_OWNER_REVIEW_PATTERN =
      Pattern.compile(ON_CODE_OWNER_APPROVAL_REGEX + "|" + ON_CODE_OWNER_OVERRIDE_REGEX);

  private static final Pattern REPLY_BY_REASON_PATTERN =
      Pattern.compile("(.*) replied on the change");
  private static final Pattern ADDED_BY_REASON_PATTERN =
      Pattern.compile("Added by (.*) using the hovercard menu");
  private static final Pattern REMOVED_BY_REASON_PATTERN =
      Pattern.compile("Removed by (.*) using the hovercard menu");
  private static final Pattern REMOVED_BY_ICON_CLICK_REASON_PATTERN =
      Pattern.compile("Removed by (.*) by clicking the attention icon");

  /** Matches {@link Account#getNameEmail} */
  private static final Pattern NAME_EMAIL_PATTERN = Pattern.compile("(.*) (\\<.*\\>|\\(.*\\))");

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeNotes.Factory changeNotesFactory;
  private final AccountCache accountCache;
  private final DiffAlgorithm diffAlgorithm = new HistogramDiff();
  private static final Gson gson = OutputFormat.JSON_COMPACT.newGson();

  @Inject
  CommitRewriter(ChangeNotes.Factory changeNotesFactory, AccountCache accountCache) {
    this.changeNotesFactory = changeNotesFactory;
    this.accountCache = accountCache;
  }

  /**
   * Rewrites commit history of {@link RefNames#changeMetaRef}s in single {@code repo}. Only
   * rewrites branch if necessary, i.e. if there were any commits that contained user data.
   *
   * <p>See {@link RunOptions} for the execution and output options.
   *
   * @param project project to backfill
   * @param repo repo to backfill
   * @param options {@link RunOptions} to control how the run is executed.
   * @return BackfillResult
   */
  public BackfillResult backfillProject(
      Project.NameKey project, Repository repo, RunOptions options) {
    BackfillResult result = new BackfillResult();
    result.ok = true;
    try (RevWalk revWalk = new RevWalk(repo);
        ObjectInserter ins = newPackInserter(repo)) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      bru.setForceRefLog(true);
      bru.setRefLogMessage(CommitRewriter.class.getName(), false);
      bru.setAllowNonFastForwards(true);
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
        Change.Id changeId = Change.Id.fromRef(ref.getName());
        if (changeId == null || !ref.getName().equals(RefNames.changeMetaRef(changeId))) {
          continue;
        }
        try {
          ImmutableSet<AccountState> accountsInChange = ImmutableSet.of();
          if (options.verifyCommits) {
            try {
              ChangeNotes changeNotes = changeNotesFactory.create(project, changeId);
              accountsInChange = collectAccounts(changeNotes);
            } catch (Exception e) {
              logger.atWarning().withCause(e).log("Failed to run verification on ref %s", ref);
            }
          }
          ChangeFixProgress changeFixProgress =
              backfillChange(revWalk, ins, ref, accountsInChange, options);
          if (changeFixProgress.anyFixesApplied) {
            bru.addCommand(
                new ReceiveCommand(ref.getObjectId(), changeFixProgress.newTipId, ref.getName()));
            result.fixedRefDiff.put(ref.getName(), changeFixProgress.commitDiffs);
          }

          if (!changeFixProgress.isValidAfterFix) {
            result.refsStillInvalidAfterFix.add(ref.getName());
          }
        } catch (Exception e) {
          logger.atWarning().withCause(e).log("Failed to fix ref %s", ref);
          result.refsFailedToFix.add(ref.getName());
        }
      }

      if (!bru.getCommands().isEmpty()) {
        if (!options.dryRun) {
          ins.flush();
          RefUpdateUtil.executeChecked(bru, revWalk);
        }
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to fix project %s", project.get());
      result.ok = false;
    }

    return result;
  }

  /**
   * Retrieves accounts, that are associated with a change (e.g. reviewers, commenters, etc.). These
   * accounts are used to verify that commits do not contain user data. See {@link #verifyCommit}
   *
   * @param changeNotes {@link ChangeNotes} of the change to retrieve associated accounts from.
   * @return {@link AccountState} of accounts, that are associated with the change.
   */
  private ImmutableSet<AccountState> collectAccounts(ChangeNotes changeNotes) {
    Set<Account.Id> accounts = new HashSet<>();
    accounts.add(changeNotes.getChange().getOwner());
    for (PatchSetApproval patchSetApproval : changeNotes.getApprovals().values()) {
      if (patchSetApproval.accountId() != null) {
        accounts.add(patchSetApproval.accountId());
      }
      if (patchSetApproval.realAccountId() != null) {
        accounts.add(patchSetApproval.realAccountId());
      }
    }
    accounts.addAll(changeNotes.getAllPastReviewers());
    accounts.addAll(changeNotes.getPastAssignees());
    changeNotes
        .getAttentionSetUpdates()
        .forEach(attentionSetUpdate -> accounts.add(attentionSetUpdate.account()));
    for (SubmitRecord submitRecord : changeNotes.getSubmitRecords()) {
      if (submitRecord.labels != null) {
        accounts.addAll(
            submitRecord.labels.stream()
                .map(label -> label.appliedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
      }
    }
    for (HumanComment comment : changeNotes.getHumanComments().values()) {
      if (comment.author != null) {
        accounts.add(comment.author.getId());
      }
      if (comment.getRealAuthor() != null) {
        accounts.add(comment.getRealAuthor().getId());
      }
    }
    return ImmutableSet.copyOf(accountCache.get(accounts).values());
  }

  /** Verifies that the commit does not contain user data of accounts in {@code accounts}. */
  private boolean verifyCommit(
      String commitMessage, PersonIdent author, Collection<AccountState> accounts) {
    for (AccountState accountState : accounts) {
      Account account = accountState.account();
      if (commitMessage.contains(account.getName())) {
        return false;
      }
      if (account.fullName() != null && commitMessage.contains(account.fullName())) {
        return false;
      }
      if (account.displayName() != null && commitMessage.contains(account.displayName())) {
        return false;
      }
      if (account.preferredEmail() != null && commitMessage.contains(account.preferredEmail())) {
        return false;
      }
      if (accountState.userName().isPresent()
          && commitMessage.contains(accountState.userName().get())) {
        return false;
      }
      Stream<String> allEmails =
          accountState.externalIds().stream().map(ExternalId::email).filter(Objects::nonNull);
      if (allEmails.anyMatch(email -> commitMessage.contains(email))) {
        return false;
      }
      if (author.toString().contains(account.getName())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Walks the ref history from oldest update to the most recent update, fixing the commits that
   * contain user data case by case. Commit history is rewritten from the first commit, that needs
   * to be updated, for all subsequent updates. The new ref tip is returned in {@link
   * ChangeFixProgress#newTipId}.
   */
  public ChangeFixProgress backfillChange(
      RevWalk revWalk,
      ObjectInserter inserter,
      Ref ref,
      ImmutableSet<AccountState> accountsInChange,
      RunOptions options)
      throws IOException, ConfigInvalidException {

    ObjectId oldTip = ref.getObjectId();
    // Walk from the first commit of the branch.
    revWalk.reset();
    revWalk.markStart(revWalk.parseCommit(oldTip));
    revWalk.sort(RevSort.TOPO);

    revWalk.sort(RevSort.REVERSE);

    RevCommit originalCommit;

    boolean rewriteStarted = false;
    ChangeFixProgress changeFixProgress = new ChangeFixProgress();
    while ((originalCommit = revWalk.next()) != null) {

      changeFixProgress.updateAuthorId =
          parseIdent(changeFixProgress, originalCommit.getAuthorIdent());
      PersonIdent fixedAuthorIdent;
      if (changeFixProgress.updateAuthorId.isPresent()) {
        fixedAuthorIdent =
            getFixedIdent(originalCommit.getAuthorIdent(), changeFixProgress.updateAuthorId.get());
      } else {
        // Field to parse id from ident. Update by gerrit server or an old/broken change.
        // Leave as it is.
        fixedAuthorIdent = originalCommit.getAuthorIdent();
      }
      Optional<String> fixedCommitMessage = fixedCommitMessage(originalCommit, changeFixProgress);
      String commitMessage =
          fixedCommitMessage.isPresent()
              ? fixedCommitMessage.get()
              : originalCommit.getFullMessage();
      if (options.verifyCommits) {
        boolean isCommitValid = verifyCommit(commitMessage, fixedAuthorIdent, accountsInChange);
        changeFixProgress.isValidAfterFix &= isCommitValid;
        if (!isCommitValid) {
          StringBuilder detailedVerificationStatus =
              new StringBuilder(
                  String.format(
                      "Commit %s of ref %s failed verification after fix",
                      originalCommit.getId(), ref));
          detailedVerificationStatus.append("\nCommit body:\n");
          detailedVerificationStatus.append(commitMessage);
          if (fixedCommitMessage.isPresent()) {
            detailedVerificationStatus.append("\n was fixed.\n");
          }
          detailedVerificationStatus.append("Commit author:\n");
          detailedVerificationStatus.append(fixedAuthorIdent.toString());
          logger.atWarning().log(detailedVerificationStatus.toString());
        }
      }
      boolean needsFix =
          !fixedAuthorIdent.equals(originalCommit.getAuthorIdent())
              || fixedCommitMessage.isPresent();

      if (!rewriteStarted && !needsFix) {
        changeFixProgress.newTipId = originalCommit;
        continue;
      }
      rewriteStarted = true;
      changeFixProgress.anyFixesApplied = true;
      CommitBuilder cb = new CommitBuilder();
      if (changeFixProgress.newTipId != null) {
        cb.setParentId(changeFixProgress.newTipId);
      }
      cb.setTreeId(originalCommit.getTree());
      cb.setMessage(commitMessage);
      cb.setAuthor(fixedAuthorIdent);
      cb.setCommitter(originalCommit.getCommitterIdent());
      cb.setEncoding(originalCommit.getEncoding());
      byte[] newCommitContent = cb.build();
      checkCommitModification(originalCommit, newCommitContent);
      changeFixProgress.newTipId = inserter.insert(Constants.OBJ_COMMIT, newCommitContent);
      // Only compute diff if the content of the commit was actually changed.
      if (options.outputDiff && needsFix) {
        String diff = computeDiff(originalCommit.getRawBuffer(), newCommitContent);
        checkState(
            !Strings.isNullOrEmpty(diff),
            "Expected diff for commit %s of ref %s",
            originalCommit.getId(),
            ref.getName());
        changeFixProgress.commitDiffs.add(CommitDiff.create(originalCommit.getId(), diff));
      }
    }
    return changeFixProgress;
  }

  /**
   * In NoteDb, all the meta information is stored in footer lines. If we accidentally drop some of
   * the footer lines, the original meta information will be lost, and the change might become
   * unparsable.
   *
   * <p>While we can not verify the entire commit content, we at least make sure that the resulting
   * commit has the same author, committer and footer lines are in the same order and contain same
   * footer keys as the original commit.
   *
   * <p>Commit message and footer values might have been rewritten.
   */
  private void checkCommitModification(RevCommit originalCommit, byte[] newCommitContent)
      throws IOException {
    RevCommit newCommit = RevCommit.parse(newCommitContent);
    PersonIdent newAuthorIdent = newCommit.getAuthorIdent();
    PersonIdent originalAuthorIdent = originalCommit.getAuthorIdent();
    // The new commit must have same author and committer ident as the original commit.
    if (!verifyPersonIdent(newAuthorIdent, originalAuthorIdent)) {
      throw new IllegalStateException(
          String.format(
              "New author %s does not match original author %s",
              newAuthorIdent.toExternalString(), originalAuthorIdent.toExternalString()));
    }
    PersonIdent newCommitterIdent = newCommit.getCommitterIdent();
    PersonIdent originalCommitterIdent = originalCommit.getCommitterIdent();
    if (!verifyPersonIdent(newCommitterIdent, originalCommitterIdent)) {
      throw new IllegalStateException(
          String.format(
              "New committer %s does not match original committer %s",
              newCommitterIdent.toExternalString(), originalCommitterIdent.toExternalString()));
    }

    List<FooterLine> newFooterLines = newCommit.getFooterLines();
    List<FooterLine> originalFooterLines = originalCommit.getFooterLines();
    // Number and order of footer lines must remain the same, the value may have changed.
    if (newFooterLines.size() != originalFooterLines.size()) {
      String diff = computeDiff(originalCommit.getRawBuffer(), newCommitContent);
      throw new IllegalStateException(
          String.format(
              "Expected footer lines in new commit to match original footer lines. Diff %s", diff));
    }
    for (int i = 0; i < newFooterLines.size(); i++) {
      FooterLine newFooterLine = newFooterLines.get(i);
      FooterLine originalFooterLine = originalFooterLines.get(i);
      if (!newFooterLine.getKey().equals(originalFooterLine.getKey())) {
        String diff = computeDiff(originalCommit.getRawBuffer(), newCommitContent);
        throw new IllegalStateException(
            String.format(
                "Expected footer lines in new commit to match original footer lines. Diff %s",
                diff));
      }
    }
  }

  private boolean verifyPersonIdent(PersonIdent newIdent, PersonIdent originalIdent) {
    return newIdent.getTimeZoneOffset() == originalIdent.getTimeZoneOffset()
        && newIdent.getWhen().getTime() == originalIdent.getWhen().getTime()
        && newIdent.getEmailAddress().equals(originalIdent.getEmailAddress());
  }

  private Optional<String> fixAssigneeChangeMessage(
      ChangeFixProgress changeFixProgress,
      Optional<Account.Id> oldAssignee,
      Optional<Account.Id> newAssignee,
      String originalChangeMessage) {
    if (Strings.isNullOrEmpty(originalChangeMessage)) {
      return Optional.empty();
    }

    Matcher assigneeDeletedMatcher = ASSIGNEE_DELETED_PATTERN.matcher(originalChangeMessage);
    if (assigneeDeletedMatcher.matches()) {
      if (!NON_REPLACE_ACCOUNT_PATTERN.matcher(assigneeDeletedMatcher.group(1)).matches()) {
        return Optional.of(
            "Assignee deleted: "
                + getPossibleAccountReplacement(
                    changeFixProgress, oldAssignee, assigneeDeletedMatcher.group(1)));
      }
      return Optional.empty();
    }

    Matcher assigneeAddedMatcher = ASSIGNEE_ADDED_PATTERN.matcher(originalChangeMessage);
    if (assigneeAddedMatcher.matches()) {
      if (!NON_REPLACE_ACCOUNT_PATTERN.matcher(assigneeAddedMatcher.group(1)).matches()) {
        return Optional.of(
            "Assignee added: "
                + getPossibleAccountReplacement(
                    changeFixProgress, newAssignee, assigneeAddedMatcher.group(1)));
      }
      return Optional.empty();
    }

    Matcher assigneeChangedMatcher = ASSIGNEE_CHANGED_PATTERN.matcher(originalChangeMessage);
    if (assigneeChangedMatcher.matches()) {
      if (!NON_REPLACE_ACCOUNT_PATTERN.matcher(assigneeChangedMatcher.group(1)).matches()) {
        return Optional.of(
            String.format(
                "Assignee changed from: %s to: %s",
                getPossibleAccountReplacement(
                    changeFixProgress, oldAssignee, assigneeChangedMatcher.group(1)),
                getPossibleAccountReplacement(
                    changeFixProgress, newAssignee, assigneeChangedMatcher.group(2))));
      }
      return Optional.empty();
    }
    return Optional.empty();
  }

  private Optional<String> fixReviewerChangeMessage(String originalChangeMessage) {
    if (Strings.isNullOrEmpty(originalChangeMessage)) {
      return Optional.empty();
    }
    Matcher matcher = REMOVED_REVIEWER_PATTERN.matcher(originalChangeMessage);

    if (matcher.find() && !ACCOUNT_TEMPLATE_PATTERN.matcher(matcher.group(2)).matches()) {
      // Since we do not use change messages for reviewer updates on UI, it does not matter what we
      // rewrite it to.
      return Optional.of(originalChangeMessage.substring(0, matcher.end(1)));
    }
    return Optional.empty();
  }

  private Optional<String> fixRemoveVoteChangeMessage(
      ChangeFixProgress changeFixProgress,
      Optional<Account.Id> reviewer,
      String originalChangeMessage) {
    if (Strings.isNullOrEmpty(originalChangeMessage)) {
      return Optional.empty();
    }

    Matcher matcher = REMOVED_VOTE_PATTERN.matcher(originalChangeMessage);
    if (matcher.matches() && !NON_REPLACE_ACCOUNT_PATTERN.matcher(matcher.group(2)).matches()) {
      return Optional.of(
          String.format(
              "Removed %s by %s",
              matcher.group(1),
              getPossibleAccountReplacement(
                  changeFixProgress, reviewer, getNameFromNameEmail(matcher.group(2)))));
    }
    return Optional.empty();
  }

  private Optional<String> fixDeleteChangeMessageCommitMessage(String originalChangeMessage) {
    if (Strings.isNullOrEmpty(originalChangeMessage)) {
      return Optional.empty();
    }

    Matcher matcher = REMOVED_CHANGE_MESSAGE_PATTERN.matcher(originalChangeMessage);
    if (matcher.matches() && !ACCOUNT_TEMPLATE_PATTERN.matcher(matcher.group(1)).matches()) {
      String fixedMessage = "Change message removed";
      if (matcher.group(2) != null) {
        fixedMessage += matcher.group(2);
      }
      return Optional.of(fixedMessage);
    }
    return Optional.empty();
  }

  private Optional<String> fixSubmitChangeMessage(String originalChangeMessage) {
    if (Strings.isNullOrEmpty(originalChangeMessage)) {
      return Optional.empty();
    }

    Matcher matcher = SUBMITTED_PATTERN.matcher(originalChangeMessage);
    if (matcher.matches()) {
      // See https://gerrit-review.googlesource.com/c/gerrit/+/272654
      return Optional.of(originalChangeMessage.substring(0, matcher.end(1)));
    }
    return Optional.empty();
  }

  /**
   * Rewrites a code owners change message.
   *
   * <p>See https://gerrit-review.googlesource.com/c/plugins/code-owners/+/305409
   */
  private Optional<String> fixCodeOwnersOnAddReviewerChangeMessage(
      ChangeFixProgress changeFixProgress, String originalMessage) {
    if (Strings.isNullOrEmpty(originalMessage)) {
      return Optional.empty();
    }

    Matcher onAddReviewerMatcher = ON_CODE_OWNER_ADD_REVIEWER_PATTERN.matcher(originalMessage);
    if (!onAddReviewerMatcher.find()
        || NON_REPLACE_ACCOUNT_PATTERN.matcher(onAddReviewerMatcher.group(1)).matches()) {
      return Optional.empty();
    }

    // Pre fix, try to replace with something meaningful.
    // Retrieve reviewer accounts from cache and try to match by their name.
    onAddReviewerMatcher.reset();
    StringBuffer sb = new StringBuffer();
    while (onAddReviewerMatcher.find()) {
      String reviewerName = onAddReviewerMatcher.group(1);
      String replacementName =
          getPossibleAccountReplacement(changeFixProgress, Optional.empty(), reviewerName);
      onAddReviewerMatcher.appendReplacement(
          sb, replacementName + ", who was added as reviewer owns the following files");
    }
    onAddReviewerMatcher.appendTail(sb);
    sb.append("\n");
    return Optional.of(sb.toString());
  }

  private Optional<String> fixCodeOwnersOnReviewChangeMessage(
      Optional<Account.Id> reviewer, String originalMessage) {
    if (Strings.isNullOrEmpty(originalMessage)) {
      return Optional.empty();
    }

    Matcher onCodeOwnerReviewMatcher = ON_CODE_OWNER_REVIEW_PATTERN.matcher(originalMessage);
    while (onCodeOwnerReviewMatcher.find()) {
      String accountName =
          firstNonNull(onCodeOwnerReviewMatcher.group(1), onCodeOwnerReviewMatcher.group(2));
      if (!ACCOUNT_TEMPLATE_PATTERN.matcher(accountName).matches()) {
        return Optional.of(
            originalMessage.replace(
                    "by " + accountName,
                    "by "
                        + reviewer
                            .map(AccountTemplateUtil::getAccountTemplate)
                            .orElse(DEFAULT_ACCOUNT_REPLACEMENT))
                + "\n");
      }
    }

    return Optional.empty();
  }

  private Optional<String> fixAttentionSetReason(String originalReason) {
    if (Strings.isNullOrEmpty(originalReason)) {
      return Optional.empty();
    }
    // Only the latest attention set updates are displayed on UI. As long as reason is
    // human-readable, it does not matter what we rewrite it to.

    Matcher replyByReasonMatcher = REPLY_BY_REASON_PATTERN.matcher(originalReason);
    if (replyByReasonMatcher.matches()
        && !OK_ACCOUNT_NAME_PATTERN.matcher(replyByReasonMatcher.group(1)).matches()) {
      return Optional.of("Someone replied on the change");
    }

    Matcher addedByReasonMatcher = ADDED_BY_REASON_PATTERN.matcher(originalReason);
    if (addedByReasonMatcher.matches()
        && !OK_ACCOUNT_NAME_PATTERN.matcher(addedByReasonMatcher.group(1)).matches()) {
      return Optional.of("Added by someone using the hovercard menu");
    }

    Matcher removedByReasonMatcher = REMOVED_BY_REASON_PATTERN.matcher(originalReason);
    if (removedByReasonMatcher.matches()
        && !OK_ACCOUNT_NAME_PATTERN.matcher(removedByReasonMatcher.group(1)).matches()) {

      return Optional.of("Removed by someone using the hovercard menu");
    }

    Matcher removedByIconClickReasonMatcher =
        REMOVED_BY_ICON_CLICK_REASON_PATTERN.matcher(originalReason);
    if (removedByIconClickReasonMatcher.matches()
        && !OK_ACCOUNT_NAME_PATTERN.matcher(removedByIconClickReasonMatcher.group(1)).matches()) {

      return Optional.of("Removed by someone by clicking the attention icon");
    }
    return Optional.empty();
  }

  /**
   * Fixes commit body case by case, so it does not contain user data. Returns fixed commit message,
   * or {@link Optional#empty} if no fixes were applied.
   */
  private Optional<String> fixedCommitMessage(RevCommit revCommit, ChangeFixProgress fixProgress)
      throws ConfigInvalidException {
    byte[] raw = revCommit.getRawBuffer();
    Charset enc = RawParseUtils.parseEncoding(raw);
    Optional<CommitMessageRange> commitMessageRange =
        ChangeNoteUtil.parseCommitMessageRange(revCommit);
    if (!commitMessageRange.isPresent()) {
      throw new ConfigInvalidException("Failed to parse commit message " + revCommit.getName());
    }
    String changeSubject =
        RawParseUtils.decode(
            enc,
            raw,
            commitMessageRange.get().subjectStart(),
            commitMessageRange.get().subjectEnd());
    Optional<String> fixedChangeMessage = Optional.empty();
    String originalChangeMessage = null;
    if (commitMessageRange.isPresent() && commitMessageRange.get().hasChangeMessage()) {
      originalChangeMessage =
          RawParseUtils.decode(
                  enc,
                  raw,
                  commitMessageRange.get().changeMessageStart(),
                  commitMessageRange.get().changeMessageEnd() + 1)
              .trim();
    }
    List<FooterLine> footerLines = revCommit.getFooterLines();
    StringBuilder footerLinesBuilder = new StringBuilder();
    boolean anyFootersFixed = false;
    for (FooterLine fl : footerLines) {
      String footerKey = fl.getKey();
      String footerValue = fl.getValue();
      if (footerKey.equalsIgnoreCase(FOOTER_ASSIGNEE.getName())) {
        Account.Id oldAssignee = fixProgress.assigneeId;
        FixIdentResult fixedAssignee = null;
        if (footerValue.equals("")) {
          fixProgress.assigneeId = null;
        } else {
          fixedAssignee = getFixedIdentString(fixProgress, footerValue);
          fixProgress.assigneeId = fixedAssignee.accountId;
        }
        if (!fixedChangeMessage.isPresent()) {
          fixedChangeMessage =
              fixAssigneeChangeMessage(
                  fixProgress,
                  Optional.ofNullable(oldAssignee),
                  Optional.ofNullable(fixProgress.assigneeId),
                  originalChangeMessage);
        }
        if (fixedAssignee != null && fixedAssignee.fixedIdentString.isPresent()) {
          addFooter(footerLinesBuilder, footerKey, fixedAssignee.fixedIdentString.get());
          anyFootersFixed = true;
          continue;
        }
      } else if (Arrays.stream(ReviewerStateInternal.values())
          .anyMatch(state -> footerKey.equalsIgnoreCase(state.getFooterKey().getName()))) {
        if (!fixedChangeMessage.isPresent()) {
          fixedChangeMessage = fixReviewerChangeMessage(originalChangeMessage);
        }
        FixIdentResult fixedReviewer = getFixedIdentString(fixProgress, footerValue);
        if (fixedReviewer.fixedIdentString.isPresent()) {
          addFooter(footerLinesBuilder, footerKey, fixedReviewer.fixedIdentString.get());
          anyFootersFixed = true;
          continue;
        }
      } else if (footerKey.equalsIgnoreCase(FOOTER_REAL_USER.getName())) {
        FixIdentResult fixedRealUser = getFixedIdentString(fixProgress, footerValue);
        if (fixedRealUser.fixedIdentString.isPresent()) {
          addFooter(footerLinesBuilder, footerKey, fixedRealUser.fixedIdentString.get());
          anyFootersFixed = true;
          continue;
        }
      } else if (footerKey.equalsIgnoreCase(FOOTER_LABEL.getName())) {
        int voterIdentStart = footerValue.indexOf(' ');
        FixIdentResult fixedVoter = null;
        if (voterIdentStart > 0) {
          String originalIdentString = footerValue.substring(voterIdentStart + 1);
          fixedVoter = getFixedIdentString(fixProgress, originalIdentString);
        }
        if (!fixedChangeMessage.isPresent()) {
          fixedChangeMessage =
              fixRemoveVoteChangeMessage(
                  fixProgress,
                  fixedVoter == null
                      ? fixProgress.updateAuthorId
                      : Optional.of(fixedVoter.accountId),
                  originalChangeMessage);
        }
        if (fixedVoter != null && fixedVoter.fixedIdentString.isPresent()) {
          String fixedLabelVote =
              footerValue.substring(0, voterIdentStart) + " " + fixedVoter.fixedIdentString.get();
          addFooter(footerLinesBuilder, footerKey, fixedLabelVote);
          anyFootersFixed = true;
          continue;
        }
      } else if (footerKey.equalsIgnoreCase(FOOTER_SUBMITTED_WITH.getName())) {
        // Record format:
        // Submitted-with: OK
        // Submitted-with: OK: Code-Review: User Name <accountId@serverId>
        int voterIdentStart = StringUtils.ordinalIndexOf(footerValue, ": ", 2);
        if (voterIdentStart >= 0) {
          String originalIdentString = footerValue.substring(voterIdentStart + 2);
          FixIdentResult fixedVoter = getFixedIdentString(fixProgress, originalIdentString);
          if (fixedVoter.fixedIdentString.isPresent()) {
            String fixedLabelVote =
                footerValue.substring(0, voterIdentStart)
                    + ": "
                    + fixedVoter.fixedIdentString.get();
            addFooter(footerLinesBuilder, footerKey, fixedLabelVote);
            anyFootersFixed = true;
            continue;
          }
        }

      } else if (footerKey.equalsIgnoreCase(FOOTER_ATTENTION.getName())) {
        AttentionStatusInNoteDb originalAttentionSetUpdate =
            gson.fromJson(footerValue, AttentionStatusInNoteDb.class);
        FixIdentResult fixedAttentionAccount =
            getFixedIdentString(fixProgress, originalAttentionSetUpdate.personIdent);
        Optional<String> fixedReason = fixAttentionSetReason(originalAttentionSetUpdate.reason);
        if (fixedAttentionAccount.fixedIdentString.isPresent() || fixedReason.isPresent()) {
          AttentionStatusInNoteDb fixedAttentionSetUpdate =
              new AttentionStatusInNoteDb(
                  fixedAttentionAccount.fixedIdentString.isPresent()
                      ? fixedAttentionAccount.fixedIdentString.get()
                      : originalAttentionSetUpdate.personIdent,
                  originalAttentionSetUpdate.operation,
                  fixedReason.isPresent() ? fixedReason.get() : originalAttentionSetUpdate.reason);
          addFooter(footerLinesBuilder, footerKey, gson.toJson(fixedAttentionSetUpdate));
          anyFootersFixed = true;
          continue;
        }
      }
      addFooter(footerLinesBuilder, footerKey, footerValue);
    }
    // Some of the old commits are missing corresponding footers but still have change messages that
    // need the fix. For such cases, try to guess or replace with the default string (see
    // getPossibleAccountReplacement)
    if (!fixedChangeMessage.isPresent()) {
      fixedChangeMessage = fixReviewerChangeMessage(originalChangeMessage);
    }
    if (!fixedChangeMessage.isPresent()) {
      fixedChangeMessage =
          fixRemoveVoteChangeMessage(fixProgress, Optional.empty(), originalChangeMessage);
    }
    if (!fixedChangeMessage.isPresent()) {
      fixedChangeMessage =
          fixAssigneeChangeMessage(
              fixProgress, Optional.empty(), Optional.empty(), originalChangeMessage);
    }
    if (!fixedChangeMessage.isPresent()) {
      fixedChangeMessage = fixSubmitChangeMessage(originalChangeMessage);
    }
    if (!fixedChangeMessage.isPresent()) {
      fixedChangeMessage = fixDeleteChangeMessageCommitMessage(originalChangeMessage);
    }
    if (!fixedChangeMessage.isPresent()) {
      fixedChangeMessage =
          fixCodeOwnersOnReviewChangeMessage(fixProgress.updateAuthorId, originalChangeMessage);
    }
    if (!fixedChangeMessage.isPresent()) {
      fixedChangeMessage =
          fixCodeOwnersOnAddReviewerChangeMessage(fixProgress, originalChangeMessage);
    }
    if (!anyFootersFixed && !fixedChangeMessage.isPresent()) {
      return Optional.empty();
    }
    StringBuilder fixedCommitBuilder = new StringBuilder();
    fixedCommitBuilder.append(changeSubject);
    fixedCommitBuilder.append("\n\n");
    if (commitMessageRange.get().hasChangeMessage()) {
      fixedCommitBuilder.append(fixedChangeMessage.orElse(originalChangeMessage));
      fixedCommitBuilder.append("\n\n");
    }
    fixedCommitBuilder.append(footerLinesBuilder);
    return Optional.of(fixedCommitBuilder.toString());
  }

  private static StringBuilder addFooter(StringBuilder sb, String footer, String value) {
    if (value == null) {
      return sb;
    }
    sb.append(footer).append(":");
    sb.append(" ").append(value);
    sb.append('\n');
    return sb;
  }

  private Optional<Account.Id> parseIdent(ChangeFixProgress changeFixProgress, PersonIdent ident) {
    Optional<Account.Id> account = NoteDbUtil.parseIdent(ident);
    if (account.isPresent()) {
      changeFixProgress.parsedAccounts.putIfAbsent(account.get(), "");
    } else {
      logger.atWarning().log("Failed to parse id %s", ident);
    }
    return account;
  }

  /**
   * Fixes {@code originalIdent} so it does not contain user data, see {@link
   * ChangeNoteUtil#getAccountIdAsUsername}.
   */
  private PersonIdent getFixedIdent(PersonIdent originalIdent, Account.Id identAccount) {
    return new PersonIdent(
        ChangeNoteUtil.getAccountIdAsUsername(identAccount),
        originalIdent.getEmailAddress(),
        originalIdent.getWhen(),
        originalIdent.getTimeZone());
  }

  /**
   * Parses {@code originalIdentString} and applies the fix, so it does not contain user data, see
   * {@link ChangeNoteUtil#appendAccountIdIdentString}.
   *
   * @param changeFixProgress see {@link ChangeFixProgress}
   * @param originalIdentString ident to apply the fix to.
   * @return {@link FixIdentResult}, with {@link FixIdentResult#accountId} parsed from {@code
   *     originalIdentString} and {@link FixIdentResult#fixedIdentString} if the fix was applied.
   * @throws ConfigInvalidException if could not parse {@link FixIdentResult#accountId} from {@code
   *     originalIdentString}
   */
  private FixIdentResult getFixedIdentString(
      ChangeFixProgress changeFixProgress, String originalIdentString)
      throws ConfigInvalidException {
    FixIdentResult fixIdentResult = new FixIdentResult();
    PersonIdent originalIdent = RawParseUtils.parsePersonIdent(originalIdentString);
    // Ident as String is saved in NoteDB footers, if this fails to parse, something is
    // wrong with the change and we better not touch it.
    fixIdentResult.accountId =
        parseIdent(changeFixProgress, originalIdent)
            .orElseThrow(
                () -> new ConfigInvalidException("field to parse id: " + originalIdentString));
    String fixedIdentString =
        ChangeNoteUtil.formatAccountIdentString(
            fixIdentResult.accountId, originalIdent.getEmailAddress());
    fixIdentResult.fixedIdentString =
        fixedIdentString.equals(originalIdentString)
            ? Optional.empty()
            : Optional.of(fixedIdentString);
    return fixIdentResult;
  }

  /** Extracts {@link Account#getName} from {@link Account#getNameEmail} */
  private String getNameFromNameEmail(String nameEmail) {
    Matcher nameEmailMatcher = NAME_EMAIL_PATTERN.matcher(nameEmail);
    return nameEmailMatcher.matches() ? nameEmailMatcher.group(1) : nameEmail;
  }

  /**
   * Returns replacement for {@code accountName}.
   *
   * <p>If {@code account} is known, replace with {@link AccountTemplateUtil#getAccountTemplate}.
   * Otherwise, try to guess the correct replacement account for {@code accountName} among {@link
   * ChangeFixProgress#parsedAccounts} that appeared in the change. If this fails {@link
   * #DEFAULT_ACCOUNT_REPLACEMENT} is applied.
   *
   * @param changeFixProgress see {@link ChangeFixProgress}
   * @param account account that should be used for replacement, if known
   * @param accountName {@link Account#getName} to replace.
   * @return replacement for {@code accountName}
   */
  private String getPossibleAccountReplacement(
      ChangeFixProgress changeFixProgress, Optional<Account.Id> account, String accountName) {
    if (account.isPresent()) {
      return AccountTemplateUtil.getAccountTemplate(account.get());
    }
    // Retrieve reviewer accounts from cache and try to match by their name.
    Map<Account.Id, AccountState> missingUserNameReviewers =
        accountCache.get(
            changeFixProgress.parsedAccounts.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(ImmutableSet.toImmutableSet()));
    changeFixProgress.parsedAccounts.putAll(
        missingUserNameReviewers.entrySet().stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    Map.Entry::getKey, e -> e.getValue().account().getName())));
    Set<Account.Id> possibleReplacements =
        changeFixProgress.parsedAccounts.entrySet().stream()
            .filter(e -> e.getValue().equals(accountName))
            .map(Entry::getKey)
            .collect(ImmutableSet.toImmutableSet());
    String replacementName = DEFAULT_ACCOUNT_REPLACEMENT;
    if (possibleReplacements.isEmpty()) {
      logger.atWarning().log("Could not find reviewer account matching name %s", accountName);
    } else if (possibleReplacements.size() > 1) {
      logger.atWarning().log("Found multiple reviewer account matching name %s", accountName);
    } else {
      replacementName =
          AccountTemplateUtil.getAccountTemplate(Iterables.getOnlyElement(possibleReplacements));
    }
    return replacementName;
  }

  /**
   * Cuts tree and parent lines from raw unparsed commit body, so they are not included in diff
   * comparison.
   *
   * @param b raw unparsed commit body, see {@link RevCommit#getRawBuffer()}.
   *     <p>For parsing, see {@link RawParseUtils#author}, {@link RawParseUtils#commitMessage}, etc.
   * @return raw unparsed commit body, without tree and parent lines.
   */
  public static byte[] cutTreeAndParents(byte[] b) {
    final int sz = b.length;
    int ptr = 46; // skip the "tree ..." line.
    while (ptr < sz && b[ptr] == 'p') {
      ptr += 48;
    } // skip this parent.
    return Arrays.copyOfRange(b, ptr, b.length + 1);
  }

  private String computeDiff(byte[] oldCommit, byte[] newCommit) throws IOException {
    RawText oldBody = new RawText(cutTreeAndParents(oldCommit));
    RawText newBody = new RawText(cutTreeAndParents(newCommit));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    EditList diff = diffAlgorithm.diff(RawTextComparator.DEFAULT, oldBody, newBody);
    try (DiffFormatter fmt = new DiffFormatter(out)) {
      // Do not show any unchanged lines, since it is not interesting
      fmt.setContext(0);
      fmt.format(diff, oldBody, newBody);
      fmt.flush();
      return out.toString();
    }
  }

  private static ObjectInserter newPackInserter(Repository repo) {
    if (!(repo instanceof FileRepository)) {
      return repo.newObjectInserter();
    }
    PackInserter ins = ((FileRepository) repo).getObjectDatabase().newPackInserter();
    ins.checkExisting(false);
    return ins;
  }

  /**
   * Parsed and fixed {@link PersonIdent} string, formatted as {@link
   * ChangeNoteUtil#appendAccountIdIdentString}
   */
  private static class FixIdentResult {

    /** {@link com.google.gerrit.entities.Account.Id} parsed from PersonIdent string. */
    Account.Id accountId;
    /**
     * Fixed ident string, that does not contain user data, or {@link Optional#empty} if fix was not
     * required.
     */
    Optional<String> fixedIdentString;
  }

  /**
   * Holds the state of change rewrite progress. Rewrite goes from the oldest commit to the most
   * recent update.
   */
  private static class ChangeFixProgress {
    /** Assignee at current commit update. */
    Account.Id assigneeId = null;

    /** Author of the current commit update. */
    Optional<Account.Id> updateAuthorId = null;

    /**
     * Accounts parsed so far together with their {@link Account#getName} extracted from {@link
     * #accountCache} if needed by rewrite. Maps to empty string if was not requested from cache
     * yet.
     */
    Map<Account.Id, String> parsedAccounts = new HashMap<>();

    /** Id of the current commit in rewriter walk. */
    ObjectId newTipId = null;
    /** If any commits were rewritten by the rewriter. */
    boolean anyFixesApplied = false;

    /**
     * Whether all commits seen by the rewriter with the fixes applied passed the verification, see
     * {@link #verifyCommit}.
     */
    boolean isValidAfterFix = true;

    List<CommitDiff> commitDiffs = new ArrayList<>();
  }
}
