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

package com.google.gerrit.server;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility functions to manipulate patchset approvals.
 * <p>
 * Approvals are overloaded, they represent both approvals and reviewers
 * which should be CCed on a change.  To ensure that reviewers are not lost
 * there must always be an approval on each patchset for each reviewer,
 * even if the reviewer hasn't actually given a score to the change.  To
 * mark the "no score" case, a dummy approval, which may live in any of
 * the available categories, with a score of 0 is used.
 * <p>
 * The methods in this class only modify the gwtorm database.
 */
@Singleton
public class ApprovalsUtil {
  private static Ordering<PatchSetApproval> SORT_APPROVALS = Ordering.natural()
      .onResultOf(new Function<PatchSetApproval, Timestamp>() {
        @Override
        public Timestamp apply(PatchSetApproval a) {
          return a.getGranted();
        }
      });

  public static List<PatchSetApproval> sortApprovals(
      Iterable<PatchSetApproval> approvals) {
    return SORT_APPROVALS.sortedCopy(approvals);
  }

  private static Iterable<PatchSetApproval> filterApprovals(
      Iterable<PatchSetApproval> psas, final Account.Id accountId) {
    return Iterables.filter(psas, new Predicate<PatchSetApproval>() {
      @Override
      public boolean apply(PatchSetApproval input) {
        return Objects.equals(input.getAccountId(), accountId);
      }
    });
  }

  private final NotesMigration migration;
  private final ApprovalCopier copier;

  @VisibleForTesting
  @Inject
  public ApprovalsUtil(NotesMigration migration,
      ApprovalCopier copier) {
    this.migration = migration;
    this.copier = copier;
  }

  /**
   * Get all reviewers for a change.
   *
   * @param db review database.
   * @param notes change notes.
   * @return multimap of reviewers keyed by state, where each account appears
   *     exactly once in {@link SetMultimap#values()}, and
   *     {@link ReviewerStateInternal#REMOVED} is not present.
   * @throws OrmException if reviewers for the change could not be read.
   */
  public ImmutableSetMultimap<ReviewerStateInternal, Account.Id> getReviewers(
      ReviewDb db, ChangeNotes notes) throws OrmException {
    if (!migration.readChanges()) {
      return getReviewers(db.patchSetApprovals().byChange(notes.getChangeId()));
    }
    return notes.load().getReviewers();
  }

  /**
   * Get all reviewers for a change.
   *
   * @param allApprovals all approvals to consider; must all belong to the same
   *     change.
   * @return multimap of reviewers keyed by state, where each account appears
   *     exactly once in {@link SetMultimap#values()}, and
   *     {@link ReviewerStateInternal#REMOVED} is not present.
   */
  public ImmutableSetMultimap<ReviewerStateInternal, Account.Id> getReviewers(
      ChangeNotes notes, Iterable<PatchSetApproval> allApprovals)
      throws OrmException {
    if (!migration.readChanges()) {
      return getReviewers(allApprovals);
    }
    return notes.load().getReviewers();
  }

  private static ImmutableSetMultimap<ReviewerStateInternal, Account.Id> getReviewers(
      Iterable<PatchSetApproval> allApprovals) {
    PatchSetApproval first = null;
    SetMultimap<ReviewerStateInternal, Account.Id> reviewers =
        LinkedHashMultimap.create();
    for (PatchSetApproval psa : allApprovals) {
      if (first == null) {
        first = psa;
      } else {
        checkArgument(
            first.getKey().getParentKey().getParentKey().equals(
              psa.getKey().getParentKey().getParentKey()),
            "multiple change IDs: %s, %s", first.getKey(), psa.getKey());
      }
      Account.Id id = psa.getAccountId();
      if (psa.getValue() != 0) {
        reviewers.put(REVIEWER, id);
        reviewers.remove(CC, id);
      } else if (!reviewers.containsEntry(REVIEWER, id)) {
        reviewers.put(CC, id);
      }
    }
    return ImmutableSetMultimap.copyOf(reviewers);
  }

  public List<PatchSetApproval> addReviewers(ReviewDb db,
      ChangeUpdate update, LabelTypes labelTypes, Change change, PatchSet ps,
      PatchSetInfo info, Iterable<Account.Id> wantReviewers,
      Collection<Account.Id> existingReviewers) throws OrmException {
    return addReviewers(db, update, labelTypes, change, ps.getId(),
        ps.isDraft(), info.getAuthor().getAccount(),
        info.getCommitter().getAccount(), wantReviewers, existingReviewers);
  }

  public List<PatchSetApproval> addReviewers(ReviewDb db, ChangeNotes notes,
      ChangeUpdate update, LabelTypes labelTypes, Change change,
      Iterable<Account.Id> wantReviewers) throws OrmException {
    PatchSet.Id psId = change.currentPatchSetId();
    return addReviewers(db, update, labelTypes, change, psId, false, null, null,
        wantReviewers, getReviewers(db, notes).values());
  }

