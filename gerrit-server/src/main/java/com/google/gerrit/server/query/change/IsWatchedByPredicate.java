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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;

import java.util.List;

class IsWatchedByPredicate extends AndPredicate<ChangeData> {
  private static String describe(CurrentUser user) {
    if (user.isIdentifiedUser()) {
      return ((IdentifiedUser) user).getAccountId().toString();
    }
    return user.toString();
  }

  private final CurrentUser user;

  IsWatchedByPredicate(ChangeQueryBuilder.Arguments args,
      CurrentUser user,
      boolean checkIsVisible) {
    super(filters(args, user, checkIsVisible));
    this.user = user;
  }

  private static List<Predicate<ChangeData>> filters(
      ChangeQueryBuilder.Arguments args,
      CurrentUser user,
      boolean checkIsVisible) {
    List<Predicate<ChangeData>> r = Lists.newArrayList();
    ChangeQueryBuilder builder = new ChangeQueryBuilder(args, user);
    for (AccountProjectWatch w : user.getNotificationFilters()) {
      Predicate<ChangeData> f = null;
      if (w.getFilter() != null) {
        try {
          f = builder.parse(w.getFilter());
          if (QueryBuilder.find(f, IsWatchedByPredicate.class) != null) {
            // If the query is going to infinite loop, assume it
            // will never match and return null. Yes this test
            // prevents you from having a filter that matches what
            // another user is filtering on. :-)
           continue;
          }
        } catch (QueryParseException e) {
          continue;
        }
      }

      Predicate<ChangeData> p;
      if (w.getProjectNameKey().equals(args.allProjectsName)) {
        p = null;
      } else {
        p = builder.project(w.getProjectNameKey().get());
      }

      if (p != null && f != null) {
        @SuppressWarnings("unchecked")
        Predicate<ChangeData> andPredicate = and(p, f);
        r.add(andPredicate);
      } else if (p != null) {
        r.add(p);
      } else if (f != null) {
        r.add(f);
      } else {
        r.add(builder.status_open());
      }
    }
    if (r.isEmpty()) {
      return none();
    } else if (checkIsVisible) {
      return ImmutableList.of(or(r), builder.is_visible());
    } else {
      return ImmutableList.of(or(r));
    }
  }

  private static List<Predicate<ChangeData>> none() {
    Predicate<ChangeData> any = any();
    return ImmutableList.of(not(any));
  }

  @Override
  public int getCost() {
    return 1;
  }

  @Override
  public String toString() {
    String val = describe(user);
    if (val.indexOf(' ') < 0) {
      return ChangeQueryBuilder.FIELD_WATCHEDBY + ":" + val;
    } else {
      return ChangeQueryBuilder.FIELD_WATCHEDBY + ":\"" + val + "\"";
    }
  }
}
