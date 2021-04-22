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

import static com.google.gerrit.entities.ChangeMessage.ACCOUNT_TEMPLATE_REGEX;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ASSIGNEE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ATTENTION;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_REAL_USER;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TAG;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
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
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.notedb.ChangeNoteUtil.CommitMessageRange;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.PackInserter;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
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
 */
@UsedAt(UsedAt.Project.GOOGLE)
@Singleton
public class CommitRewriter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Result of the backfill run for a project. */
  public static class BackfillResult {

    /** If the run for the project was successful. */
    public boolean ok;
    /** In refs that were fixed by the run/ would be fixed if in --dry-run */
    public List<String> refsFixed = new ArrayList<>();

    /**
     * Refs that still contain user data after the backfill run. Only filled if --verify-commits,
     * see {@link #verifyCommit}
     */
    public List<String> refsStillInvalidAfterFix = new ArrayList<>();

    /** Refs, failed to backfill by the run. */
    public List<String> refsFailedToFix = new ArrayList<>();
  }

  private final ChangeNotes.Factory changeNotesFactory;
  private final AccountCache accountCache;

  @Inject
  public CommitRewriter(ChangeNotes.Factory changeNotesFactory, AccountCache accountCache) {
    this.changeNotesFactory = changeNotesFactory;
    this.accountCache = accountCache;
  }

  /**
   * Rewrites commit history of {@link RefNames#changeMetaRef}s in single {@code repo}. Only
   * rewrites branch if necessary, i.e. if there were any commits that contained user data.
   *
   * <p>Optionally, verifies that commits do not contain user data for the accounts, that are linked
   * to a change, see {@link #verifyCommit}, {@link #collectAccounts}.
   *
   * @param project project to backfill
   * @param repo repo to backfill
   * @param dryRun whether to rewrite the commit history or only find refs that need to be fixed.
   * @param verifyCommits whether to verify that resulting commits contain user data
   * @return BackfillResult
   */
  public BackfillResult backfillProject(
      Project.NameKey project, Repository repo, boolean dryRun, boolean verifyCommits) {
    BackfillResult result = new BackfillResult();
    result.ok = true;
    try (RevWalk revWalk = new RevWalk(repo);
        ObjectInserter ins = newPackInserter(repo)) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      bru.setAllowNonFastForwards(true);
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
        Change.Id changeId = Change.Id.fromRef(ref.getName());
        if (changeId == null || !ref.getName().equals(RefNames.changeMetaRef(changeId))) {
          continue;
        }

        ChangeNotes changeNotes = changeNotesFactory.create(project, changeId);
        ImmutableSet<AccountState> accountsInChange =
            verifyCommits ? collectAccounts(changeNotes) : ImmutableSet.of();
        try {
          ChangeFixProgress changeFixProgress =
              backfillChange(revWalk, ins, ref, accountsInChange, verifyCommits);
          if (changeFixProgress.anyFixesApplied) {
            bru.addCommand(
                new ReceiveCommand(ref.getObjectId(), changeFixProgress.newTipId, ref.getName()));
            result.refsFixed.add(ref.getName());
          }
          if (!changeFixProgress.isValid) {
            result.refsStillInvalidAfterFix.add(ref.getName());
          }
        } catch (ConfigInvalidException | IOException e) {
          result.refsFailedToFix.add(ref.getName());
        }
      }

      if (!bru.getCommands().isEmpty()) {
        if (!dryRun) {
          ins.flush();
          RefUpdateUtil.executeChecked(bru, revWalk);
        }
      }
    } catch (IOException e) {
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
      accounts.add(patchSetApproval.accountId());
      accounts.add(patchSetApproval.realAccountId());
    }
    accounts.addAll(changeNotes.getAllPastReviewers());
    accounts.addAll(changeNotes.getPastAssignees());
    changeNotes
        .getAttentionSetUpdates()
        .forEach(attentionSetUpdate -> accounts.add(attentionSetUpdate.account()));
    for (SubmitRecord submitRecord : changeNotes.getSubmitRecords()) {
      accounts.addAll(
          submitRecord.labels.stream()
              .map(label -> label.appliedBy)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet()));
    }
    for (HumanComment comment : changeNotes.getHumanComments().values()) {
      accounts.add(comment.author.getId());
      accounts.add(comment.getRealAuthor().getId());
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
      boolean verifyCommits)
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

      PersonIdent fixedAuthorIdent = getFixedIdent(originalCommit.getAuthorIdent());
      Optional<String> fixedCommitMessage = fixedCommitMessage(originalCommit, changeFixProgress);
      String commitMessage =
          fixedCommitMessage.isPresent()
              ? fixedCommitMessage.get()
              : originalCommit.getFullMessage();
      if (verifyCommits) {
        changeFixProgress.isValid &=
            verifyCommit(commitMessage, fixedAuthorIdent, accountsInChange);
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
      changeFixProgress.newTipId = inserter.insert(cb);
    }
    return changeFixProgress;
  }

  private Optional<String> fixAssigneeChangeMessage(
      Account.Id oldAssignee, Account.Id newAssignee, String originalChangeMessage) {
    if (Strings.isNullOrEmpty(originalChangeMessage)) {
      return Optional.empty();
    }
    Pattern assigneeDeletedPattern = Pattern.compile("Assignee deleted: (.*)");
    Matcher assigneeDeletedMatcher = assigneeDeletedPattern.matcher(originalChangeMessage);
    if (assigneeDeletedMatcher.matches()) {
      if (!assigneeDeletedMatcher.group(1).matches(ACCOUNT_TEMPLATE_REGEX)) {
        return Optional.of(
            "Assignee deleted: " + ChangeMessagesUtil.getAccountTemplate(oldAssignee));
      }
      return Optional.empty();
    }
    Pattern assigneeAddedPattern = Pattern.compile("Assignee added: (.*)");
    Matcher assigneeAddedMatcher = assigneeAddedPattern.matcher(originalChangeMessage);
    if (assigneeAddedMatcher.matches()) {
      if (!assigneeAddedMatcher.group(1).matches(ACCOUNT_TEMPLATE_REGEX)) {
        return Optional.of("Assignee added: " + ChangeMessagesUtil.getAccountTemplate(newAssignee));
      }
      return Optional.empty();
    }
    Pattern assigneeChangedPattern = Pattern.compile("Assignee changed from: (.*) to: (.*)");
    Matcher assigneeChangedMatcher = assigneeChangedPattern.matcher(originalChangeMessage);
    if (assigneeChangedMatcher.matches()) {
      if (!assigneeChangedMatcher.group(1).matches(ACCOUNT_TEMPLATE_REGEX)) {
        return Optional.of(
            String.format(
                "Assignee changed from: %s to: %s",
                ChangeMessagesUtil.getAccountTemplate(oldAssignee),
                ChangeMessagesUtil.getAccountTemplate(newAssignee)));
      }
      return Optional.empty();
    }
    return Optional.empty();
  }

  private Optional<String> fixReviewerChangeMessage(String originalChangeMessage) {
    if (Strings.isNullOrEmpty(originalChangeMessage)) {
      return Optional.empty();
    }
    Pattern removedReviewer = Pattern.compile("Removed (cc|reviewer) (.*) .*");
    Matcher matcher = removedReviewer.matcher(originalChangeMessage);
    if (matcher.matches() && !matcher.group(2).matches(ACCOUNT_TEMPLATE_REGEX)) {
      // Since we do not use change messages for reviewer updates on UI, it does not matter what we
      // rewrite it to.
      return Optional.of(originalChangeMessage.substring(0, matcher.end(2)));
    }
    return Optional.empty();
  }

  private Optional<String> fixRemoveVoteChangeMessage(
      Account.Id reviewer, String originalChangeMessage) {
    if (Strings.isNullOrEmpty(originalChangeMessage)) {
      return Optional.empty();
    }
    Pattern removedVotePattern = Pattern.compile("Removed (.*) by (.*)");
    Matcher matcher = removedVotePattern.matcher(originalChangeMessage);
    if (matcher.matches() && !matcher.group(2).matches(ACCOUNT_TEMPLATE_REGEX)) {
      return Optional.of(
          String.format(
              "Removed %s by %s",
              matcher.group(1), ChangeMessagesUtil.getAccountTemplate(reviewer)));
    }
    return Optional.empty();
  }

  private Optional<String> fixDeleteChangeMessageMessage(String originalChangeMessage) {
    if (Strings.isNullOrEmpty(originalChangeMessage)) {
      return Optional.empty();
    }
    Pattern removedChangeMessage = Pattern.compile("Change message removed by (.*)(\nReason: %s)+");
    Matcher matcher = removedChangeMessage.matcher(originalChangeMessage);
    if (matcher.matches() && !matcher.group(1).matches(ACCOUNT_TEMPLATE_REGEX)) {
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
    Pattern submittedPattern = Pattern.compile("Change has been successfully (.*) by (.*)");
    Matcher matcher = submittedPattern.matcher(originalChangeMessage);
    if (matcher.matches()) {
      // See https://gerrit-review.googlesource.com/c/gerrit/+/272654
      Optional.of(originalChangeMessage.substring(0, matcher.end(1)));
    }
    return Optional.empty();
  }

  private Optional<String> fixCodeOwnersChangeMessage(String originalMessage) {
    // TODO
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
      if (footerKey.equals(FOOTER_TAG.getName())) {
        if (footerValue.equals(ChangeMessagesUtil.TAG_MERGED)) {
          fixedChangeMessage = fixSubmitChangeMessage(originalChangeMessage);
        }
      } else if (footerKey.equalsIgnoreCase(FOOTER_ASSIGNEE.getName())) {
        Account.Id oldAssignee = fixProgress.assigneeId;
        FixIdentResult fixedAssignee = null;
        if (footerValue.equals("")) {
          fixProgress.assigneeId = null;
        } else {
          fixedAssignee = getFixedIdentString(footerValue);
          fixProgress.assigneeId = fixedAssignee.accountId;
        }
        fixedChangeMessage =
            fixAssigneeChangeMessage(oldAssignee, fixProgress.assigneeId, originalChangeMessage);
        if (fixedAssignee != null && fixedAssignee.fixedIdentString.isPresent()) {
          addFooter(footerLinesBuilder, footerKey, fixedAssignee.fixedIdentString.get());
          anyFootersFixed = true;
          continue;
        }
      } else if (Arrays.stream(ReviewerStateInternal.values())
          .filter(state -> footerKey.equalsIgnoreCase(state.getFooterKey().getName()))
          .findAny()
          .isPresent()) {
        fixedChangeMessage = fixReviewerChangeMessage(originalChangeMessage);
        FixIdentResult fixedReviewer = getFixedIdentString(footerValue);
        if (fixedReviewer.fixedIdentString.isPresent()) {
          addFooter(footerLinesBuilder, footerKey, fixedReviewer.fixedIdentString.get());
          anyFootersFixed = true;
          continue;
        }
      } else if (footerKey.equalsIgnoreCase(FOOTER_REAL_USER.getName())) {
        FixIdentResult fixedRealUser = getFixedIdentString(footerValue);
        if (fixedRealUser.fixedIdentString.isPresent()) {
          addFooter(footerLinesBuilder, footerKey, fixedRealUser.fixedIdentString.get());
          anyFootersFixed = true;
          continue;
        }
      } else if (footerKey.equalsIgnoreCase(FOOTER_LABEL.getName())) {
        int voterIdentStart = footerValue.indexOf(' ');
        if (voterIdentStart <= 0) {
          continue;
        }
        String originalIdentString = footerValue.substring(voterIdentStart + 1);
        FixIdentResult fixedVoter = getFixedIdentString(originalIdentString);
        fixedChangeMessage =
            fixRemoveVoteChangeMessage(fixedVoter.accountId, originalChangeMessage);
        if (fixedVoter.fixedIdentString.isPresent()) {
          String fixedLabelVote =
              footerValue.substring(0, voterIdentStart) + " " + fixedVoter.fixedIdentString.get();
          addFooter(footerLinesBuilder, footerKey, fixedLabelVote);
          anyFootersFixed = true;
          continue;
        }
      } else if (footerKey.equalsIgnoreCase(FOOTER_SUBMITTED_WITH.getName())) {
        // TODO

      } else if (footerKey.equalsIgnoreCase(FOOTER_ATTENTION.getName())) {
        // TODO

      }
      addFooter(footerLinesBuilder, footerKey, footerValue);
    }

    if (!fixedChangeMessage.isPresent()) {
      fixedChangeMessage = fixDeleteChangeMessageMessage(originalChangeMessage);
    }
    if (!fixedChangeMessage.isPresent()) {
      fixedChangeMessage = fixCodeOwnersChangeMessage(originalChangeMessage);
    }
    if (!anyFootersFixed && !fixedChangeMessage.isPresent()) {
      return Optional.empty();
    }
    StringBuilder fixedCommitBuilder = new StringBuilder();
    fixedCommitBuilder.append(changeSubject);
    fixedCommitBuilder.append("\n\n");
    if (commitMessageRange.get().hasChangeMessage()) {
      fixedCommitBuilder.append(
          fixedChangeMessage.isPresent() ? fixedChangeMessage.get() : originalChangeMessage);
      fixedCommitBuilder.append("\n\n");
    }
    fixedCommitBuilder.append(footerLinesBuilder);
    return Optional.of(fixedCommitBuilder.toString());
  }

  private static StringBuilder addFooter(StringBuilder sb, String footer, String value) {
    return sb.append(footer).append(": ").append(value).append('\n');
  }

  private Account.Id parseIdent(PersonIdent ident) throws ConfigInvalidException {
    return NoteDbUtil.parseIdent(ident)
        .orElseThrow(
            () -> new ConfigInvalidException("field to parse id: " + ident.getEmailAddress()));
  }

  /**
   * Fixes {@code originalIdent} so it does not contain user data, see {@link
   * ChangeNoteUtil#getAccountIdAsUsername}.
   */
  private PersonIdent getFixedIdent(PersonIdent originalIdent) throws ConfigInvalidException {
    Account.Id account = parseIdent(originalIdent);
    return new PersonIdent(
        ChangeNoteUtil.getAccountIdAsUsername(account),
        originalIdent.getEmailAddress(),
        originalIdent.getWhen(),
        originalIdent.getTimeZone());
  }

  /**
   * Parses {@code originalIdentString} and applies the fix, so it does not contain user data, see
   * {@link ChangeNoteUtil#appendAccountIdIdentString}.
   *
   * @param originalIdentString ident to apply the fix to.
   * @return {@link FixIdentResult}, with {@link FixIdentResult#accountId} parsed from {@code
   *     originalIdentString} and {@link FixIdentResult#fixedIdentString} if the fix was applied.
   * @throws ConfigInvalidException if could not parse {@link FixIdentResult#accountId} from {@code
   *     originalIdentString}
   */
  private FixIdentResult getFixedIdentString(String originalIdentString)
      throws ConfigInvalidException {
    FixIdentResult fixIdentResult = new FixIdentResult();
    PersonIdent originalIdent = RawParseUtils.parsePersonIdent(originalIdentString);
    fixIdentResult.accountId = parseIdent(originalIdent);
    String fixedIdentString =
        ChangeNoteUtil.formatAccountIdentString(
            fixIdentResult.accountId, originalIdent.getEmailAddress());
    fixIdentResult.fixedIdentString =
        fixedIdentString.equals(originalIdentString)
            ? Optional.empty()
            : Optional.of(fixedIdentString);
    return fixIdentResult;
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

    /** {@link Account.Id} parsed from PersonIdent string. */
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
    /** Id of the current commit in rewriter walk. */
    ObjectId newTipId = null;
    /** If any commits were rewritten by the rewriter. */
    boolean anyFixesApplied = false;

    /** If all commits, seen by the rewriter passed the verification, see {@link #verifyCommit}. */
    boolean isValid = true;
  }
}
