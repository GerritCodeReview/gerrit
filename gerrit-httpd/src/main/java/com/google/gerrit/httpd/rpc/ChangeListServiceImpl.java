// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc;

import com.google.gerrit.common.data.AccountDashboardInfo;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.common.data.ChangeListService;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.SingleListChangeInfo;
import com.google.gerrit.common.data.ToggleStarRequest;
import com.google.gerrit.common.errors.InvalidQueryException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeAccess;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.StarredChange;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryRewriter;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangeListServiceImpl extends BaseServiceImplementation implements
    ChangeListService {
  private static final Comparator<ChangeInfo> ID_COMP =
      new Comparator<ChangeInfo>() {
        public int compare(final ChangeInfo o1, final ChangeInfo o2) {
          return o1.getId().get() - o2.getId().get();
        }
      };
  private static final Comparator<ChangeInfo> SORT_KEY_COMP =
      new Comparator<ChangeInfo>() {
        public int compare(final ChangeInfo o1, final ChangeInfo o2) {
          return o2.getSortKey().compareTo(o1.getSortKey());
        }
      };
  private static final Comparator<Change> QUERY_PREV =
      new Comparator<Change>() {
        public int compare(final Change a, final Change b) {
          return a.getSortKey().compareTo(b.getSortKey());
        }
      };
  private static final Comparator<Change> QUERY_NEXT =
      new Comparator<Change>() {
        public int compare(final Change a, final Change b) {
          return b.getSortKey().compareTo(a.getSortKey());
        }
      };

  private final Provider<CurrentUser> currentUser;
  private final ChangeControl.Factory changeControlFactory;
  private final AccountInfoCacheFactory.Factory accountInfoCacheFactory;

  private final ChangeQueryBuilder.Factory queryBuilder;
  private final Provider<ChangeQueryRewriter> queryRewriter;

  @Inject
  ChangeListServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final ChangeControl.Factory changeControlFactory,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final ChangeQueryBuilder.Factory queryBuilder,
      final Provider<ChangeQueryRewriter> queryRewriter) {
    super(schema, currentUser);
    this.currentUser = currentUser;
    this.changeControlFactory = changeControlFactory;
    this.accountInfoCacheFactory = accountInfoCacheFactory;
    this.queryBuilder = queryBuilder;
    this.queryRewriter = queryRewriter;
  }

  private boolean canRead(final Change c, final ReviewDb db) throws OrmException{
    try {
      return changeControlFactory.controlFor(c).isVisible(db);
    } catch (NoSuchChangeException e) {
      return false;
    }
  }

  @Override
  public void allQueryPrev(final String query, final String pos,
      final int pageSize, final AsyncCallback<SingleListChangeInfo> callback) {
    try {
      run(callback, new QueryPrev(pageSize, pos) {
        @Override
        ResultSet<Change> query(ReviewDb db, int lim, String key)
            throws OrmException, InvalidQueryException {
          return searchQuery(db, query, lim, key, QUERY_PREV);
        }
      });
    } catch (InvalidQueryException e) {
      callback.onFailure(e);
    }
  }

  @Override
  public void allQueryNext(final String query, final String pos,
      final int pageSize, final AsyncCallback<SingleListChangeInfo> callback) {
    try {
      run(callback, new QueryNext(pageSize, pos) {
        @Override
        ResultSet<Change> query(ReviewDb db, int lim, String key)
            throws OrmException, InvalidQueryException {
          return searchQuery(db, query, lim, key, QUERY_NEXT);
        }
      });
    } catch (InvalidQueryException e) {
      callback.onFailure(e);
    }
  }

  @SuppressWarnings("unchecked")
  private ResultSet<Change> searchQuery(final ReviewDb db, String query,
      final int limit, final String key, final Comparator<Change> cmp)
      throws OrmException, InvalidQueryException {
    try {
      final ChangeQueryBuilder builder = queryBuilder.create(currentUser.get());
      final Predicate<ChangeData> visibleToMe = builder.is_visible();
      Predicate<ChangeData> q = builder.parse(query);
      q = Predicate.and(q, //
          cmp == QUERY_PREV //
              ? builder.sortkey_after(key) //
              : builder.sortkey_before(key), //
          builder.limit(limit), //
          visibleToMe //
          );

      ChangeQueryRewriter rewriter = queryRewriter.get();
      Predicate<ChangeData> s = rewriter.rewrite(q);
      if (!(s instanceof ChangeDataSource)) {
        s = rewriter.rewrite(Predicate.and(builder.status_open(), q));
      }

      if (s instanceof ChangeDataSource) {
        ArrayList<Change> r = new ArrayList<Change>();
        HashSet<Change.Id> want = new HashSet<Change.Id>();
        for (ChangeData d : ((ChangeDataSource) s).read()) {
          if (d.hasChange()) {
            // Checking visibleToMe here should be unnecessary, the
            // query should have already performed it.  But we don't
            // want to trust the query rewriter that much yet.
            //
            if (visibleToMe.match(d)) {
              r.add(d.getChange());
            }
          } else {
            want.add(d.getId());
          }
        }

        // Here we have to check canRead. Its impossible to
        // do that test without the change object, and it being
        // missing above means we have to compute it ourselves.
        //
        if (!want.isEmpty()) {
          for (Change c : db.changes().get(want)) {
            if (canRead(c, db)) {
              r.add(c);
            }
          }
        }

        Collections.sort(r, cmp);
        return new ListResultSet<Change>(r);
      } else {
        throw new InvalidQueryException("Not Supported", s.toString());
      }
    } catch (QueryParseException e) {
      throw new InvalidQueryException(e.getMessage(), query);
    }
  }

  public void forAccount(final Account.Id id,
      final AsyncCallback<AccountDashboardInfo> callback) {
    final Account.Id me = getAccountId();
    final Account.Id target = id != null ? id : me;
    if (target == null) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }

    run(callback, new Action<AccountDashboardInfo>() {
      public AccountDashboardInfo run(final ReviewDb db) throws OrmException,
          Failure {
        final AccountInfoCacheFactory ac = accountInfoCacheFactory.create();
        final Account user = ac.get(target);
        if (user == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final Set<Change.Id> stars = currentUser.get().getStarredChanges();
        final ChangeAccess changes = db.changes();
        final AccountDashboardInfo d;

        final Set<Change.Id> openReviews = new HashSet<Change.Id>();
        final Set<Change.Id> closedReviews = new HashSet<Change.Id>();
        for (final PatchSetApproval ca : db.patchSetApprovals().openByUser(id)) {
          openReviews.add(ca.getPatchSetId().getParentKey());
        }
        for (final PatchSetApproval ca : db.patchSetApprovals()
            .closedByUser(id)) {
          closedReviews.add(ca.getPatchSetId().getParentKey());
        }

        d = new AccountDashboardInfo(target);
        d.setByOwner(filter(changes.byOwnerOpen(target), stars, ac, db));
        d.setClosed(filter(changes.byOwnerClosed(target), stars, ac, db));

        for (final ChangeInfo c : d.getByOwner()) {
          openReviews.remove(c.getId());
        }
        d.setForReview(filter(changes.get(openReviews), stars, ac, db));
        Collections.sort(d.getForReview(), ID_COMP);

        for (final ChangeInfo c : d.getClosed()) {
          closedReviews.remove(c.getId());
        }
        if (!closedReviews.isEmpty()) {
          d.getClosed().addAll(filter(changes.get(closedReviews), stars, ac, db));
          Collections.sort(d.getClosed(), SORT_KEY_COMP);
        }

        d.setAccounts(ac.create());
        return d;
      }
    });
  }

  public void toggleStars(final ToggleStarRequest req,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException {
        final Account.Id me = getAccountId();
        final Set<Change.Id> existing = currentUser.get().getStarredChanges();
        List<StarredChange> add = new ArrayList<StarredChange>();
        List<StarredChange.Key> remove = new ArrayList<StarredChange.Key>();

        if (req.getAddSet() != null) {
          for (final Change.Id id : req.getAddSet()) {
            if (!existing.contains(id)) {
              add.add(new StarredChange(new StarredChange.Key(me, id)));
            }
          }
        }

        if (req.getRemoveSet() != null) {
          for (final Change.Id id : req.getRemoveSet()) {
            remove.add(new StarredChange.Key(me, id));
          }
        }

        db.starredChanges().insert(add);
        db.starredChanges().deleteKeys(remove);
        return VoidResult.INSTANCE;
      }
    });
  }

  public void myStarredChangeIds(final AsyncCallback<Set<Change.Id>> callback) {
    callback.onSuccess(currentUser.get().getStarredChanges());
  }

  private int safePageSize(final int pageSize) throws InvalidQueryException {
    int maxLimit = currentUser.get().getCapabilities()
      .getRange(GlobalCapability.QUERY_LIMIT)
      .getMax();
    if (maxLimit <= 0) {
      throw new InvalidQueryException("Search Disabled");
    }
    return 0 < pageSize && pageSize <= maxLimit ? pageSize : maxLimit;
  }

  private List<ChangeInfo> filter(final ResultSet<Change> rs,
      final Set<Change.Id> starred, final AccountInfoCacheFactory accts,
      final ReviewDb db) throws OrmException{
    final ArrayList<ChangeInfo> r = new ArrayList<ChangeInfo>();
    for (final Change c : rs) {
      if (canRead(c, db)) {
        final ChangeInfo ci = new ChangeInfo(c);
        accts.want(ci.getOwner());
        ci.setStarred(starred.contains(ci.getId()));
        r.add(ci);
      }
    }
    return r;
  }

  private abstract class QueryNext implements Action<SingleListChangeInfo> {
    protected final String pos;
    protected final int limit;
    protected final int slim;

    QueryNext(final int pageSize, final String pos) throws InvalidQueryException {
      this.pos = pos;
      this.limit = safePageSize(pageSize);
      this.slim = limit + 1;
    }

    public SingleListChangeInfo run(final ReviewDb db) throws OrmException,
        InvalidQueryException {
      final AccountInfoCacheFactory ac = accountInfoCacheFactory.create();
      final SingleListChangeInfo d = new SingleListChangeInfo();
      final Set<Change.Id> starred = currentUser.get().getStarredChanges();

      final ArrayList<ChangeInfo> list = new ArrayList<ChangeInfo>();
      final ResultSet<Change> rs = query(db, slim, pos);
      for (final Change c : rs) {
        if (!canRead(c, db)) {
          continue;
        }
        final ChangeInfo ci = new ChangeInfo(c);
        ac.want(ci.getOwner());
        ci.setStarred(starred.contains(ci.getId()));
        list.add(ci);
        if (list.size() == slim) {
          rs.close();
          break;
        }
      }

      final boolean atEnd = finish(list);
      d.setChanges(list, atEnd);
      d.setAccounts(ac.create());
      return d;
    }

    boolean finish(final ArrayList<ChangeInfo> list) {
      final boolean atEnd = list.size() <= limit;
      if (list.size() == slim) {
        list.remove(limit);
      }
      return atEnd;
    }

    abstract ResultSet<Change> query(final ReviewDb db, final int slim,
        String sortKey) throws OrmException, InvalidQueryException;
  }

  private abstract class QueryPrev extends QueryNext {
    QueryPrev(int pageSize, String pos) throws InvalidQueryException {
      super(pageSize, pos);
    }

    @Override
    boolean finish(final ArrayList<ChangeInfo> list) {
      final boolean atEnd = super.finish(list);
      Collections.reverse(list);
      return atEnd;
    }
  }
}
