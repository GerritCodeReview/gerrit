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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

class ReviewerPredicate extends OperatorPredicate<ChangeData> {
  private final Provider<ReviewDb> dbProvider;
  private final Account.Id id;

  ReviewerPredicate(Provider<ReviewDb> dbProvider, Account.Id id) {
    super(ChangeQueryBuilder.FIELD_OWNER, id.toString());
    this.dbProvider = dbProvider;
    this.id = id;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    for (PatchSetApproval p : object.approvals(dbProvider)) {
      if (id.equals(p.getAccountId())) {
        return true;
      }
    }
    return false;
  }
}
