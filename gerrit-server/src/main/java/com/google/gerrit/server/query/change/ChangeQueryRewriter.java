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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.query.IntPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryRewriter;
import com.google.gerrit.server.query.RewritePredicate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import java.util.Collection;

public class ChangeQueryRewriter extends QueryRewriter<ChangeData> {
  private static final QueryRewriter.Definition<ChangeData, ChangeQueryRewriter> mydef =
      new QueryRewriter.Definition<ChangeData, ChangeQueryRewriter>(
          ChangeQueryRewriter.class, new ChangeQueryBuilder(
              new ChangeQueryBuilder.Arguments( //
                  new InvalidProvider<ReviewDb>(), //
                  new InvalidProvider<ChangeQueryRewriter>(), //
                  null, null, null, null, null, //
                  null, null, null, null), null));

  private final Provider<ReviewDb> dbProvider;

  @Inject
  ChangeQueryRewriter(Provider<ReviewDb> dbProvider) {
    super(mydef);
    this.dbProvider = dbProvider;
  }

  @Override
  public Predicate<ChangeData> and(Collection<? extends Predicate<ChangeData>> l) {
    return hasSource(l) ? new AndSource(dbProvider, l) : super.and(l);
  }

  @Override
  public Predicate<ChangeData> or(Collection<? extends Predicate<ChangeData>> l) {
    return hasSource(l) ? new OrSource(l) : super.or(l);
  }

  @Rewrite("-status:open")
  @NoCostComputation
  public Predicate<ChangeData> r00_notOpen() {
    return ChangeStatusPredicate.closed(dbProvider);
  }

  @Rewrite("-status:closed")
  @NoCostComputation
  public Predicate<ChangeData> r00_notClosed() {
    return ChangeStatusPredicate.open(dbProvider);
  }

  @SuppressWarnings("unchecked")
  @NoCostComputation
  @Rewrite("-status:merged")
  public Predicate<ChangeData> r00_notMerged() {
    return or(ChangeStatusPredicate.open(dbProvider),
        new ChangeStatusPredicate(dbProvider, Change.Status.ABANDONED));
  }

  @SuppressWarnings("unchecked")
  @NoCostComputation
  @Rewrite("-status:abandoned")
  public Predicate<ChangeData> r00_notAbandoned() {
    return or(ChangeStatusPredicate.open(dbProvider),
        new ChangeStatusPredicate(dbProvider, Change.Status.MERGED));
  }

  @SuppressWarnings("unchecked")
  @NoCostComputation
  @Rewrite("sortkey_before:z A=(age:*)")
  public Predicate<ChangeData> r00_ageToSortKey(@Named("A") AgePredicate a) {
    String cut = ChangeUtil.sortKey(a.getCut(), Integer.MAX_VALUE);
    return and(new SortKeyPredicate.Before(dbProvider, cut), a);
  }

  @NoCostComputation
  @Rewrite("A=(limit:*) B=(limit:*)")
  public Predicate<ChangeData> r00_smallestLimit(
      @Named("A") IntPredicate<ChangeData> a,
      @Named("B") IntPredicate<ChangeData> b) {
    return a.intValue() <= b.intValue() ? a : b;
  }

  @NoCostComputation
  @Rewrite("A=(sortkey_before:*) B=(sortkey_before:*)")
  public Predicate<ChangeData> r00_oldestSortKey(
      @Named("A") SortKeyPredicate.Before a,
      @Named("B") SortKeyPredicate.Before b) {
    return a.getValue().compareTo(b.getValue()) <= 0 ? a : b;
  }

  @NoCostComputation
  @Rewrite("A=(sortkey_after:*) B=(sortkey_after:*)")
  public Predicate<ChangeData> r00_newestSortKey(
      @Named("A") SortKeyPredicate.After a, @Named("B") SortKeyPredicate.After b) {
    return a.getValue().compareTo(b.getValue()) >= 0 ? a : b;
  }

  @Rewrite("status:open P=(project:*) B=(branch:*)")
  public Predicate<ChangeData> r05_byBranchOpen(
      @Named("P") final ProjectPredicate p,
      @Named("B") final BranchPredicate b) {
    return new ChangeSource(500) {
      @Override
      ResultSet<Change> scan(ChangeAccess a)
          throws OrmException {
        return a.byBranchOpenAll(
            new Branch.NameKey(p.getValueKey(), b.getValue()));
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isOpen()
            && p.match(cd)
            && b.match(cd);
      }
    };
  }

