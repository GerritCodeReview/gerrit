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
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Deprecated
class HasDraftByLegacyPredicate extends ChangeOperatorPredicate
    implements ChangeDataSource {
  private final Arguments args;
  private final Account.Id accountId;

  HasDraftByLegacyPredicate(Arguments args,
      Account.Id accountId) {
    super(ChangeQueryBuilder.FIELD_DRAFTBY, accountId.toString());
    this.args = args;
    this.accountId = accountId;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    return !args.plcUtil
        .draftByChangeAuthor(args.db.get(), object.notes(), accountId)
        .isEmpty();
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    Set<Change.Id> ids = new HashSet<>();
    for (PatchLineComment sc :
        args.plcUtil.draftByAuthor(args.db.get(), accountId)) {
      ids.add(sc.getKey().getParentKey().getParentKey().getParentKey());
    }

    List<ChangeData> r = new ArrayList<>(ids.size());
    // TODO Don't load the changes directly from the database, but provide
    // project name + change ID to changeDataFactory, or delete this predicate.
    for (Change c : args.db.get().changes().get(ids)) {
      r.add(args.changeDataFactory.create(args.db.get(), c));
    }
    return new ListResultSet<>(r);
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
    return 0;
  }
}
