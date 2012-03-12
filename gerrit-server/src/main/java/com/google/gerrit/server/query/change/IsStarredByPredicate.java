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

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;

class IsStarredByPredicate extends OperatorPredicate<ChangeData, PatchSet>
    implements ChangeDataSource {
  private static String describe(CurrentUser user) {
    if (user instanceof IdentifiedUser) {
      return ((IdentifiedUser) user).getAccountId().toString();
    }
    return user.toString();
  }

  private final Provider<ReviewDb> db;
  private final CurrentUser user;

  IsStarredByPredicate(Provider<ReviewDb> db, CurrentUser user) {
    super(ChangeQueryBuilder.FIELD_STARREDBY, describe(user));
    this.db = db;
    this.user = user;
  }

  @Override
  public boolean match(final ChangeData object, final PatchSet subobject) {
    return user.getStarredChanges().contains(object.getId());
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    return ChangeDataResultSet.change( //
        db.get().changes().get(user.getStarredChanges()));
  }

  @Override
  public boolean hasChange() {
    return true;
  }

  @Override
  public int getCardinality() {
    return 10;
  }

  @Override
  public int getCost() {
    return ChangeCosts.cost(ChangeCosts.IDS_MEMORY, getCardinality());
  }
}
