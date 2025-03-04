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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.ProjectWatchKey;
import com.google.gerrit.index.query.AndPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.mail.send.ProjectWatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class IsWatchedByPredicate extends AndPredicate<ChangeData> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected static String describe(CurrentUser user) {
    if (user.isIdentifiedUser()) {
      return user.getAccountId().toString();
    }
    return user.toString();
  }

  protected final CurrentUser user;

  public IsWatchedByPredicate(ChangeQueryBuilder.Arguments args) throws QueryParseException {
    super(filters(args));
    this.user = args.getUser();
  }

  protected static ImmutableList<Predicate<ChangeData>> filters(ChangeQueryBuilder.Arguments args)
      throws QueryParseException {
    List<Predicate<ChangeData>> r = new ArrayList<>();
    ProjectWatch.WatcherChangeQueryBuilder builder =
        new ProjectWatch.WatcherChangeQueryBuilder(args);
    for (ProjectWatchKey w : getWatches(args)) {
      Predicate<ChangeData> f = null;
      if (w.filter() != null) {
        try {
          f = builder.parse(w.filter());
        } catch (QueryParseException e) {
          logger.atFine().log(
              "Ignoring non-parseable filter of project watch %s: %s", w, e.getMessage());
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
        r.add(builder.statusOpen());
      }
    }
    if (r.isEmpty()) {
      return ImmutableList.of(ChangeIndexPredicate.none());
    }
    return ImmutableList.of(or(r));
  }

  protected static Set<ProjectWatchKey> getWatches(ChangeQueryBuilder.Arguments args)
      throws QueryParseException {
    CurrentUser user = args.getUser();
    if (user.isIdentifiedUser()) {
      return user.asIdentifiedUser().state().projectWatches().keySet();
    }
    return Collections.emptySet();
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
