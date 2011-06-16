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

import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class IsWatchedByPredicate extends OperatorPredicate<ChangeData> {
  private static String describe(CurrentUser user) {
    if (user instanceof IdentifiedUser) {
      return ((IdentifiedUser) user).getAccountId().toString();
    }
    return user.toString();
  }

  private final ChangeQueryBuilder.Arguments args;
  private final CurrentUser user;

  private Map<Project.NameKey, List<Predicate<ChangeData>>> rules;

  IsWatchedByPredicate(ChangeQueryBuilder.Arguments args, CurrentUser user) {
    super(ChangeQueryBuilder.FIELD_WATCHEDBY, describe(user));
    this.args = args;
    this.user = user;
  }

  @Override
  public boolean match(final ChangeData cd) throws OrmException {
    if (rules == null) {
      ChangeQueryBuilder builder = new ChangeQueryBuilder(args, user);
      rules = new HashMap<Project.NameKey, List<Predicate<ChangeData>>>();
      for (AccountProjectWatch w : user.getNotificationFilters()) {
        List<Predicate<ChangeData>> list = rules.get(w.getProjectNameKey());
        if (list == null) {
          list = new ArrayList<Predicate<ChangeData>>(4);
          rules.put(w.getProjectNameKey(), list);
        }

        Predicate<ChangeData> p = compile(builder, w);
        if (p != null) {
          list.add(p);
        }
      }
    }

    if (rules.isEmpty()) {
      return false;
    }

    Change change = cd.change(args.dbProvider);
    if (change == null) {
      return false;
    }

    Project.NameKey project = change.getDest().getParentKey();
    List<Predicate<ChangeData>> list = rules.get(project);
    if (list == null) {
      list = rules.get(args.allProjectsName);
    }
    if (list != null) {
      for (Predicate<ChangeData> p : list) {
        if (p.match(cd)) {
          return true;
        }
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private Predicate<ChangeData> compile(ChangeQueryBuilder builder,
      AccountProjectWatch w) {
    Predicate<ChangeData> p = builder.is_visible();
    if (w.getFilter() != null) {
      try {
        p = Predicate.and(builder.parse(w.getFilter()), p);
        if (builder.find(p, IsWatchedByPredicate.class) != null) {
          // If the query is going to infinite loop, assume it
          // will never match and return null. Yes this test
          // prevents you from having a filter that matches what
          // another user is filtering on. :-)
          //
          return null;
        }
        p = args.rewriter.get().rewrite(p);
      } catch (QueryParseException e) {
        return null;
      }
    }
    return p;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
