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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

class ReviewerinPredicate extends OperatorPredicate<ChangeData, PatchSet> {
  private final Provider<ReviewDb> dbProvider;
  private final IdentifiedUser.GenericFactory userFactory;
  private final AccountGroup.UUID uuid;

  ReviewerinPredicate(Provider<ReviewDb> dbProvider,
    IdentifiedUser.GenericFactory userFactory, AccountGroup.UUID uuid) {
    super(ChangeQueryBuilder.FIELD_REVIEWERIN, uuid.toString());
    this.dbProvider = dbProvider;
    this.userFactory = userFactory;
    this.uuid = uuid;
  }

  AccountGroup.UUID getAccountGroupUUID() {
    return uuid;
  }

  @Override
  public boolean match(final ChangeData object, final PatchSet subobject)
      throws OrmException {
    for (PatchSetApproval p : object.approvals(dbProvider)) {
      final IdentifiedUser reviewer = userFactory.create(dbProvider,
        p.getAccountId());
      if (reviewer.getEffectiveGroups().contains(uuid)) {
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
