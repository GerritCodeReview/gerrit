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

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;

import java.util.ArrayList;

class IsStarredByPredicate extends OperatorPredicate<ChangeData> implements
    ChangeDataSource {
  private static String describe(CurrentUser user) {
    if (user instanceof IdentifiedUser) {
      return ((IdentifiedUser) user).getAccountId().toString();
    }
    return user.toString();
  }

  private final CurrentUser user;

  IsStarredByPredicate(CurrentUser user) {
    super("starredby", describe(user));
    this.user = user;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    return user.getStarredChanges().contains(object.getId());
  }

  @Override
  public ResultSet<ChangeData> read() {
    ArrayList<ChangeData> r = new ArrayList<ChangeData>();
    for (Change.Id id : user.getStarredChanges()) {
      r.add(new ChangeData(id));
    }
    return new ListResultSet<ChangeData>(r);
  }
}
