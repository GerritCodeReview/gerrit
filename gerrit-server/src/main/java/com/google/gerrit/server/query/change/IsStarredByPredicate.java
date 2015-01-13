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

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.List;
import java.util.Set;

class IsStarredByPredicate extends OrPredicate<ChangeData> implements
    ChangeDataSource {
  private static String describe(CurrentUser user) {
    if (user.isIdentifiedUser()) {
      return ((IdentifiedUser) user).getAccountId().toString();
    }
    return user.toString();
  }

  private static List<Predicate<ChangeData>> predicates(Set<Change.Id> ids) {
    List<Predicate<ChangeData>> r = Lists.newArrayListWithCapacity(ids.size());
    for (Change.Id id : ids) {
      r.add(new LegacyChangeIdPredicate(id));
    }
    return r;
  }

  private final Arguments args;
  private final CurrentUser user;

  IsStarredByPredicate(Arguments args) throws QueryParseException {
    this(args, args.getIdentifiedUser());
  }

  private IsStarredByPredicate(Arguments args, IdentifiedUser user) {
    super(predicates(user.getStarredChanges()));
    this.args = args;
    this.user = user;
  }

  @Override
  public boolean match(final ChangeData object) {
    return user.getStarredChanges().contains(object.getId());
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    return ChangeDataResultSet.change(args.changeDataFactory, args.db,
        args.db.get().changes().get(user.getStarredChanges()));
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
    return 0;
  }

  @Override
  public String toString() {
    String val = describe(user);
    if (val.indexOf(' ') < 0) {
      return ChangeQueryBuilder.FIELD_STARREDBY + ":" + val;
    } else {
      return ChangeQueryBuilder.FIELD_STARREDBY + ":\"" + val + "\"";
    }
  }
}
