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

package com.google.gerrit.server.patch;

import static com.google.gerrit.client.rpc.BaseServiceImplementation.canRead;

import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.patches.CommentDetail;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SafeFile;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Action;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Failure;
import com.google.gwtorm.client.OrmException;


class PatchCommentAction implements Action<CommentDetail> {
  private final Patch.Key patchKey;
  private final PatchSet.Id psa;
  private final PatchSet.Id psb;

  private final PatchSet.Id patchSetId;
  private final Change.Id changeId;

  private Account.Id me;
  private Change change;
  private Patch patch;

  PatchCommentAction(final Patch.Key patchKey, final PatchSet.Id psa,
      final PatchSet.Id psb) {
    this.patchKey = patchKey;
    this.psa = psa;
    this.psb = psb;

    patchSetId = patchKey.getParentKey();
    changeId = patchSetId.getParentKey();
  }

  public CommentDetail run(final ReviewDb db) throws OrmException, Failure {
    validatePatchSetId(psa);
    validatePatchSetId(psb);

    me = Common.getAccountId();
    change = db.changes().get(changeId);
    patch = db.patches().get(patchKey);

    if (change == null || patch == null || !canRead(change)) {
      throw new Failure(new NoSuchEntityException());
    }

    final String pn = patch.getFileName();
    final AccountInfoCacheFactory aic = new AccountInfoCacheFactory(db);
    final CommentDetail r;

    r = new CommentDetail(psa, psb != null ? psb : patchSetId);
    for (PatchLineComment p : db.patchComments().published(changeId, pn)) {
      if (r.include(p)) {
        aic.want(p.getAuthor());
      }
    }

    if (me != null) {
      aic.want(me);
      for (PatchLineComment p : db.patchComments().draft(changeId, pn, me)) {
        r.include(p);
      }
    }

    r.setHistory(db.patches().history(changeId, pn).toList());
    r.setAccountInfoCache(aic.create());
    return r;
  }

  private void validatePatchSetId(final PatchSet.Id psId) throws Failure {
    if (psId == null) { // OK, means use base;
    } else if (changeId.equals(psId.getParentKey())) { // OK, same change;
    } else {
      throw new Failure(new NoSuchEntityException());
    }
  }
}