  @Rewrite("status:merged P=(project:*) B=(branch:*) S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r05_byBranchMergedPrev(
      @Named("P") final ProjectPredicate p,
      @Named("B") final BranchPredicate b,
      @Named("S") final SortKeyPredicate.After s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(40000, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.byBranchClosedPrev(Change.Status.MERGED.getCode(), //
            new Branch.NameKey(p.getValueKey(), b.getValue()), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.MERGED
            && p.match(cd) //
            && b.match(cd) //
            && s.match(cd);
      }
    };
  }

  @Rewrite("status:merged P=(project:*) B=(branch:*) S=(sortkey_before:*) L=(limit:*)")
  public Predicate<ChangeData> r05_byBranchMergedNext(
      @Named("P") final ProjectPredicate p,
      @Named("B") final BranchPredicate b,
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(40000, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.byBranchClosedNext(Change.Status.MERGED.getCode(), //
            new Branch.NameKey(p.getValueKey(), b.getValue()), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.MERGED
            && p.match(cd) //
            && b.match(cd) //
            && s.match(cd);
      }
    };
  }

  @Rewrite("status:open P=(project:*) S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r10_byProjectOpenPrev(
      @Named("P") final ProjectPredicate p,
      @Named("S") final SortKeyPredicate.After s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(500, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.byProjectOpenPrev(p.getValueKey(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isOpen() //
            && p.match(cd) //
            && s.match(cd);
      }
    };
  }

  @Rewrite("status:open P=(project:*) S=(sortkey_before:*) L=(limit:*)")
  public Predicate<ChangeData> r10_byProjectOpenNext(
      @Named("P") final ProjectPredicate p,
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(500, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.byProjectOpenNext(p.getValueKey(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isOpen() //
            && p.match(cd) //
            && s.match(cd);
      }
    };
  }

  @Rewrite("status:merged P=(project:*) S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r10_byProjectMergedPrev(
      @Named("P") final ProjectPredicate p,
      @Named("S") final SortKeyPredicate.After s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(40000, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.byProjectClosedPrev(Change.Status.MERGED.getCode(), //
            p.getValueKey(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.MERGED
            && p.match(cd) //
            && s.match(cd);
      }
    };
  }

  @Rewrite("status:merged P=(project:*) S=(sortkey_before:*) L=(limit:*)")
  public Predicate<ChangeData> r10_byProjectMergedNext(
      @Named("P") final ProjectPredicate p,
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(40000, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.byProjectClosedNext(Change.Status.MERGED.getCode(), //
            p.getValueKey(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.MERGED
            && p.match(cd) //
            && s.match(cd);
      }
    };
  }

  @Rewrite("status:abandoned P=(project:*) S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r10_byProjectAbandonedPrev(
      @Named("P") final ProjectPredicate p,
      @Named("S") final SortKeyPredicate.After s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(40000, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.byProjectClosedPrev(Change.Status.ABANDONED.getCode(), //
            p.getValueKey(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.ABANDONED
            && p.match(cd) //
            && s.match(cd);
      }
    };
  }

  @Rewrite("status:abandoned P=(project:*) S=(sortkey_before:*) L=(limit:*)")
  public Predicate<ChangeData> r10_byProjectAbandonedNext(
      @Named("P") final ProjectPredicate p,
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(40000, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.byProjectClosedNext(Change.Status.ABANDONED.getCode(), //
            p.getValueKey(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.ABANDONED
            && p.match(cd) //
            && s.match(cd);
      }
    };
  }

  @Rewrite("status:open S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r20_byOpenPrev(
      @Named("S") final SortKeyPredicate.After s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(2000, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.allOpenPrev(key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isOpen() && s.match(cd);
      }
    };
  }

  @Rewrite("status:open S=(sortkey_before:*) L=(limit:*)")
  public Predicate<ChangeData> r20_byOpenNext(
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(2000, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.allOpenNext(key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isOpen() && s.match(cd);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:merged S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r20_byMergedPrev(
      @Named("S") final SortKeyPredicate.After s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(50000, s.getValue(), l.intValue()) {
      {
        init("r20_byMergedPrev", s, l);
      }

      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.allClosedPrev(Change.Status.MERGED.getCode(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.MERGED
            && s.match(cd);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:merged S=(sortkey_before:*) L=(limit:*)")
  public Predicate<ChangeData> r20_byMergedNext(
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(50000, s.getValue(), l.intValue()) {
      {
        init("r20_byMergedNext", s, l);
      }

      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.allClosedNext(Change.Status.MERGED.getCode(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.MERGED
            && s.match(cd);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:abandoned S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r20_byAbandonedPrev(
      @Named("S") final SortKeyPredicate.After s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(50000, s.getValue(), l.intValue()) {
      {
        init("r20_byAbandonedPrev", s, l);
      }

      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.allClosedPrev(Change.Status.ABANDONED.getCode(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.ABANDONED
            && s.match(cd);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:abandoned S=(sortkey_before:*) L=(limit:*)")
  public Predicate<ChangeData> r20_byAbandonedNext(
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return new PaginatedSource(50000, s.getValue(), l.intValue()) {
      {
        init("r20_byAbandonedNext", s, l);
      }

      @Override
      ResultSet<Change> scan(ChangeAccess a, String key, int limit)
          throws OrmException {
        return a.allClosedNext(Change.Status.ABANDONED.getCode(), key, limit);
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.ABANDONED
            && s.match(cd);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:closed S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r20_byClosedPrev(
      @Named("S") final SortKeyPredicate.After s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return or(r20_byMergedPrev(s, l), r20_byAbandonedPrev(s, l));
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:closed S=(sortkey_after:*) L=(limit:*)")
  public Predicate<ChangeData> r20_byClosedNext(
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<ChangeData> l) {
    return or(r20_byMergedNext(s, l), r20_byAbandonedNext(s, l));
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:open O=(owner:*)")
  public Predicate<ChangeData> r25_byOwnerOpen(
      @Named("O") final OwnerPredicate o) {
    return new ChangeSource(50) {
      {
        init("r25_byOwnerOpen", o);
      }

      @Override
      ResultSet<Change> scan(ChangeAccess a) throws OrmException {
        return a.byOwnerOpen(o.getAccountId());
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isOpen() && o.match(cd);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:closed O=(owner:*)")
  public Predicate<ChangeData> r25_byOwnerClosed(
      @Named("O") final OwnerPredicate o) {
    return new ChangeSource(5000) {
      {
        init("r25_byOwnerClosed", o);
      }

      @Override
      ResultSet<Change> scan(ChangeAccess a) throws OrmException {
        return a.byOwnerClosedAll(o.getAccountId());
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isClosed() && o.match(cd);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("O=(owner:*)")
  public Predicate<ChangeData> r26_byOwner(@Named("O") OwnerPredicate o) {
    return or(r25_byOwnerOpen(o), r25_byOwnerClosed(o));
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:open R=(reviewer:*)")
  public Predicate<ChangeData> r30_byReviewerOpen(
      @Named("R") final ReviewerPredicate r) {
    return new Source() {
      {
        init("r30_byReviewerOpen", r);
      }

      @Override
      public ResultSet<ChangeData> read() throws OrmException {
        return ChangeDataResultSet.patchSetApproval(dbProvider.get()
            .patchSetApprovals().openByUser(r.getAccountId()));
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        Change change = cd.change(dbProvider);
        return change != null && change.getStatus().isOpen() && r.match(cd);
      }

      @Override
      public int getCardinality() {
        return 50;
      }

      @Override
      public int getCost() {
        return ChangeCosts.cost(ChangeCosts.APPROVALS_SCAN, getCardinality());
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:closed R=(reviewer:*)")
  public Predicate<ChangeData> r30_byReviewerClosed(
      @Named("R") final ReviewerPredicate r) {
    return new Source() {
      {
        init("r30_byReviewerClosed", r);
      }

      @Override
      public ResultSet<ChangeData> read() throws OrmException {
        return ChangeDataResultSet.patchSetApproval(dbProvider.get()
            .patchSetApprovals().closedByUserAll(r.getAccountId()));
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        Change change = cd.change(dbProvider);
        return change != null && change.getStatus().isClosed() && r.match(cd);
      }

      @Override
      public int getCardinality() {
        return 5000;
      }

      @Override
      public int getCost() {
        return ChangeCosts.cost(ChangeCosts.APPROVALS_SCAN, getCardinality());
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:closed (O=(owner:*) OR R=(reviewer:*)) limit:10")
  public Predicate<ChangeData> r30_byRecentlyClosed(
      @Named("R") final ReviewerPredicate r,
      @Named("O") final OwnerPredicate o) {
    return or(new Source() {
      {
        init("byReviewerRecentlyClosed", r);
      }

      @Override
      public ResultSet<ChangeData> read() throws OrmException {
        return ChangeDataResultSet.patchSetApproval(dbProvider.get()
            .patchSetApprovals().closedByUser(r.getAccountId()));
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        Change change = cd.change(dbProvider);
        return change != null && change.getStatus().isClosed() && r.match(cd);
      }

      @Override
      public int getCardinality() {
        return 10;
      }

      @Override
      public int getCost() {
        return ChangeCosts.cost(ChangeCosts.APPROVALS_SCAN, getCardinality());
      }
    },
    new ChangeSource(10) {
      {
        init("byOwnerRecentlyClosed", o);
      }

      @Override
      ResultSet<Change> scan(ChangeAccess a) throws OrmException {
        return a.byOwnerClosed(o.getAccountId());
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus().isClosed() && o.match(cd);
      }
    });
  }

  @SuppressWarnings("unchecked")
  @Rewrite("R=(reviewer:*)")
  public Predicate<ChangeData> r31_byReviewer(
      @Named("R") final ReviewerPredicate r) {
    return or(r30_byReviewerOpen(r), r30_byReviewerClosed(r));
  }

  @Rewrite("status:submitted")
  public Predicate<ChangeData> r99_allSubmitted() {
    return new ChangeSource(50) {
      @Override
      ResultSet<Change> scan(ChangeAccess a) throws OrmException {
        return a.allSubmitted();
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return cd.change(dbProvider).getStatus() == Change.Status.SUBMITTED;
      }
    };
  }

  @Rewrite("P=(project:*)")
  public Predicate<ChangeData> r99_byProject(
      @Named("P") final ProjectPredicate p) {
    return new ChangeSource(1000000) {
      @Override
      ResultSet<Change> scan(ChangeAccess a) throws OrmException {
        return a.byProject(p.getValueKey());
      }

      @Override
      public boolean match(ChangeData cd) throws OrmException {
        return p.match(cd);
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
    @Override
    public boolean hasChange() {
      return false;
    }
  }

  private abstract class ChangeSource extends Source {
    private final int cardinality;

    ChangeSource(int card) {
      this.cardinality = card;
    }

    abstract ResultSet<Change> scan(ChangeAccess a) throws OrmException;

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      return ChangeDataResultSet.change(scan(dbProvider.get().changes()));
    }

    @Override
    public boolean hasChange() {
      return true;
    }

    @Override
    public int getCardinality() {
      return cardinality;
    }

    @Override
    public int getCost() {
      return ChangeCosts.cost(ChangeCosts.CHANGES_SCAN, getCardinality());
    }
  }

  private abstract class PaginatedSource extends ChangeSource implements
      Paginated {
    private final String startKey;
    private final int limit;

    PaginatedSource(int card, String start, int lim) {
      super(card);
      this.startKey = start;
      this.limit = lim;
    }

    @Override
    public int limit() {
      return limit;
    }

    @Override
    ResultSet<Change> scan(ChangeAccess a) throws OrmException {
      return scan(a, startKey, limit);
    }

    @Override
    public ResultSet<ChangeData> restart(ChangeData last) throws OrmException {
      return ChangeDataResultSet.change(scan(dbProvider.get().changes(), //
          last.change(dbProvider).getSortKey(), //
          limit));
    }

    abstract ResultSet<Change> scan(ChangeAccess a, String key, int limit)
        throws OrmException;
  }

  private static final class InvalidProvider<T> implements Provider<T> {
    @Override
    public T get() {
      throw new OutOfScopeException("Not available at init");
    }
  }
}
