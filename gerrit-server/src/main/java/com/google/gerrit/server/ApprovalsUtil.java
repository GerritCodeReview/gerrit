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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.Collections;
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
   * Moves the PatchSetApprovals to the specified PatchSet on the change from
   * the prior PatchSet, while keeping the vetos.
   *
   * @param db database connection to use for updates.
   * @param dest PatchSet to copy to
   * @throws OrmException
   * @return List<PatchSetApproval> The previous approvals
   */
  public List<PatchSetApproval> copyVetosToPatchSet(ReviewDb db,
      LabelTypes labelTypes, PatchSet.Id dest) throws OrmException {
    PatchSet.Id source;
    if (dest.get() > 1) {
      source = new PatchSet.Id(dest.getParentKey(), dest.get() - 1);
    } else {
      throw new OrmException("Previous patch set could not be found");
    }

    List<PatchSetApproval> patchSetApprovals =
        db.patchSetApprovals().byChange(dest.getParentKey()).toList();
    for (PatchSetApproval a : patchSetApprovals) {
      // ApprovalCategory.SUBMIT is still in db but not relevant in git-store
      if (!ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
        final LabelType type = labelTypes.byId(a.getCategoryId().get());
        if (a.getPatchSetId().equals(source) &&
            type.isCopyMinScore() &&
            type.isMaxNegative(a)) {
          db.patchSetApprovals().insert(
              Collections.singleton(new PatchSetApproval(dest, a)));
        }
      }
    }
    return patchSetApprovals;
  }

  public void addReviewers(ReviewDb db, LabelTypes labelTypes, Change change,
      PatchSet ps, PatchSetInfo info, Set<Id> wantReviewers,
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
    ApprovalCategory.Id catId = new ApprovalCategory.Id(
        allTypes.get(allTypes.size() - 1).getId());
    for (Account.Id account : need) {
      PatchSetApproval psa = new PatchSetApproval(
          new PatchSetApproval.Key(ps.getId(), account, catId),
          (short) 0);
      psa.cache(change);
      cells.add(psa);
    }
    db.patchSetApprovals().insert(cells);
  }
}
