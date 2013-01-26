// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.DeleteReviewer.Input;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;

class DeleteReviewer implements RestModifyView<ReviewerResource, Input> {
  static class Input {
  }

  private final Provider<ReviewDb> dbProvider;

  @Inject
  DeleteReviewer(Provider<ReviewDb> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public Object apply(ReviewerResource rsrc, Input input)
      throws AuthException, ResourceNotFoundException, OrmException {
    ChangeControl control = rsrc.getControl();
    Change.Id changeId = rsrc.getChange().getId();
    ReviewDb db = dbProvider.get();
    db.changes().beginTransaction(changeId);
    try {
      List<PatchSetApproval> del = Lists.newArrayList();
      for (PatchSetApproval a : approvals(db, rsrc)) {
        if (control.canRemoveReviewer(a)) {
          del.add(a);
        } else {
          throw new AuthException("delete not permitted");
        }
      }
      if (del.isEmpty()) {
        throw new ResourceNotFoundException();
      }
      db.patchSetApprovals().delete(del);
      db.commit();
    } finally {
      db.rollback();
    }
    return Response.none();
  }

  private Iterable<PatchSetApproval> approvals(ReviewDb db,
      final ReviewerResource rsrc) throws OrmException {
    return Iterables.filter(
        db.patchSetApprovals().byChange(rsrc.getChange().getId()),
        new Predicate<PatchSetApproval>() {
          @Override
          public boolean apply(PatchSetApproval input) {
            return input.getAccountId().equals(rsrc.getAccount().getId());
          }
        });
  }
}
