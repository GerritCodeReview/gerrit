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

package com.google.gerrit.server.approval;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.change.LabelNormalizer;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.gerrit.server.query.approval.UserInPredicate;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Utility functions to manipulate patchset approvals.
 *
 * <p>Approvals are overloaded, they represent both approvals and reviewers which should be CCed on
 * a change. To ensure that reviewers are not lost there must always be an approval on each patchset
 * for each reviewer, even if the reviewer hasn't actually given a score to the change. To mark the
 * "no score" case, a dummy approval, which may live in any of the available categories, with a
 * score of 0 is used.
 */
@Singleton
public class ApprovalsUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static PatchSetApproval.Builder newApproval(
      PatchSet.Id psId, CurrentUser user, LabelId labelId, int value, Instant when) {
    PatchSetApproval.Builder b =
        PatchSetApproval.builder()
            .key(PatchSetApproval.key(psId, user.getAccountId(), labelId))
            .value(value)
            .granted(when);
    user.updateRealAccountId(b::realAccountId);
    return b;
  }

  private static Iterable<PatchSetApproval> filterApprovals(
      Iterable<PatchSetApproval> psas, Account.Id accountId) {
    return Iterables.filter(psas, a -> Objects.equals(a.accountId(), accountId));
  }

  private final AccountCache accountCache;
  private final String anonymousCowardName;
  private final ApprovalCopier approvalCopier;
  private final Provider<ApprovalQueryBuilder> approvalQueryBuilderProvider;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final LabelNormalizer labelNormalizer;
  private final OneOffRequestContext requestContext;

  @VisibleForTesting
  @Inject
  public ApprovalsUtil(
      AccountCache accountCache,
      @AnonymousCowardName String anonymousCowardName,
      ApprovalCopier approvalCopier,
      Provider<ApprovalQueryBuilder> approvalQueryBuilderProvider,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      LabelNormalizer labelNormalizer,
      OneOffRequestContext requestContext) {
    this.accountCache = accountCache;
    this.anonymousCowardName = anonymousCowardName;
    this.approvalCopier = approvalCopier;
    this.approvalQueryBuilderProvider = approvalQueryBuilderProvider;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.labelNormalizer = labelNormalizer;
    this.requestContext = requestContext;
  }

  /**
   * Get all reviewers for a change.
   *
   * @param notes change notes.
   * @return reviewers for the change.
   */
  public ReviewerSet getReviewers(ChangeNotes notes) {
    return notes.load().getReviewers();
  }

  /**
   * Get updates to reviewer set.
   *
   * @param notes change notes.
   * @return reviewer updates for the change.
   */
  public List<ReviewerStatusUpdate> getReviewerUpdates(ChangeNotes notes) {
    return notes.load().getReviewerUpdates();
  }

  public List<PatchSetApproval> addReviewers(
      ChangeUpdate update,
      LabelTypes labelTypes,
      Change change,
      PatchSet ps,
      PatchSetInfo info,
      Iterable<Account.Id> wantReviewers,
      Collection<Account.Id> existingReviewers) {
    return addReviewers(
        update,
        labelTypes,
        change,
        ps.id(),
        info.getAuthor().getAccount(),
        info.getCommitter().getAccount(),
        wantReviewers,
        existingReviewers);
  }

  public List<PatchSetApproval> addReviewers(
      ChangeNotes notes,
      ChangeUpdate update,
      LabelTypes labelTypes,
      Change change,
      Iterable<Account.Id> wantReviewers) {
    PatchSet.Id psId = change.currentPatchSetId();
    Collection<Account.Id> existingReviewers;
    existingReviewers = notes.load().getReviewers().byState(REVIEWER);
    // Existing reviewers should include pending additions in the REVIEWER
    // state, taken from ChangeUpdate.
    existingReviewers = Lists.newArrayList(existingReviewers);
    for (Map.Entry<Account.Id, ReviewerStateInternal> entry : update.getReviewers().entrySet()) {
      if (entry.getValue() == REVIEWER) {
        existingReviewers.add(entry.getKey());
      }
    }
    return addReviewers(
        update, labelTypes, change, psId, null, null, wantReviewers, existingReviewers);
  }

  private List<PatchSetApproval> addReviewers(
      ChangeUpdate update,
      LabelTypes labelTypes,
      Change change,
      PatchSet.Id psId,
      Account.Id authorId,
      Account.Id committerId,
      Iterable<Account.Id> wantReviewers,
      Collection<Account.Id> existingReviewers) {
    List<LabelType> allTypes = labelTypes.getLabelTypes();
    if (allTypes.isEmpty()) {
      return ImmutableList.of();
    }

    Set<Account.Id> need = Sets.newLinkedHashSet(wantReviewers);
    if (authorId != null && canSee(update.getNotes(), authorId)) {
      need.add(authorId);
    }

    if (committerId != null && canSee(update.getNotes(), committerId)) {
      need.add(committerId);
    }
    need.remove(change.getOwner());
    need.removeAll(existingReviewers);
    if (need.isEmpty()) {
      return ImmutableList.of();
    }

    List<PatchSetApproval> cells = Lists.newArrayListWithCapacity(need.size());
    LabelId labelId = Iterables.getLast(allTypes).getLabelId();
    for (Account.Id account : need) {
      cells.add(
          PatchSetApproval.builder()
              .key(PatchSetApproval.key(psId, account, labelId))
              .value(0)
              .granted(update.getWhen())
              .build());
      update.putReviewer(account, REVIEWER);
    }
    return Collections.unmodifiableList(cells);
  }

  private boolean canSee(ChangeNotes notes, Account.Id accountId) {
    try {
      if (!projectCache
          .get(notes.getProjectName())
          .orElseThrow(illegalState(notes.getProjectName()))
          .statePermitsRead()) {
        return false;
      }
      return permissionBackend.absentUser(accountId).change(notes).test(ChangePermission.READ);
    } catch (PermissionBackendException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check if account %d can see change %d",
          accountId.get(), notes.getChangeId().get());
      return false;
    }
  }

  /**
   * Adds accounts to a change as reviewers in the CC state.
   *
   * @param notes change notes.
   * @param update change update.
   * @param wantCCs accounts to CC.
   * @param keepExistingReviewers whether provided accounts that are already reviewer should be kept
   *     as reviewer or be downgraded to CC
   * @return whether a change was made.
   */
  public Collection<Account.Id> addCcs(
      ChangeNotes notes,
      ChangeUpdate update,
      Collection<Account.Id> wantCCs,
      boolean keepExistingReviewers) {
    return addCcs(update, wantCCs, notes.load().getReviewers(), keepExistingReviewers);
  }

  private Collection<Account.Id> addCcs(
      ChangeUpdate update,
      Collection<Account.Id> wantCCs,
      ReviewerSet existingReviewers,
      boolean keepExistingReviewers) {
    Set<Account.Id> need = new LinkedHashSet<>(wantCCs);
    need.removeAll(existingReviewers.byState(CC));
    if (keepExistingReviewers) {
      need.removeAll(existingReviewers.byState(REVIEWER));
    }
    need.removeAll(update.getReviewers().keySet());
    for (Account.Id account : need) {
      update.putReviewer(account, CC);
    }
    return need;
  }

  /**
   * Adds approvals to ChangeUpdate for a new patch set, and writes to NoteDb.
   *
   * @param update change update.
   * @param labelTypes label types for the containing project.
   * @param ps patch set being approved.
   * @param user user adding approvals.
   * @param approvals approvals to add.
   */
  public Iterable<PatchSetApproval> addApprovalsForNewPatchSet(
      ChangeUpdate update,
      LabelTypes labelTypes,
      PatchSet ps,
      CurrentUser user,
      Map<String, Short> approvals)
      throws RestApiException, PermissionBackendException {
    Account.Id accountId = user.getAccountId();
    checkArgument(
        accountId.equals(ps.uploader()),
        "expected user %s to match patch set uploader %s",
        accountId,
        ps.uploader());
    if (approvals.isEmpty()) {
      return ImmutableList.of();
    }
    checkApprovals(approvals, permissionBackend.user(user).change(update.getNotes()));
    List<PatchSetApproval> cells = new ArrayList<>(approvals.size());
    Instant ts = update.getWhen();
    for (Map.Entry<String, Short> vote : approvals.entrySet()) {
      Optional<LabelType> lt = labelTypes.byLabel(vote.getKey());
      if (!lt.isPresent()) {
        throw new BadRequestException(
            String.format("label \"%s\" is not a configured label", vote.getKey()));
      }
      cells.add(newApproval(ps.id(), user, lt.get().getLabelId(), vote.getValue(), ts).build());
    }
    for (PatchSetApproval psa : cells) {
      update.putApproval(psa.label(), psa.value());
    }
    return cells;
  }

  public static void checkLabel(LabelTypes labelTypes, String name, Short value)
      throws BadRequestException {
    Optional<LabelType> label = labelTypes.byLabel(name);
    if (!label.isPresent()) {
      throw new BadRequestException(String.format("label \"%s\" is not a configured label", name));
    }
    if (label.get().getValue(value) == null) {
      throw new BadRequestException(
          String.format("label \"%s\": %d is not a valid value", name, value));
    }
  }

  private static void checkApprovals(
      Map<String, Short> approvals, PermissionBackend.ForChange forChange)
      throws AuthException, PermissionBackendException {
    for (Map.Entry<String, Short> vote : approvals.entrySet()) {
      String name = vote.getKey();
      Short value = vote.getValue();
      if (!forChange.test(new LabelPermission.WithValue(name, value))) {
        throw new AuthException(
            String.format("applying label \"%s\": %d is restricted", name, value));
      }
    }
  }

  public ListMultimap<PatchSet.Id, PatchSetApproval> byChangeExcludingCopiedApprovals(
      ChangeNotes notes) {
    return notes.load().getApprovals().onlyNonCopied();
  }

  /**
   * Copies approvals to a new patch set.
   *
   * <p>Computes the approvals of the prior patch set that should be copied to the new patch set and
   * stores them in NoteDb.
   *
   * <p>For outdated approvals (approvals on the prior patch set which are outdated by the new patch
   * set and hence not copied) the approvers are added to the attention set since they need to
   * re-review the change and renew their approvals.
   *
   * @param notes the change notes
   * @param patchSet the newly created patch set
   * @param revWalk {@link RevWalk} that can see the new patch set revision
   * @param repoConfig the repo config
   * @param changeUpdate changeUpdate that is used to persist the copied approvals and update the
   *     attention set
   * @return the result of the approval copying
   */
  public ApprovalCopier.Result copyApprovalsToNewPatchSet(
      ChangeNotes notes,
      PatchSet patchSet,
      RevWalk revWalk,
      Config repoConfig,
      ChangeUpdate changeUpdate) {
    ApprovalCopier.Result approvalCopierResult =
        approvalCopier.forPatchSet(notes, patchSet, revWalk, repoConfig);
    approvalCopierResult
        .copiedApprovals()
        .forEach(approvalData -> changeUpdate.putCopiedApproval(approvalData.patchSetApproval()));

    if (!notes.getChange().isWorkInProgress()) {
      // The attention set should not be updated when the change is work-in-progress.
      addAttentionSetUpdatesForOutdatedApprovals(
          changeUpdate,
          approvalCopierResult.outdatedApprovals().stream()
              .map(ApprovalCopier.Result.ApprovalData::patchSetApproval)
              .collect(toImmutableSet()));
    }

    return approvalCopierResult;
  }

  private void addAttentionSetUpdatesForOutdatedApprovals(
      ChangeUpdate changeUpdate, ImmutableSet<PatchSetApproval> outdatedApprovals) {
    Set<AttentionSetUpdate> updates = new HashSet<>();

    Multimap<Account.Id, PatchSetApproval> outdatedApprovalsByUser = ArrayListMultimap.create();
    outdatedApprovals.forEach(psa -> outdatedApprovalsByUser.put(psa.accountId(), psa));
    for (Map.Entry<Account.Id, Collection<PatchSetApproval>> e :
        outdatedApprovalsByUser.asMap().entrySet()) {
      Account.Id approverId = e.getKey();
      Collection<PatchSetApproval> outdatedUserApprovals = e.getValue();

      String message;
      if (outdatedUserApprovals.size() == 1) {
        PatchSetApproval outdatedUserApproval = Iterables.getOnlyElement(outdatedUserApprovals);
        message =
            String.format(
                "Vote got outdated and was removed: %s",
                LabelVote.create(outdatedUserApproval.label(), outdatedUserApproval.value())
                    .format());
      } else {
        message =
            String.format(
                "Votes got outdated and were removed: %s",
                outdatedUserApprovals.stream()
                    .map(
                        outdatedUserApproval ->
                            LabelVote.create(
                                    outdatedUserApproval.label(), outdatedUserApproval.value())
                                .format())
                    .sorted()
                    .collect(joining(", ")));
      }

      updates.add(
          AttentionSetUpdate.createForWrite(approverId, AttentionSetUpdate.Operation.ADD, message));
    }
    changeUpdate.addToPlannedAttentionSetUpdates(updates);
  }

  public Optional<String> formatApprovalCopierResult(
      ApprovalCopier.Result approvalCopierResult, LabelTypes labelTypes) {
    requireNonNull(approvalCopierResult, "approvalCopierResult");
    requireNonNull(labelTypes, "labelTypes");

    if (approvalCopierResult.copiedApprovals().isEmpty()
        && approvalCopierResult.outdatedApprovals().isEmpty()) {
      return Optional.empty();
    }

    StringBuilder message = new StringBuilder();

    if (!approvalCopierResult.copiedApprovals().isEmpty()) {
      message.append("Copied Votes:\n");
      message.append(
          formatApprovalListWithCopyCondition(approvalCopierResult.copiedApprovals(), labelTypes));
    }
    if (!approvalCopierResult.outdatedApprovals().isEmpty()) {
      if (!approvalCopierResult.copiedApprovals().isEmpty()) {
        message.append("\n");
      }
      message.append("Outdated Votes:\n");
      message.append(
          formatApprovalListWithCopyCondition(
              approvalCopierResult.outdatedApprovals(), labelTypes));
    }

    return Optional.of(message.toString());
  }

  /**
   * Formats the given approvals as a bullet list, each approval with the corresponding copy
   * condition if available.
   *
   * <p>E.g.:
   *
   * <pre>
   * * Code-Review+1, Code-Review+2 (copy condition: "is:MIN")
   * * Verified+1 (copy condition: "is:MIN")
   * </pre>
   *
   * <p>Entries in the list can have the following formats:
   *
   * <ul>
   *   <li>{@code <comma-separated-list-of-approvals-for-the-same-label> (copy condition:
   *       "<copy-condition-without-UserInPredicate>")} (if a copy condition without UserInPredicate
   *       is present), e.g.: {@code Code-Review+1, Code-Review+2 (copy condition: "is:MIN")}
   *   <li>{@code <approval> by <comma-separated-list-of-approvers> (copy condition:
   *       "<copy-condition-with-UserInPredicate>")} (if a copy condition with UserInPredicate is
   *       present), e.g. {@code Code-Review+1 by <GERRIT_ACCOUNT_1000000>, <GERRIT_ACCOUNT_1000001>
   *       (copy condition: "approverin:7d9e2d5b561e75230e4463ae757ac5d6ff715d85")}
   *   <li>{@code <comma-separated-list-of-approval-for-the-same-label>} (if no copy condition is
   *       present), e.g.: {@code Code-Review+1, Code-Review+2}
   *   <li>{@code <comma-separated-list-of-approval-for-the-same-label> (label type is missing)} (if
   *       the label type is missing), e.g.: {@code Code-Review+1, Code-Review+2 (label type is
   *       missing)}
   *   <li>{@code <comma-separated-list-of-approval-for-the-same-label> (non-parseable copy
   *       condition: "<non-parseable copy-condition>")} (if a non-parseable copy condition is
   *       present), e.g.: {@code Code-Review+1, Code-Review+2 (non-parseable copy condition:
   *       "is:FOO")}
   * </ul>
   *
   * @param approvalDatas the approvals that should be formatted, with approval meta data
   * @param labelTypes the label types
   * @return bullet list with the formatted approvals
   */
  private String formatApprovalListWithCopyCondition(
      ImmutableSet<ApprovalCopier.Result.ApprovalData> approvalDatas, LabelTypes labelTypes) {
    StringBuilder message = new StringBuilder();

    // sort approvals by label vote so that we list them in a deterministic order
    ImmutableList<ApprovalCopier.Result.ApprovalData> approvalsSortedByLabelVote =
        approvalDatas.stream()
            .sorted(
                comparing(
                    approvalData ->
                        LabelVote.create(
                                approvalData.patchSetApproval().label(),
                                approvalData.patchSetApproval().value())
                            .format()))
            .collect(toImmutableList());

    ImmutableListMultimap<String, ApprovalCopier.Result.ApprovalData> approvalsByLabel =
        Multimaps.index(
            approvalsSortedByLabelVote, approvalData -> approvalData.patchSetApproval().label());

    for (Map.Entry<String, Collection<ApprovalCopier.Result.ApprovalData>> approvalsByLabelEntry :
        approvalsByLabel.asMap().entrySet()) {
      String label = approvalsByLabelEntry.getKey();
      Collection<ApprovalCopier.Result.ApprovalData> approvalsForSameLabel =
          approvalsByLabelEntry.getValue();

      if (!labelTypes.byLabel(label).isPresent()) {
        message
            .append("* ")
            .append(formatApprovalsAsLabelVotesList(approvalsForSameLabel))
            .append(" (label type is missing)\n");
        continue;
      }

      LabelType labelType = labelTypes.byLabel(label).get();
      if (!labelType.getCopyCondition().isPresent()) {
        message
            .append("* ")
            .append(formatApprovalsAsLabelVotesList(approvalsForSameLabel))
            .append("\n");
        continue;
      }

      // Group the approvals that have the same label by the passing atoms. If approvals have the
      // same label, but have different passing atoms, we need to list them in separate lines
      // (because in each line we will highlight different passing atoms that matched). Approvals
      // with the same label and the same passing atoms are formatted as a single line.
      ImmutableListMultimap<ImmutableSet<String>, ApprovalCopier.Result.ApprovalData>
          approvalsForSameLabelByPassingAndFailingAtoms =
              Multimaps.index(
                  approvalsForSameLabel, ApprovalCopier.Result.ApprovalData::passingAtoms);

      // Approvals with the same label that have the same passing atoms should have the same failing
      // atoms (since the label is the same they have the same copy condition).
      approvalsForSameLabelByPassingAndFailingAtoms
          .asMap()
          .values()
          .forEach(
              approvalsForSameLabelAndSamePassingAtoms ->
                  checkThatPropertyIsTheSameForAllApprovals(
                      approvalsForSameLabelAndSamePassingAtoms,
                      "failing atoms",
                      approvalData -> approvalData.failingAtoms()));

      // The order in which we add lines for approvals with the same label but different passing
      // atoms needs to be deterministic for tests. Just sort them by the string representation of
      // the passing atoms.
      for (Collection<ApprovalCopier.Result.ApprovalData>
          approvalsForSameLabelWithSamePassingAndFailingAtoms :
              approvalsForSameLabelByPassingAndFailingAtoms.asMap().entrySet().stream()
                  .sorted(
                      comparing(
                          (Map.Entry<
                                      ImmutableSet<String>,
                                      Collection<ApprovalCopier.Result.ApprovalData>>
                                  e) -> e.getKey().toString()))
                  .map(Map.Entry::getValue)
                  .collect(toImmutableList())) {
        message
            .append("* ")
            .append(
                formatApprovalsWithCopyCondition(
                    approvalsForSameLabelWithSamePassingAndFailingAtoms,
                    labelType.getCopyCondition().get()))
            .append("\n");
      }
    }

    return message.toString();
  }

  /**
   * Formats the given approvals with the given copy condition.
   *
   * <p>The given approvals must have the same label and the same passing and failing atoms.
   *
   * <p>E.g.: {Code-Review+1, Code-Review+2 (copy condition: "is:MIN")}
   *
   * <p>The following format may be returned:
   *
   * <ul>
   *   <li>{@code <comma-separated-list-of-approvals-for-the-same-label> (copy condition:
   *       "<copy-condition-without-UserInPredicate>")} (if a copy condition without UserInPredicate
   *       is present), e.g.: {@code Code-Review+1, Code-Review+2 (copy condition: "is:MIN")}
   *   <li>{@code <approval> by <comma-separated-list-of-approvers> (copy condition:
   *       "<copy-condition-with-UserInPredicate>")} (if a copy condition with UserInPredicate is
   *       present), e.g. {@code Code-Review+1 by <GERRIT_ACCOUNT_1000000>, <GERRIT_ACCOUNT_1000001>
   *       (copy condition: "approverin:7d9e2d5b561e75230e4463ae757ac5d6ff715d85")}
   *   <li>{@code <comma-separated-list-of-approval-for-the-same-label> (non-parseable copy
   *       condition: "<non-parseable copy-condition>")} (if a non-parseable copy condition is
   *       present), e.g.: {@code Code-Review+1, Code-Review+2 (non-parseable copy condition:
   *       "is:FOO")}
   * </ul>
   *
   * @param approvalsWithSameLabelAndSamePassingAndFailingAtoms the approvals that should be
   *     formatted, must be for the same label
   * @param copyCondition the copy condition of the label
   * @return the formatted approvals
   */
  private String formatApprovalsWithCopyCondition(
      Collection<ApprovalCopier.Result.ApprovalData>
          approvalsWithSameLabelAndSamePassingAndFailingAtoms,
      String copyCondition) {
    // Check that all given approvals have the same label and the same passing and failing atoms.
    checkThatPropertyIsTheSameForAllApprovals(
        approvalsWithSameLabelAndSamePassingAndFailingAtoms,
        "label",
        approvalData -> approvalData.patchSetApproval().label());
    checkThatPropertyIsTheSameForAllApprovals(
        approvalsWithSameLabelAndSamePassingAndFailingAtoms,
        "passing atoms",
        approvalData -> approvalData.passingAtoms());
    checkThatPropertyIsTheSameForAllApprovals(
        approvalsWithSameLabelAndSamePassingAndFailingAtoms,
        "failing atoms",
        approvalData -> approvalData.failingAtoms());

    StringBuilder message = new StringBuilder();

    boolean containsUserInPredicate;
    try {
      containsUserInPredicate = containsUserInPredicate(copyCondition);
    } catch (QueryParseException e) {
      logger.atWarning().withCause(e).log("Non-parsable query condition");
      message.append(
          formatApprovalsAsLabelVotesList(approvalsWithSameLabelAndSamePassingAndFailingAtoms));
      message.append(String.format(" (non-parseable copy condition: \"%s\")", copyCondition));
      return message.toString();
    }

    if (containsUserInPredicate) {
      // If a UserInPredicate is used (e.g. 'approverin:<group>' or 'uploaderin:<group>') we need to
      // include the approvers into the change message since they are relevant for the matching. For
      // example it can happen that the same approval of different users is copied for the one user
      // but not for the other user (since the one user is a member of the approverin group and the
      // other user isn't).
      //
      // Example:
      // * label Foo has the copy condition 'is:ANY approverin:123'
      // * group 123 contains UserA as member, but not UserB
      // * a change has the following approvals: Foo+1 by UserA and Foo+1 by UserB
      //
      // In this case Foo+1 by UserA is copied because UserA is a member of group 123 and the copy
      // condition matches, while Foo+1 by UserB is not copied because UserB is not a member of
      // group 123 and the copy condition doesn't match.
      //
      // So it can happen that the same approval Foo+1, but by different users, is copied and
      // outdated at the same time. To allow users to understand that the copying depends on who did
      // the approval, the approvers must be included into the change message.

      // sort the approvals by their approvers name-email so that the approvers always appear in a
      // deterministic order
      ImmutableList<ApprovalCopier.Result.ApprovalData> approvalsSortedByLabelVoteAndApprover =
          approvalsWithSameLabelAndSamePassingAndFailingAtoms.stream()
              .sorted(
                  comparing(
                          (ApprovalCopier.Result.ApprovalData approvalData) ->
                              LabelVote.create(
                                      approvalData.patchSetApproval().label(),
                                      approvalData.patchSetApproval().value())
                                  .format())
                      .thenComparing(
                          approvalData ->
                              accountCache
                                  .getEvenIfMissing(approvalData.patchSetApproval().accountId())
                                  .account()
                                  .getNameEmail(anonymousCowardName)))
              .collect(toImmutableList());

      ImmutableListMultimap<LabelVote, Account.Id> approversByLabelVote =
          Multimaps.index(
                  approvalsSortedByLabelVoteAndApprover,
                  approvalData ->
                      LabelVote.create(
                          approvalData.patchSetApproval().label(),
                          approvalData.patchSetApproval().value()))
              .entries().stream()
              .collect(
                  toImmutableListMultimap(
                      e -> e.getKey(), e -> e.getValue().patchSetApproval().accountId()));
      message.append(
          approversByLabelVote.asMap().entrySet().stream()
              .map(
                  approversByLabelVoteEntry ->
                      formatLabelVoteWithApprovers(
                          approversByLabelVoteEntry.getKey(), approversByLabelVoteEntry.getValue()))
              .collect(joining(", ")));
    } else {
      // copy condition doesn't contain a UserInPredicate
      message.append(
          formatApprovalsAsLabelVotesList(approvalsWithSameLabelAndSamePassingAndFailingAtoms));
    }
    ImmutableSet<String> passingAtoms =
        !approvalsWithSameLabelAndSamePassingAndFailingAtoms.isEmpty()
            ? approvalsWithSameLabelAndSamePassingAndFailingAtoms.iterator().next().passingAtoms()
            : ImmutableSet.of();
    message.append(
        String.format(
            " (copy condition: \"%s\")",
            formatCopyConditionAsMarkdown(copyCondition, passingAtoms)));
    return message.toString();
  }

  /** Checks that all given approvals have the same value for a given property. */
  private void checkThatPropertyIsTheSameForAllApprovals(
      Collection<ApprovalCopier.Result.ApprovalData> approvals,
      String propertyName,
      Function<ApprovalCopier.Result.ApprovalData, ?> propertyExtractor) {
    if (approvals.isEmpty()) {
      return;
    }

    Object propertyOfFirstEntry = propertyExtractor.apply(approvals.iterator().next());
    approvals.forEach(
        approvalData ->
            checkState(
                propertyExtractor.apply(approvalData).equals(propertyOfFirstEntry),
                "property %s of approval %s does not match, expected value: %s",
                propertyName,
                approvalData,
                propertyOfFirstEntry));
  }

  /**
   * Formats the given copy condition as a Markdown string.
   *
   * <p>Passing atoms are formatted as bold.
   *
   * @param copyCondition the copy condition that should be formatted
   * @param passingAtoms atoms of the copy conditions which are passing/matching
   * @return the formatted copy condition as a Markdown string
   */
  private String formatCopyConditionAsMarkdown(
      String copyCondition, ImmutableSet<String> passingAtoms) {
    StringBuilder formattedCopyCondition = new StringBuilder();
    StringTokenizer tokenizer = new StringTokenizer(copyCondition, " ()", /* returnDelims= */ true);
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (passingAtoms.contains(token)) {
        formattedCopyCondition.append("**" + token.replace("*", "\\*") + "**");
      } else {
        formattedCopyCondition.append(token);
      }
    }
    return formattedCopyCondition.toString();
  }

  private boolean containsUserInPredicate(String copyCondition) throws QueryParseException {
    // Use a request context to run checks as an internal user with expanded visibility. This is
    // so that the output of the copy condition does not depend on who is running the current
    // request (e.g. a group used in this query might not be visible to the person sending this
    // request).
    try (ManualRequestContext ignored = requestContext.open()) {
      return approvalQueryBuilderProvider.get().parse(copyCondition).getFlattenedPredicateList()
          .stream()
          .anyMatch(UserInPredicate.class::isInstance);
    }
  }

  /**
   * Formats the given approvals as a comma-separated list of label votes.
   *
   * <p>E.g.: {@code Code-Review+1, CodeReview+2}
   *
   * @param sortedApprovalsForSameLabel the approvals that should be formatted as a comma-separated
   *     list of label votes, must be sorted
   * @return the given approvals as a comma-separated list of label votes
   */
  private String formatApprovalsAsLabelVotesList(
      Collection<ApprovalCopier.Result.ApprovalData> sortedApprovalsForSameLabel) {
    return sortedApprovalsForSameLabel.stream()
        .map(ApprovalCopier.Result.ApprovalData::patchSetApproval)
        .map(psa -> LabelVote.create(psa.label(), psa.value()))
        .distinct()
        .map(LabelVote::format)
        .collect(joining(", "));
  }

  /**
   * Formats the given label vote with a comma-separated list of the given approvers.
   *
   * <p>E.g.: {@code Code-Review+1 by <user1-placeholder>, <user2-placeholder>}
   *
   * @param labelVote the label vote that should be formatted with a comma-separated list of the
   *     given approver
   * @param sortedApprovers the approvers that should be formatted as a comma-separated list for the
   *     given label vote
   * @return the given label vote with a comma-separated list of the given approvers
   */
  private String formatLabelVoteWithApprovers(
      LabelVote labelVote, Collection<Account.Id> sortedApprovers) {
    return new StringBuilder()
        .append(labelVote.format())
        .append(" by ")
        .append(
            sortedApprovers.stream()
                .map(AccountTemplateUtil::getAccountTemplate)
                .collect(joining(", ")))
        .toString();
  }

  /**
   * Gets {@link PatchSetApproval}s for a specified patch-set. The result includes copied votes but
   * does not include deleted labels.
   *
   * @param notes changenotes of the change.
   * @param psId patch-set id for the change and patch-set we want to get approvals.
   * @return all approvals for the specified patch-set, including copied votes, not including
   *     deleted labels.
   */
  public Iterable<PatchSetApproval> byPatchSet(ChangeNotes notes, PatchSet.Id psId) {
    List<PatchSetApproval> approvalsNotNormalized = notes.load().getApprovals().all().get(psId);
    return labelNormalizer.normalize(notes, approvalsNotNormalized).getNormalized();
  }

  public Iterable<PatchSetApproval> byPatchSetUser(
      ChangeNotes notes, PatchSet.Id psId, Account.Id accountId) {
    return filterApprovals(byPatchSet(notes, psId), accountId);
  }

  @Nullable
  public PatchSetApproval getSubmitter(ChangeNotes notes, PatchSet.Id c) {
    if (c == null) {
      return null;
    }
    try {
      // Submit approval is never copied.
      return getSubmitter(c, byChangeExcludingCopiedApprovals(notes).get(c));
    } catch (StorageException e) {
      return null;
    }
  }

  @Nullable
  public static PatchSetApproval getSubmitter(PatchSet.Id c, Iterable<PatchSetApproval> approvals) {
    if (c == null) {
      return null;
    }
    PatchSetApproval submitter = null;
    for (PatchSetApproval a : approvals) {
      if (a.patchSetId().equals(c) && a.value() > 0 && a.isLegacySubmit()) {
        if (submitter == null || a.granted().compareTo(submitter.granted()) > 0) {
          submitter = a;
        }
      }
    }
    return submitter;
  }

  public static String renderMessageWithApprovals(
      int patchSetId, Map<String, Short> n, Map<String, PatchSetApproval> c) {
    StringBuilder msgs = new StringBuilder("Uploaded patch set " + patchSetId);
    if (!n.isEmpty()) {
      boolean first = true;
      for (Map.Entry<String, Short> e : n.entrySet()) {
        if (c.containsKey(e.getKey()) && c.get(e.getKey()).value() == e.getValue()) {
          continue;
        }
        if (first) {
          msgs.append(":");
          first = false;
        }
        msgs.append(" ").append(LabelVote.create(e.getKey(), e.getValue()).format());
      }
    }
    return msgs.toString();
  }
}
