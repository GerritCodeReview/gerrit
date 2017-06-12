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
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class IsWatchedByPredicate extends AndPredicate<ChangeData> {
  private static String describe(CurrentUser user) {
    if (user.isIdentifiedUser()) {
      return user.getAccountId().toString();
    }
    return user.toString();
  }

  private final CurrentUser user;

  IsWatchedByPredicate(ChangeQueryBuilder.Arguments args, boolean checkIsVisible)
      throws QueryParseException {
    super(filters(args, checkIsVisible));
    this.user = args.getUser();
  }

  private static List<Predicate<ChangeData>> filters(
      ChangeQueryBuilder.Arguments args, boolean checkIsVisible) throws QueryParseException {
    List<Predicate<ChangeData>> r = new ArrayList<>();
    ChangeQueryBuilder builder = new ChangeQueryBuilder(args);
    for (ProjectWatchKey w : getWatches(args)) {
      Predicate<ChangeData> f = null;
      if (w.filter() != null) {
        try {
          f = builder.parse(w.filter());
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
      if (w.project().equals(args.allProjectsName)) {
        p = null;
      } else {
        p = builder.project(w.project().get());
      }

      if (p != null && f != null) {
        r.add(and(p, f));
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

  private static Collection<ProjectWatchKey> getWatches(ChangeQueryBuilder.Arguments args)
      throws QueryParseException {
    CurrentUser user = args.getUser();
    if (user.isIdentifiedUser()) {
      return args.accountCache.get(args.getUser().getAccountId()).getProjectWatches().keySet();
    }
    return Collections.<ProjectWatchKey>emptySet();
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
    }
    return ChangeQueryBuilder.FIELD_WATCHEDBY + ":\"" + val + "\"";
  }
}
