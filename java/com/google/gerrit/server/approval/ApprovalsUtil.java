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
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
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
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
      PatchSet.Id psId, CurrentUser user, LabelId labelId, int value, Date when) {
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

  private final ApprovalInference approvalInference;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final ApprovalCache approvalCache;

  @VisibleForTesting
  @Inject
  public ApprovalsUtil(
      ApprovalInference approvalInference,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      ApprovalCache approvalCache) {
    this.approvalInference = approvalInference;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.approvalCache = approvalCache;
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
      permissionBackend.absentUser(accountId).change(notes).check(ChangePermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
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
    Date ts = update.getWhen();
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
      try {
        forChange.check(new LabelPermission.WithValue(name, value));
      } catch (AuthException e) {
        throw new AuthException(
            String.format("applying label \"%s\": %d is restricted", name, value), e);
      }
    }
  }

  public ListMultimap<PatchSet.Id, PatchSetApproval> byChange(ChangeNotes notes) {
    return notes.load().getApprovals();
  }

  public Iterable<PatchSetApproval> byPatchSet(
      ChangeNotes notes, PatchSet.Id psId, @Nullable RevWalk rw, @Nullable Config repoConfig) {
    return approvalInference.forPatchSet(notes, psId, rw, repoConfig);
  }

  public Iterable<PatchSetApproval> byPatchSet(ChangeNotes notes, PatchSet patchSet) {
    return approvalInference.forPatchSet(notes, patchSet, /* rw= */ null, /* repoConfig= */ null);
  }

  /**
   * This method should only be used when we want to dynamically compute the approvals. Generally,
   * the copied approvals are available in {@link ChangeNotes}. However, if the patch-set is just
   * being created, we need to dynamically compute the approvals so that we can persist them in
   * storage. The {@link RevWalk} and {@link Config} objects that are being used to create the new
   * patch-set are required for this method. Here we also add those votes to the provided {@link
   * ChangeUpdate} object.
   */
  public void persistCopiedApprovals(
      ChangeNotes notes,
      PatchSet patchSet,
      RevWalk revWalk,
      Config repoConfig,
      ChangeUpdate changeUpdate) {
    Set<PatchSetApproval> current =
        ImmutableSet.copyOf(notes.getApprovalsWithCopied().get(notes.getCurrentPatchSet().id()));
    Set<PatchSetApproval> inferred =
        ImmutableSet.copyOf(approvalInference.forPatchSet(notes, patchSet, revWalk, repoConfig));
    for (PatchSetApproval psa : inferred) {
      if (!current.contains(psa)) {
        changeUpdate.putCopiedApproval(psa);
      }
    }
  }

  public Iterable<PatchSetApproval> byPatchSet(ChangeNotes notes, PatchSet.Id psId) {
    return approvalCache.get(notes, psId);
  }

  public Iterable<PatchSetApproval> byPatchSetUser(
      ChangeNotes notes,
      PatchSet.Id psId,
      Account.Id accountId,
      @Nullable RevWalk rw,
      @Nullable Config repoConfig) {
    return filterApprovals(byPatchSet(notes, psId, rw, repoConfig), accountId);
  }

  public Iterable<PatchSetApproval> byPatchSetUser(
      ChangeNotes notes, PatchSet.Id psId, Account.Id accountId) {
    return filterApprovals(byPatchSet(notes, psId), accountId);
  }

  public PatchSetApproval getSubmitter(ChangeNotes notes, PatchSet.Id c) {
    if (c == null) {
      return null;
    }
    try {
      // Submit approval is never copied, so bypass expensive byPatchSet call.
      return getSubmitter(c, byChange(notes).get(c));
    } catch (StorageException e) {
      return null;
    }
  }

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