  private List<PatchSetApproval> addReviewers(ReviewDb db, ChangeUpdate update,
      LabelTypes labelTypes, Change change, PatchSet.Id psId, boolean isDraft,
      Account.Id authorId, Account.Id committerId,
      Iterable<Account.Id> wantReviewers,
      Collection<Account.Id> existingReviewers) throws OrmException {
    List<LabelType> allTypes = labelTypes.getLabelTypes();
    if (allTypes.isEmpty()) {
      return ImmutableList.of();
    }

    Set<Account.Id> need = Sets.newLinkedHashSet(wantReviewers);
    if (authorId != null && !isDraft) {
      need.add(authorId);
    }

    if (committerId != null && !isDraft) {
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
      cells.add(new PatchSetApproval(
          new PatchSetApproval.Key(psId, account, labelId),
          (short) 0, TimeUtil.nowTs()));
      update.putReviewer(account, REVIEWER);
    }
    db.patchSetApprovals().insert(cells);
    return Collections.unmodifiableList(cells);
  }

  public void addApprovals(ReviewDb db, ChangeUpdate update,
      LabelTypes labelTypes, PatchSet ps, ChangeControl changeCtl,
      Map<String, Short> approvals) throws OrmException {
    Timestamp ts = TimeUtil.nowTs();
    addApprovals(db, update, labelTypes, ps, changeCtl, approvals, ts);
  }

  public void addApprovals(ReviewDb db, ChangeUpdate update,
      LabelTypes labelTypes, PatchSet ps, ChangeControl changeCtl,
      Map<String, Short> approvals, Timestamp ts) throws OrmException {
    if (!approvals.isEmpty()) {
      checkApprovals(approvals, changeCtl);
      List<PatchSetApproval> cells = new ArrayList<>(approvals.size());
      for (Map.Entry<String, Short> vote : approvals.entrySet()) {
        LabelType lt = labelTypes.byLabel(vote.getKey());
        cells.add(new PatchSetApproval(new PatchSetApproval.Key(
            ps.getId(),
            ps.getUploader(),
            lt.getLabelId()),
            vote.getValue(),
            ts));
        update.putApproval(vote.getKey(), vote.getValue());
      }
      db.patchSetApprovals().insert(cells);
    }
  }

  public static void checkLabel(LabelTypes labelTypes, String name, Short value) {
    LabelType label = labelTypes.byLabel(name);
    if (label == null) {
      throw new IllegalArgumentException(String.format(
          "label \"%s\" is not a configured label", name));
    }
    if (label.getValue(value) == null) {
      throw new IllegalArgumentException(String.format(
          "label \"%s\": %d is not a valid value", name, value));
    }
  }

  private static void checkApprovals(Map<String, Short> approvals,
      ChangeControl changeCtl) {
    for (Map.Entry<String, Short> vote : approvals.entrySet()) {
      String name = vote.getKey();
      Short value = vote.getValue();
      PermissionRange range = changeCtl.getRange(Permission.forLabel(name));
      if (range == null || !range.contains(value)) {
        throw new IllegalArgumentException(String.format(
            "applying label \"%s\": %d is restricted", name, value));
      }
    }
  }

  public ListMultimap<PatchSet.Id, PatchSetApproval> byChange(ReviewDb db,
      ChangeNotes notes) throws OrmException {
    if (!migration.readChanges()) {
      ImmutableListMultimap.Builder<PatchSet.Id, PatchSetApproval> result =
          ImmutableListMultimap.builder();
      for (PatchSetApproval psa
          : db.patchSetApprovals().byChange(notes.getChangeId())) {
        result.put(psa.getPatchSetId(), psa);
      }
      return result.build();
    }
    return notes.load().getApprovals();
  }

  public Iterable<PatchSetApproval> byPatchSet(ReviewDb db, ChangeControl ctl,
      PatchSet.Id psId) throws OrmException {
    if (!migration.readChanges()) {
      return sortApprovals(db.patchSetApprovals().byPatchSet(psId));
    }
    return copier.getForPatchSet(db, ctl, psId);
  }

  public Iterable<PatchSetApproval> byPatchSetUser(ReviewDb db,
      ChangeControl ctl, PatchSet.Id psId, Account.Id accountId)
      throws OrmException {
    if (!migration.readChanges()) {
      return sortApprovals(
          db.patchSetApprovals().byPatchSetUser(psId, accountId));
    }
    return filterApprovals(byPatchSet(db, ctl, psId), accountId);
  }

  public PatchSetApproval getSubmitter(ReviewDb db, ChangeNotes notes,
      PatchSet.Id c) {
    if (c == null) {
      return null;
    }
    try {
      // Submit approval is never copied, so bypass expensive byPatchSet call.
      return getSubmitter(c, byChange(db, notes).get(c));
    } catch (OrmException e) {
      return null;
    }
  }

  public static PatchSetApproval getSubmitter(PatchSet.Id c,
      Iterable<PatchSetApproval> approvals) {
    if (c == null) {
      return null;
    }
    PatchSetApproval submitter = null;
    for (PatchSetApproval a : approvals) {
      if (a.getPatchSetId().equals(c) && a.getValue() > 0 && a.isSubmit()) {
        if (submitter == null
            || a.getGranted().compareTo(submitter.getGranted()) > 0) {
          submitter = a;
        }
      }
    }
    return submitter;
  }
}
