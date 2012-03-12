// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

class IsReviewedPredicate extends OperatorPredicate<ChangeData, PatchSet> {
  private final Provider<ReviewDb> dbProvider;

  IsReviewedPredicate(Provider<ReviewDb> dbProvider) {
    super(ChangeQueryBuilder.FIELD_IS, "reviewed");
    this.dbProvider = dbProvider;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    Change c = object.change(dbProvider);
    if (c == null) {
      return false;
    }

    PatchSet.Id current = c.currentPatchSetId();
    for (PatchSetApproval p : object.approvals(dbProvider)) {
      if (p.getPatchSetId().equals(current) && p.getValue() != 0) {
        return true;
      }
    }

    return false;
  }

  @Override
  public int getCost() {
    return 2;
  }
}
