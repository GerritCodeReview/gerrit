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
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.extensions.Function;
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

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApprovalsUtil {
  private final ReviewDb db;
  private final ApprovalTypes approvalTypes;

  @Inject
  ApprovalsUtil(ReviewDb db, ApprovalTypes approvalTypes) {
    this.db = db;
    this.approvalTypes = approvalTypes;
  }

  /**
   * Resync the changeOpen status which is cached in the approvals table for
   * performance reasons
   */
  public void syncChangeStatus(final Change change)
      throws OrmException {
    final List<PatchSetApproval> approvals =
        db.patchSetApprovals().byChange(change.getId()).toList();
    for (PatchSetApproval a : approvals) {
      a.cache(change);
    }
    db.patchSetApprovals().update(approvals);
  }

  public interface ApprovalsFunction
    extends Function<Set<PatchSetApproval>,Set<PatchSetApproval>> {
  };

  public class CopyOnlyVetos implements ApprovalsFunction {
    public Set<PatchSetApproval> apply(Set<PatchSetApproval> approvals) {
      Set<PatchSetApproval> vetos = new HashSet<PatchSetApproval>();
      for (PatchSetApproval a : approvals) {
        // ApprovalCategory.SUBMIT is still in db but not relevant in git-store
        if (!ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
          final ApprovalType type = approvalTypes.byId(a.getCategoryId());
          if (type.getCategory().isCopyMinScore() && type.isMaxNegative(a)) {
            vetos.add(a);
          }
        }
      }
      return vetos;
    }
  }

  /**
   * Moves the PatchSetApprovals to the last PatchSet on the change while
   * keeping the vetos.
   *
   * @param change Change to update
   * @throws OrmException
   * @throws IOException
   */
  public void applyRebase(Change change)
      throws OrmException, IOException {
    applyApprovalsFromPreviousPatchSet(change, this.new CopyOnlyVetos());
  }

  public void applyApprovalsFromPreviousPatchSet(Change change, ApprovalsFunction f)
      throws OrmException, IOException {
    PatchSet.Id source;
    if (change.getNumberOfPatchSets() > 1) {
      source = new PatchSet.Id(change.getId(), change.getNumberOfPatchSets() - 1);
    } else {
      throw new IOException("Previous patch set could not be found");
    }

    PatchSet.Id dest = change.currPatchSetId();

    Set<PatchSetApproval> prev = new HashSet<PatchSetApproval>();
    prev.addAll(db.patchSetApprovals().byPatchSet(source).toList());

    Set<PatchSetApproval> cur = new HashSet<PatchSetApproval>();
    for (PatchSetApproval a : f.apply(prev)) {
      cur.add(new PatchSetApproval(dest, a));
    }
    db.patchSetApprovals().insert(cur);
  }


  /** Attach reviewers to a change. */
  public void addReviewers(Change change, PatchSet ps, PatchSetInfo info,
      Set<Account.Id> wantReviewers) throws OrmException {
    Set<Id> existing = Sets.<Account.Id> newHashSet();
    addReviewers(change, ps, info, wantReviewers, existing);
  }

  /** Attach reviewers to a change. */
  public void addReviewers(Change change, PatchSet ps, PatchSetInfo info,
      Set<Account.Id> wantReviewers, Set<Account.Id> existingReviewers)
      throws OrmException {
    List<ApprovalType> allTypes = approvalTypes.getApprovalTypes();
    if (allTypes.isEmpty()) {
      return;
    }

    Set<Account.Id> need = Sets.newHashSet(wantReviewers);
    Account.Id authorId = info.getAuthor() != null
        ? info.getAuthor().getAccount()
        : null;
    if (authorId != null) {
      need.add(authorId);
    }

    Account.Id committerId = info.getCommitter() != null
        ? info.getCommitter().getAccount()
        : null;
    if (committerId != null) {
      need.add(committerId);
    }
    need.remove(change.getOwner());
    need.removeAll(existingReviewers);

    List<PatchSetApproval> cells = Lists.newArrayListWithCapacity(need.size());
    ApprovalCategory.Id catId = allTypes.get(allTypes.size() - 1).getCategory().getId();
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
