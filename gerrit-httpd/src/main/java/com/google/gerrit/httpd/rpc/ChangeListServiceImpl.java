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
import com.google.gerrit.common.data.ToggleStarRequest;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
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

  private final Provider<CurrentUser> currentUser;
  private final ChangeControl.Factory changeControlFactory;
  private final AccountInfoCacheFactory.Factory accountInfoCacheFactory;
  private AccountControl.Factory accountControlFactory;

  @Inject
  ChangeListServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final ChangeControl.Factory changeControlFactory,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final AccountControl.Factory accountControlFactory) {
    super(schema, currentUser);
    this.currentUser = currentUser;
    this.changeControlFactory = changeControlFactory;
    this.accountInfoCacheFactory = accountInfoCacheFactory;
    this.accountControlFactory = accountControlFactory;
  }

  private boolean canRead(final Change c, final ReviewDb db) throws OrmException {
    try {
      return changeControlFactory.controlFor(c).isVisible(db);
    } catch (NoSuchChangeException e) {
      return false;
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

        // User dashboards are visible to other users, if the current user
        // can see any of the changes in the dashboard.
        if (!target.equals(me)
            && d.getByOwner().isEmpty()
            && d.getClosed().isEmpty()
            && d.getForReview().isEmpty()) {
          throw new Failure(new NoSuchEntityException());
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

  private List<ChangeInfo> filter(final ResultSet<Change> rs,
      final Set<Change.Id> starred, final AccountInfoCacheFactory accts,
      final ReviewDb db) throws OrmException {
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
}
