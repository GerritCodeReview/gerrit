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

import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.query.IntPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryRewriter;
import com.google.gerrit.server.query.RewritePredicate;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import java.util.Collection;

public class ChangeQueryRewriter extends QueryRewriter<ChangeData> {
  private static final QueryRewriter.Definition<ChangeData, ChangeQueryRewriter> mydef =
      new QueryRewriter.Definition<ChangeData, ChangeQueryRewriter>(
          ChangeQueryRewriter.class, new ChangeQueryBuilder(
              new InvalidProvider<ReviewDb>(),
              new InvalidProvider<CurrentUser>(), null, null, null));

  private final Provider<ReviewDb> dbProvider;

  @Inject
  ChangeQueryRewriter(Provider<ReviewDb> dbProvider) {
    super(mydef);
    this.dbProvider = dbProvider;
  }

  @Override
  public Predicate<ChangeData> and(Collection<? extends Predicate<ChangeData>> l) {
    return hasSource(l) ? new AndSource(l) : super.and(l);
  }

  @Override
  public Predicate<ChangeData> or(Collection<? extends Predicate<ChangeData>> l) {
    return hasSource(l) ? new OrSource(l) : super.or(l);
  }

  @Rewrite("status:open S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r1_byOpenPrev(
      @Named("S") final SortKeyPredicate.After s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new Source() {
      @Override
      public ResultSet<ChangeData> read() throws OrmException {
        return ChangeDataResultSet.change(dbProvider.get().changes()
            .allOpenPrev(s.getValue(), l.intValue()));
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isOpen() && s.match(cd);
      }
    };
  }

  @Rewrite("status:open S=(sortkey_before:*) L=(limit:*)")
  public Predicate<ChangeData> r1_byOpenNext(
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new Source() {
      @Override
      public ResultSet<ChangeData> read() throws OrmException {
        return ChangeDataResultSet.change(dbProvider.get().changes()
            .allOpenNext(s.getValue(), l.intValue()));
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isOpen() && s.match(cd);
      }
    };
  }

  @Rewrite("C=(commit:*)")
  public Predicate<ChangeData> r2_commit(
      @Named("C") final CommitPredicate commit) {
    return new Source() {
      @Override
      public ResultSet<ChangeData> read() throws OrmException {
        final RevId id = new RevId(commit.getValue());
        if (id.isComplete()) {
          return ChangeDataResultSet.patchSet(dbProvider.get().patchSets()
              .byRevision(id));
        } else {
          return ChangeDataResultSet.patchSet(dbProvider.get().patchSets()
              .byRevisionRange(id, id.max()));
        }
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return commit.match(cd);
      }
    };
  }

  private static boolean hasSource(Collection<? extends Predicate<ChangeData>> l) {
    for (Predicate<ChangeData> p : l) {
      if (p instanceof ChangeDataSource) {
        return true;
      }
    }
    return false;
  }

  private abstract static class Source extends RewritePredicate<ChangeData>
      implements ChangeDataSource {
  }

  private static final class InvalidProvider<T> implements Provider<T> {
    @Override
    public T get() {
      throw new OutOfScopeException("Not available at init");
    }
  }
}
