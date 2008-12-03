// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.data.AccountCache;
import com.google.gerrit.client.data.AccountDashboardInfo;
import com.google.gerrit.client.data.AccountInfo;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeAccess;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.StarredChange;
import com.google.gerrit.client.reviewdb.Change.Id;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.RpcUtil;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangeListServiceImpl extends BaseServiceImplementation implements
    ChangeListService {
  public ChangeListServiceImpl(final SchemaFactory<ReviewDb> rdf) {
    super(rdf);
  }

  public void forAccount(final Account.Id id,
      final AsyncCallback<AccountDashboardInfo> callback) {
    final Account.Id me = RpcUtil.getAccountId();
    final Account.Id target = id != null ? id : me;
    if (target == null) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }

    run(callback, new Action<AccountDashboardInfo>() {
      public AccountDashboardInfo run(final ReviewDb db) throws OrmException,
          Failure {
        final AccountCache ac = new AccountCache(db);
        final Account user = ac.get(target);
        if (user == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final Set<Change.Id> stars = starredBy(db, me);
        final ChangeAccess changes = db.changes();
        final AccountDashboardInfo d;

        d = new AccountDashboardInfo(new AccountInfo(user));
        d.setByOwner(list(changes.byOwnerOpen(target), stars, ac));
        d.setClosed(list(changes.byOwnerMerged(target), stars, ac));
        return d;
      }
    });
  }

  public void myStarredChanges(final AsyncCallback<List<ChangeInfo>> callback) {
    run(callback, new Action<List<ChangeInfo>>() {
      public List<ChangeInfo> run(final ReviewDb db) throws OrmException {
        final Account.Id me = RpcUtil.getAccountId();
        final AccountCache ac = new AccountCache(db);
        final Set<Change.Id> starred = starredBy(db, me);
        return list(db.changes().get(starred), starred, ac);
      }
    });
  }

  public void toggleStars(final ToggleStarRequest req,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException {
        final Account.Id me = RpcUtil.getAccountId();
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
        return starredBy(db, RpcUtil.getAccountId());
      }
    });
  }

  private static List<ChangeInfo> list(final ResultSet<Change> rs,
      final Set<Change.Id> starred, final AccountCache accts)
      throws OrmException {
    final ArrayList<ChangeInfo> r = new ArrayList<ChangeInfo>();
    for (final Change c : rs) {
      final ChangeInfo ci = new ChangeInfo(c, accts);
      ci.setStarred(starred.contains(ci.getId()));
      r.add(ci);
    }
    return r;
  }

  private static Set<Change.Id> starredBy(final ReviewDb db, final Account.Id me)
      throws OrmException {
    final Set<Change.Id> existing = new HashSet<Change.Id>();
    if (me != null) {
      for (final StarredChange sc : db.starredChanges().byAccount(me)) {
        existing.add(sc.getChangeId());
      }
    }
    return existing;
  }
}
