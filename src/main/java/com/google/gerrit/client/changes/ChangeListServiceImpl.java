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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.AccountDashboardInfo;
import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.data.SingleListChangeInfo;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeAccess;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.RevId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.StarredChange;
import com.google.gerrit.client.reviewdb.Change.Id;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.Transaction;
import com.google.gwtorm.client.impl.ListResultSet;

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

  private static final int MAX_PER_PAGE = 100;

  private static int safePageSize(final int pageSize) {
    return 0 < pageSize && pageSize <= MAX_PER_PAGE ? pageSize : MAX_PER_PAGE;
  }

  public void allOpenPrev(final String pos, final int pageSize,
      final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new QueryPrev(pageSize, pos) {
      @Override
      ResultSet<Change> query(ReviewDb db, int slim, String sortKey)
          throws OrmException {
        return db.changes().allOpenPrev(sortKey, slim);
      }
    });
  }

  public void allOpenNext(final String pos, final int pageSize,
      final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new QueryNext(pageSize, pos) {
      @Override
      ResultSet<Change> query(ReviewDb db, int slim, String sortKey)
          throws OrmException {
        return db.changes().allOpenNext(sortKey, slim);
      }
    });
  }

  public void byProjectOpenPrev(final Project.NameKey project,
      final String pos, final int pageSize,
      final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new QueryPrev(pageSize, pos) {
      @Override
      ResultSet<Change> query(ReviewDb db, int slim, String sortKey)
          throws OrmException {
        return db.changes().byProjectOpenPrev(project, sortKey, slim);
      }
    });
  }

  public void byProjectOpenNext(final Project.NameKey project,
      final String pos, final int pageSize,
      final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new QueryNext(pageSize, pos) {
      @Override
      ResultSet<Change> query(ReviewDb db, int slim, String sortKey)
          throws OrmException {
        return db.changes().byProjectOpenNext(project, sortKey, slim);
      }
    });
  }

  public void allClosedPrev(final Change.Status s, final String pos,
      final int pageSize, final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new QueryPrev(pageSize, pos) {
      @Override
      ResultSet<Change> query(ReviewDb db, int lim, String key)
          throws OrmException {
        return db.changes().allClosedPrev(s.getCode(), key, lim);
      }
    });
  }

  public void allClosedNext(final Change.Status s, final String pos,
      final int pageSize, final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new QueryNext(pageSize, pos) {
      @Override
      ResultSet<Change> query(ReviewDb db, int lim, String key)
          throws OrmException {
        return db.changes().allClosedNext(s.getCode(), key, lim);
      }
    });
  }

  @Override
  public void allQueryPrev(final String query, final String pos,
      final int pageSize, final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new QueryPrev(pageSize, pos) {
      @Override
      ResultSet<Change> query(ReviewDb db, int lim, String key)
          throws OrmException {
        return searchQuery(db, query, lim, key, QUERY_PREV);
      }
    });
  }

  @Override
  public void allQueryNext(final String query, final String pos,
      final int pageSize, final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new QueryNext(pageSize, pos) {
      @Override
      ResultSet<Change> query(ReviewDb db, int lim, String key)
          throws OrmException {
        return searchQuery(db, query, lim, key, QUERY_NEXT);
      }
    });
  }

  private ResultSet<Change> searchQuery(final ReviewDb db, String query,
      final int limit, final String key, final Comparator<Change> cmp)
      throws OrmException {
    final HashSet<Change.Id> want = new HashSet<Change.Id>();
    query = query.trim();

    if (query.matches("^[1-9][0-9]*$")) {
      want.add(Change.Id.parse(query));

    } else if (query.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      final RevId id = new RevId(query);
      final ResultSet<PatchSet> patches;
      if (id.isComplete()) {
        patches = db.patchSets().byRevision(id);
      } else {
        patches = db.patchSets().byRevisionRange(id, id.max());
      }
      for (PatchSet p : patches) {
        want.add(p.getId().getParentKey());
      }
    }

    if (want.isEmpty()) {
      return new ListResultSet<Change>(Collections.<Change> emptyList());
    }

    List<Change> result = new ArrayList<Change>();
    final ResultSet<Change> rs = db.changes().get(want);
    if (cmp == QUERY_PREV) {
      for (Change c : rs) {
        if (c.getSortKey().compareTo(key) > 0) {
          result.add(c);
        }
      }
    } else /* cmp == QUERY_NEXT */{
      for (Change c : rs) {
        if (c.getSortKey().compareTo(key) < 0) {
          result.add(c);
        }
      }
    }
    Collections.sort(result, cmp);
    if (limit < result.size()) {
      result = result.subList(0, limit);
    }
    return new ListResultSet<Change>(result);
  }

  public void forAccount(final Account.Id id,
      final AsyncCallback<AccountDashboardInfo> callback) {
    final Account.Id me = Common.getAccountId();
    final Account.Id target = id != null ? id : me;
    if (target == null) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }

    run(callback, new Action<AccountDashboardInfo>() {
      public AccountDashboardInfo run(final ReviewDb db) throws OrmException,
          Failure {
        final AccountInfoCacheFactory ac = new AccountInfoCacheFactory(db);
        final Account user = ac.get(target);
        if (user == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final Set<Change.Id> stars = starredBy(db, me);
        final ChangeAccess changes = db.changes();
        final AccountDashboardInfo d;

        final Set<Change.Id> openReviews = new HashSet<Change.Id>();
        final Set<Change.Id> closedReviews = new HashSet<Change.Id>();
        for (final ChangeApproval ca : db.changeApprovals().openByUser(id)) {
          openReviews.add(ca.getChangeId());
        }
        for (final ChangeApproval ca : db.changeApprovals().closedByUser(id)) {
          closedReviews.add(ca.getChangeId());
        }

        d = new AccountDashboardInfo(target);
        d.setByOwner(filter(changes.byOwnerOpen(target), stars, ac));
        d.setClosed(filter(changes.byOwnerClosed(target), stars, ac));

        for (final ChangeInfo c : d.getByOwner()) {
          openReviews.remove(c.getId());
        }
        d.setForReview(filter(changes.get(openReviews), stars, ac));
        Collections.sort(d.getForReview(), ID_COMP);

        for (final ChangeInfo c : d.getClosed()) {
          closedReviews.remove(c.getId());
        }
        if (!closedReviews.isEmpty()) {
          d.getClosed().addAll(filter(changes.get(closedReviews), stars, ac));
          Collections.sort(d.getClosed(), SORT_KEY_COMP);
        }

        d.setAccounts(ac.create());
        return d;
      }
    });
  }

  public void myStarredChanges(
      final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new Action<SingleListChangeInfo>() {
      public SingleListChangeInfo run(final ReviewDb db) throws OrmException {
        final Account.Id me = Common.getAccountId();
        final AccountInfoCacheFactory ac = new AccountInfoCacheFactory(db);
        final SingleListChangeInfo d = new SingleListChangeInfo();
        final Set<Change.Id> starred = starredBy(db, me);
        d.setChanges(filter(db.changes().get(starred), starred, ac));
        Collections.sort(d.getChanges(), new Comparator<ChangeInfo>() {
          public int compare(final ChangeInfo o1, final ChangeInfo o2) {
            return o1.getLastUpdatedOn().compareTo(o2.getLastUpdatedOn());
          }
        });
        d.setAccounts(ac.create());
        return d;
      }
    });
  }

  public void myDraftChanges(final AsyncCallback<SingleListChangeInfo> callback) {
    run(callback, new Action<SingleListChangeInfo>() {
      public SingleListChangeInfo run(final ReviewDb db) throws OrmException {
        final Account.Id me = Common.getAccountId();
        final AccountInfoCacheFactory ac = new AccountInfoCacheFactory(db);
        final SingleListChangeInfo d = new SingleListChangeInfo();
        final Set<Change.Id> starred = starredBy(db, me);
        final Set<Change.Id> drafted = draftedBy(db, me);
        d.setChanges(filter(db.changes().get(drafted), starred, ac));
        Collections.sort(d.getChanges(), new Comparator<ChangeInfo>() {
          public int compare(final ChangeInfo o1, final ChangeInfo o2) {
            return o1.getLastUpdatedOn().compareTo(o2.getLastUpdatedOn());
          }
        });
        d.setAccounts(ac.create());
        return d;
      }
    });
  }

  public void toggleStars(final ToggleStarRequest req,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException {
        final Account.Id me = Common.getAccountId();
        final Set<Change.Id> existing = starredBy(db, me);
        final ArrayList<StarredChange> add = new ArrayList<StarredChange>();
        final ArrayList<StarredChange> remove = new ArrayList<StarredChange>();

        if (req.getAddSet() != null) {
          for (final Change.Id id : req.getAddSet()) {
            if (!existing.contains(id)) {
              add.add(new StarredChange(new StarredChange.Key(me, id)));
            }
          }
        }

        if (req.getRemoveSet() != null) {
          for (final Change.Id id : req.getRemoveSet()) {
            if (existing.contains(id)) {
              remove.add(new StarredChange(new StarredChange.Key(me, id)));
            }
          }
        }

        if (!add.isEmpty() || !remove.isEmpty()) {
          final Transaction txn = db.beginTransaction();
          db.starredChanges().insert(add);
          db.starredChanges().delete(remove);
          txn.commit();
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  public void myStarredChangeIds(final AsyncCallback<Set<Change.Id>> callback) {
    run(callback, new Action<Set<Change.Id>>() {
      public Set<Id> run(final ReviewDb db) throws OrmException {
        return starredBy(db, Common.getAccountId());
      }
    });
  }

  private static List<ChangeInfo> filter(final ResultSet<Change> rs,
      final Set<Change.Id> starred, final AccountInfoCacheFactory accts) {
    final ArrayList<ChangeInfo> r = new ArrayList<ChangeInfo>();
    for (final Change c : rs) {
      if (canRead(c)) {
        final ChangeInfo ci = new ChangeInfo(c, accts);
        ci.setStarred(starred.contains(ci.getId()));
        r.add(ci);
      }
    }
    return r;
  }

  static Set<Change.Id> starredBy(final ReviewDb db, final Account.Id me)
      throws OrmException {
    final Set<Change.Id> existing = new HashSet<Change.Id>();
    if (me != null) {
      for (final StarredChange sc : db.starredChanges().byAccount(me)) {
        existing.add(sc.getChangeId());
      }
    }
    return existing;
  }

  private static Set<Change.Id> draftedBy(final ReviewDb db, final Account.Id me)
      throws OrmException {
    final Set<Change.Id> existing = new HashSet<Change.Id>();
    if (me != null) {
      for (final PatchLineComment sc : db.patchComments().draftByAuthor(me)) {
        final Change.Id c =
            sc.getKey().getParentKey().getParentKey().getParentKey();
        existing.add(c);
      }
    }
    return existing;
  }

  private abstract class QueryNext implements Action<SingleListChangeInfo> {
    protected final String pos;
    protected final int limit;
    protected final int slim;

    QueryNext(final int pageSize, final String pos) {
      this.pos = pos;
      this.limit = safePageSize(pageSize);
      this.slim = limit + 1;
    }

    public SingleListChangeInfo run(final ReviewDb db) throws OrmException {
      final Account.Id me = Common.getAccountId();
      final AccountInfoCacheFactory ac = new AccountInfoCacheFactory(db);
      final SingleListChangeInfo d = new SingleListChangeInfo();
      final Set<Change.Id> starred = starredBy(db, me);

      boolean results = true;
      String sortKey = pos;
      final ArrayList<ChangeInfo> list = new ArrayList<ChangeInfo>();
      while (results && list.size() < slim) {
        results = false;
        final ResultSet<Change> rs = query(db, slim, sortKey);
        for (final Change c : rs) {
          results = true;
          if (canRead(c)) {
            final ChangeInfo ci = new ChangeInfo(c, ac);
            ci.setStarred(starred.contains(ci.getId()));
            list.add(ci);
            if (list.size() == slim) {
              rs.close();
              break;
            }
          }
          sortKey = c.getSortKey();
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
        String sortKey) throws OrmException;
  }

  private abstract class QueryPrev extends QueryNext {
    QueryPrev(int pageSize, String pos) {
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
