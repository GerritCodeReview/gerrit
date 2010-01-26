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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;


class CommentDetailFactory extends Handler<CommentDetail> {
  interface Factory {
    CommentDetailFactory create(Patch.Key patchKey,
        @Assisted("patchSetA") PatchSet.Id patchSetA,
        @Assisted("patchSetB") PatchSet.Id patchSetB);
  }

  private final ReviewDb db;
  private final ChangeControl.Factory changeControlFactory;
  private final AccountInfoCacheFactory aic;

  private final Patch.Key patchKey;
  private final PatchSet.Id psa;
  private final PatchSet.Id psb;

  private final PatchSet.Id patchSetId;
  private final Change.Id changeId;

  @Inject
  CommentDetailFactory(final ReviewDb db,
      final ChangeControl.Factory changeControlFactory,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      @Assisted final Patch.Key patchKey,
      @Assisted("patchSetA") @Nullable final PatchSet.Id patchSetA,
      @Assisted("patchSetB") final PatchSet.Id patchSetB) {
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.aic = accountInfoCacheFactory.create();

    this.patchKey = patchKey;
    this.psa = patchSetA;
    this.psb = patchSetB;

    patchSetId = patchKey.getParentKey();
    changeId = patchSetId.getParentKey();
  }

  @Override
  public CommentDetail call() throws OrmException, NoSuchChangeException {
    validatePatchSetId(psa);
    validatePatchSetId(psb);

    final ChangeControl control = changeControlFactory.validateFor(changeId);
    final String pn = patchKey.getFileName();
    final CommentDetail r = new CommentDetail(psa, psb);

    final List<Patch> historyList = new ArrayList<Patch>();
    final Map<PatchSet.Id, Patch> bySet = new HashMap<PatchSet.Id, Patch>();
    for (final PatchSet ps : db.patchSets().byChange(changeId)) {
      final Patch p = new Patch(new Patch.Key(ps.getId(), pn));
      historyList.add(p);
      bySet.put(ps.getId(), p);
    }

    for (PatchLineComment c : db.patchComments().published(changeId, pn)) {
      if (r.include(c)) {
        aic.want(c.getAuthor());
      }
      final PatchSet.Id psId = c.getKey().getParentKey().getParentKey();
      final Patch patch = bySet.get(psId);
      if (patch != null) {
        patch.setCommentCount(patch.getCommentCount() + 1);
      }
    }

    final CurrentUser user = control.getCurrentUser();
    if (user instanceof IdentifiedUser) {
      final Account.Id me = ((IdentifiedUser) user).getAccountId();
      for (PatchLineComment c : db.patchComments().draft(changeId, pn, me)) {
        if (r.include(c)) {
          aic.want(me);
        }
        final PatchSet.Id psId = c.getKey().getParentKey().getParentKey();
        final Patch patch = bySet.get(psId);
        if (patch != null) {
          patch.setDraftCount(patch.getDraftCount() + 1);
        }
      }
    }

    r.setHistory(historyList);
    r.setAccountInfoCache(aic.create());
    return r;
  }

  private void validatePatchSetId(final PatchSet.Id psId)
      throws NoSuchChangeException {
    if (psId == null) { // OK, means use base;
    } else if (changeId.equals(psId.getParentKey())) { // OK, same change;
    } else {
      throw new NoSuchChangeException(changeId);
    }
  }
}
