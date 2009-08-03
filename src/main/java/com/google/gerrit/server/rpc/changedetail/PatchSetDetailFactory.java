// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.rpc.changedetail;

import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountPatchReview;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.rpc.Handler;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Creates a {@link PatchSetDetail} from a {@link PatchSet}. */
class PatchSetDetailFactory extends Handler<PatchSetDetail> {
  interface Factory {
    PatchSetDetailFactory create(PatchSet.Id id);
  }

  private final PatchSetInfoFactory infoFactory;
  private final ReviewDb db;
  private final PatchSet.Id psId;

  private PatchSetDetail detail;
  PatchSet patchSet;

  @Inject
  PatchSetDetailFactory(final PatchSetInfoFactory psif, final ReviewDb db,
      @Assisted final PatchSet.Id id) {
    this.infoFactory = psif;
    this.db = db;
    this.psId = id;
  }

  @Override
  public PatchSetDetail call() throws OrmException, NoSuchEntityException,
      PatchSetInfoNotAvailableException {
    if (patchSet == null) {
      patchSet = db.patchSets().get(psId);
      if (patchSet == null) {
        throw new NoSuchEntityException();
      }
    }

    detail = new PatchSetDetail();
    detail.setPatchSet(patchSet);

    detail.setInfo(infoFactory.get(psId));
    detail.setPatches(db.patches().byPatchSet(psId).toList());

    final Account.Id me = Common.getAccountId();
    if (me != null) {
      // If we are signed in, compute the number of draft comments by the
      // current user on each of these patch files. This way they can more
      // quickly locate where they have pending drafts, and review them.
      //
      final List<PatchLineComment> comments =
          db.patchComments().draft(psId, me).toList();
      if (!comments.isEmpty()) {
        final Map<Patch.Key, Patch> byKey =
            db.patches().toMap(detail.getPatches());
        for (final PatchLineComment c : comments) {
          final Patch p = byKey.get(c.getKey().getParentKey());
          if (p != null) {
            p.setDraftCount(p.getDraftCount() + 1);
          }
        }
      }

      // Get all the reviewed patches in one query
      ResultSet<AccountPatchReview> reviews =
          db.accountPatchReviews().byReviewer(me, psId);
      HashSet<Patch.Key> reviewedPatches = new HashSet<Patch.Key>();
      for (AccountPatchReview review : reviews) {
        reviewedPatches.add(review.getKey().getPatchKey());
      }

      // Initialize the reviewed status of each patch
      for (Patch p : detail.getPatches()) {
        if (reviewedPatches.contains(p.getKey())) {
          p.setReviewedByCurrentUser(true);
        }
      }
    }

    return detail;
  }
}
