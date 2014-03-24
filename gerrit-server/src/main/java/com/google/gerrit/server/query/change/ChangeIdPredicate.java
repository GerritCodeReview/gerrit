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
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

class ChangeIdPredicate extends IndexPredicate<ChangeData> implements
    ChangeDataSource {
  private final Arguments args;

  ChangeIdPredicate(Arguments args, String id) {
    super(ChangeField.ID, ChangeQueryBuilder.FIELD_CHANGE, id);
    this.args = args;
  }

  @Override
  public boolean match(final ChangeData cd) throws OrmException {
    Change change = cd.change();
    if (change == null) {
      return false;
    }

    String key = change.getKey().get();
    if (key.equals(getValue()) || key.startsWith(getValue())) {
      return true;
    }
    return false;
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    Change.Key a = new Change.Key(getValue());
    Change.Key b = a.max();
    return ChangeDataResultSet.change(args.changeDataFactory, args.db,
        args.db.get().changes().byKeyRange(a, b));
  }

  @Override
  public boolean hasChange() {
    return true;
  }

  @Override
  public int getCost() {
    return ChangeCosts.cost(ChangeCosts.CHANGES_SCAN, getCardinality());
  }

  @Override
  public int getCardinality() {
    return ChangeCosts.CARD_KEY;
  }
}
