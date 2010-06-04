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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountPatchReview;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Creates a {@link PatchSetDetail} from a {@link PatchSet}. */
class PatchSetDetailFactory extends Handler<PatchSetDetail> {
  interface Factory {
    PatchSetDetailFactory create(PatchSet.Id id);
  }

  private final PatchSetInfoFactory infoFactory;
  private final ReviewDb db;
  private final PatchListCache patchListCache;
  private final ChangeControl.Factory changeControlFactory;

  private final PatchSet.Id psId;

  private PatchSetDetail detail;
  ChangeControl control;
  PatchSet patchSet;

  @Inject
  PatchSetDetailFactory(final PatchSetInfoFactory psif, final ReviewDb db,
      final PatchListCache patchListCache,
      final ChangeControl.Factory changeControlFactory,
      @Assisted final PatchSet.Id id) {
    this.infoFactory = psif;
    this.db = db;
    this.patchListCache = patchListCache;
    this.changeControlFactory = changeControlFactory;

    this.psId = id;
  }

  @Override
  public PatchSetDetail call() throws OrmException, NoSuchEntityException,
      PatchSetInfoNotAvailableException, NoSuchChangeException {
    if (control == null || patchSet == null) {
      control = changeControlFactory.validateFor(psId.getParentKey());
      patchSet = db.patchSets().get(psId);
      if (patchSet == null) {
        throw new NoSuchEntityException();
      }
    }

    final PatchList list = patchListCache.get(control.getChange(), patchSet);
    if (list == null) {
      throw new NoSuchEntityException();
    }

    final List<Patch> patches = list.toPatchList(patchSet.getId());
    final Map<Patch.Key, Patch> byKey = new HashMap<Patch.Key, Patch>();
    for (final Patch p : patches) {
      byKey.put(p.getKey(), p);
    }

    for (final PatchLineComment c : db.patchComments().publishedByPatchSet(psId)) {
      final Patch p = byKey.get(c.getKey().getParentKey());
      if (p != null) {
        p.setCommentCount(p.getCommentCount() + 1);
      }
    }

    detail = new PatchSetDetail();
    detail.setPatchSet(patchSet);

    detail.setInfo(infoFactory.get(psId));
    detail.setPatches(patches);

    final CurrentUser user = control.getCurrentUser();
    if (user instanceof IdentifiedUser) {
      // If we are signed in, compute the number of draft comments by the
      // current user on each of these patch files. This way they can more
      // quickly locate where they have pending drafts, and review them.
      //
      final Account.Id me = ((IdentifiedUser) user).getAccountId();
      for (final PatchLineComment c : db.patchComments().draftByPatchSet(psId, me)) {
        final Patch p = byKey.get(c.getKey().getParentKey());
        if (p != null) {
          p.setDraftCount(p.getDraftCount() + 1);
        }
      }

      for (AccountPatchReview r : db.accountPatchReviews().byReviewer(me, psId)) {
        final Patch p = byKey.get(r.getKey().getPatchKey());
        if (p != null) {
          p.setReviewedByCurrentUser(true);
        }
      }
    }

    return detail;
  }
}
