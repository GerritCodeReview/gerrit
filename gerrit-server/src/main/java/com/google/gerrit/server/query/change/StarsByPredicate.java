// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.collect.Multimap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.ArrayList;
import java.util.List;

public class StarsByPredicate extends OrPredicate<ChangeData>
    implements ChangeDataSource {

  private static String describe(CurrentUser user) {
    if (user.isIdentifiedUser()) {
      return user.getAccountId().toString();
    }
    return user.toString();
  }

  private static List<Predicate<ChangeData>> predicates(
      Schema<ChangeData> schema, Iterable<Change.Id> ids) {
    List<Predicate<ChangeData>> r = new ArrayList<>();
    for (Change.Id id : ids) {
      r.add(new LegacyChangeIdPredicate(schema, id));
    }
    return r;
  }

  private final Arguments args;
  private final String label;
  private final CurrentUser user;

  StarsByPredicate(Arguments args) throws QueryParseException {
    this(args, null);
  }

  StarsByPredicate(Arguments args, String label) throws QueryParseException {
    this(args, label, args.getIdentifiedUser());
  }

  private StarsByPredicate(Arguments args, String label, IdentifiedUser user) {
    super(predicates(args.getSchema(), label != null
        ? args.starredChangesUtil.byAccount(user.getAccountId(), label)
        : args.starredChangesUtil.byAccount(user.getAccountId()).keySet()));
    this.args = args;
    this.label = label;
    this.user = user;
  }

  @Override
  public boolean match(ChangeData object) {
    Multimap<Change.Id, String> byAccount =
        args.starredChangesUtil.byAccount(user.getAccountId());
    if (label != null) {
      return byAccount.get(object.getId()).contains(label);
    }

    return byAccount.keySet().contains(object.getId());
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
      return ChangeQueryBuilder.FIELD_STAR + ":" + val;
    } else {
      return ChangeQueryBuilder.FIELD_STAR + ":\"" + val + "\"";
    }
  }
}
