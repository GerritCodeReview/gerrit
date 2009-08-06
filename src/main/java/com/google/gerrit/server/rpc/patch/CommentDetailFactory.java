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

package com.google.gerrit.server.rpc.patch;

import com.google.gerrit.client.patches.CommentDetail;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.config.Nullable;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.rpc.Handler;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;


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

  private Patch patch;

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
    patch = db.patches().get(patchKey);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }

    final String pn = patch.getFileName();
    final CommentDetail r;

    r = new CommentDetail(psa, psb != null ? psb : patchSetId);
    for (PatchLineComment p : db.patchComments().published(changeId, pn)) {
      if (r.include(p)) {
        aic.want(p.getAuthor());
      }
    }

    if (control.getCurrentUser() instanceof IdentifiedUser) {
      final Account.Id me =
          ((IdentifiedUser) control.getCurrentUser()).getAccountId();
      aic.want(me);
      for (PatchLineComment p : db.patchComments().draft(changeId, pn, me)) {
        r.include(p);
      }
    }

    r.setHistory(db.patches().history(changeId, pn).toList());
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
