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
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.Collections;

class LegacyChangeIdPredicate extends IndexPredicate<ChangeData> implements
    ChangeDataSource {
  private final Arguments args;
  private final Change.Id id;

  LegacyChangeIdPredicate(Arguments args, Change.Id id) {
    super(ChangeField.LEGACY_ID, ChangeQueryBuilder.FIELD_CHANGE, id.toString());
    this.args = args;
    this.id = id;
  }

  @Override
  public boolean match(final ChangeData object) {
    return id.equals(object.getId());
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    Change c = args.db.get().changes().get(id);
    if (c != null) {
      return new ListResultSet<ChangeData>(Collections.singletonList(
          args.changeDataFactory.create(args.db.get(), c)));
    } else {
      return new ListResultSet<ChangeData>(Collections.<ChangeData> emptyList());
    }
  }

  @Override
  public boolean hasChange() {
    return true;
  }

  @Override
  public int getCardinality() {
    return 1;
  }

  @Override
  public int getCost() {
    return ChangeCosts.IDS_MEMORY;
  }
}
