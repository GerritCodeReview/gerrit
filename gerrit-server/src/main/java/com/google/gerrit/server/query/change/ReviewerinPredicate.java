// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

class ReviewerinPredicate extends OperatorPredicate<ChangeData> {
  private final Provider<ReviewDb> dbProvider;
  private final IdentifiedUser.GenericFactory userFactory;
  private final AccountGroup.Id id;

  ReviewerinPredicate(Provider<ReviewDb> dbProvider,
    IdentifiedUser.GenericFactory userFactory, AccountGroup.Id id) {
    super(ChangeQueryBuilder.FIELD_REVIEWERIN, id.toString());
    this.dbProvider = dbProvider;
    this.userFactory = userFactory;
    this.id = id;
  }

  AccountGroup.Id getAccountGroupId() {
    return id;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    for (PatchSetApproval p : object.approvals(dbProvider)) {
      final IdentifiedUser reviewer = userFactory.create(dbProvider,
        p.getAccountId());
      if (reviewer.getEffectiveGroups().contains(id)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getCost() {
    return 3;
  }
}
