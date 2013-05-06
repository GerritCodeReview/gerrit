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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.List;
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
 */
public class ApprovalsUtil {
  private final ReviewDb db;

  @Inject
  public ApprovalsUtil(ReviewDb db) {
    this.db = db;
  }

  /**
   * Resync the changeOpen status which is cached in the approvals table for
   * performance reasons
   */
  public void syncChangeStatus(final Change change) throws OrmException {
    final List<PatchSetApproval> approvals =
        db.patchSetApprovals().byChange(change.getId()).toList();
    for (PatchSetApproval a : approvals) {
      a.cache(change);
    }
    db.patchSetApprovals().update(approvals);
  }

  /**
   * Copy min/max scores from one patch set to another.
   *
   * @throws OrmException
   */
  public static void copyLabels(ReviewDb db, LabelTypes labelTypes,
      PatchSet.Id source, PatchSet.Id dest) throws OrmException {
    Iterable<PatchSetApproval> sourceApprovals =
        db.patchSetApprovals().byPatchSet(source);
    copyLabels(db, labelTypes, sourceApprovals, source, dest);
  }

  /**
   * Copy a set's min/max scores from one patch set to another.
   *
   * @throws OrmException
   */
  public static void copyLabels(ReviewDb db, LabelTypes labelTypes,
      Iterable<PatchSetApproval> sourceApprovals, PatchSet.Id source,
      PatchSet.Id dest) throws OrmException {
    List<PatchSetApproval> copied = Lists.newArrayList();
    for (PatchSetApproval a : sourceApprovals) {
      if (source.equals(a.getPatchSetId())) {
        LabelType type = labelTypes.byLabel(a.getLabelId());
        if (type == null) {
          continue;
        } else if (type.isCopyMinScore() && type.isMaxNegative(a)) {
          copied.add(new PatchSetApproval(dest, a));
        } else if (type.isCopyMaxScore() && type.isMaxPositive(a)) {
          copied.add(new PatchSetApproval(dest, a));
        }
      }
    }
    db.patchSetApprovals().insert(copied);
  }

  public void addReviewers(ReviewDb db, LabelTypes labelTypes, Change change,
      PatchSet ps, PatchSetInfo info, Set<Account.Id> wantReviewers,
      Set<Account.Id> existingReviewers) throws OrmException {
    List<LabelType> allTypes = labelTypes.getLabelTypes();
    if (allTypes.isEmpty()) {
      return;
    }

    Set<Account.Id> need = Sets.newHashSet(wantReviewers);
    Account.Id authorId = info.getAuthor() != null
        ? info.getAuthor().getAccount()
        : null;
    if (authorId != null && !ps.isDraft()) {
      need.add(authorId);
    }

    Account.Id committerId = info.getCommitter() != null
        ? info.getCommitter().getAccount()
        : null;
    if (committerId != null && !ps.isDraft()) {
      need.add(committerId);
    }
    need.remove(change.getOwner());
    need.removeAll(existingReviewers);

    List<PatchSetApproval> cells = Lists.newArrayListWithCapacity(need.size());
    LabelId labelId = Iterables.getLast(allTypes).getLabelId();
    for (Account.Id account : need) {
      PatchSetApproval psa = new PatchSetApproval(
          new PatchSetApproval.Key(ps.getId(), account, labelId),
          (short) 0);
      psa.cache(change);
      cells.add(psa);
    }
    db.patchSetApprovals().insert(cells);
  }
}
