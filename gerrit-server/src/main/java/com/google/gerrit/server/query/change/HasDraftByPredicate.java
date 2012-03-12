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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;

class HasDraftByPredicate extends OperatorPredicate<ChangeData, PatchSet> implements
    ChangeDataSource {
  private final Provider<ReviewDb> db;
  private final Account.Id accountId;

  HasDraftByPredicate(Provider<ReviewDb> db, Account.Id accountId) {
    super(ChangeQueryBuilder.FIELD_DRAFTBY, accountId.toString());
    this.db = db;
    this.accountId = accountId;
  }

  @Override
  public boolean match(final ChangeData object, final PatchSet subobject)
      throws OrmException {
    for (PatchLineComment c : object.comments(db)) {
      final PatchSet.Id patchSetId = c.getKey().getParentKey().getParentKey();
      if (c.getStatus() == PatchLineComment.Status.DRAFT
          && c.getAuthor().equals(accountId) &&
          (subobject == null || patchSetId.equals(subobject.getId()))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    HashSet<Change.Id> ids = new HashSet<Change.Id>();
    for (PatchLineComment sc : db.get().patchComments()
        .draftByAuthor(accountId)) {
      ids.add(sc.getKey().getParentKey().getParentKey().getParentKey());
    }

    ArrayList<ChangeData> r = new ArrayList<ChangeData>(ids.size());
    for (Change.Id id : ids) {
      r.add(new ChangeData(id));
    }
    return new ListResultSet<ChangeData>(r);
  }

  @Override
  public boolean hasChange() {
    return false;
  }

  @Override
  public int getCardinality() {
    return 20;
  }

  @Override
  public int getCost() {
    return ChangeCosts.cost(ChangeCosts.PATCH_SETS_SCAN, getCardinality());
  }
}
