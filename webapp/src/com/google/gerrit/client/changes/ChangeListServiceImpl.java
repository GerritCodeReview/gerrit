package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
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
import com.google.gerrit.client.rpc.NotSignedInException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.CookieAccess;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangeListServiceImpl implements ChangeListService {
  private final SchemaFactory<ReviewDb> schema;

  public ChangeListServiceImpl(final SchemaFactory<ReviewDb> rdf) {
    schema = rdf;
  }

  public void forAccount(Account.Id id,
      AsyncCallback<AccountDashboardInfo> callback) {
    final Account.Id me = idFromCookie();
    if (id == null) {
      id = me;
    }
    if (id == null) {
      callback.onFailure(new IllegalArgumentException("No Account.Id"));
      return;
    }

    try {
      final ReviewDb db = schema.open();
      try {
        final AccountCache accts = new AccountCache(db);
        final Account user = accts.get(id);
        if (user == null) {
          callback.onFailure(new IllegalArgumentException("No such user"));
          return;
        }

        final Set<Change.Id> starred = new HashSet<Change.Id>();
        if (me != null) {
          for (final StarredChange sc : db.starredChanges().byAccount(me)) {
            starred.add(sc.getChangeId());
          }
        }

        final ChangeAccess changes = db.changes();
        final AccountDashboardInfo d;

        d = new AccountDashboardInfo(new AccountInfo(user));
        d.setByOwner(list(changes.byOwnerOpen(user.getId()), starred, accts));
        d.setClosed(list(changes.byOwnerMerged(user.getId()), starred, accts));
        callback.onSuccess(d);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }

  public void myStarredChanges(final AsyncCallback<List<ChangeInfo>> callback) {
    final Account.Id me = idFromCookie();
    if (me == null) {
      callback.onFailure(new NotSignedInException());
      return;
    }
    try {
      final ReviewDb db = schema.open();
      try {
        final AccountCache accts = new AccountCache(db);
        final Set<Change.Id> starred = new HashSet<Change.Id>();
        for (final StarredChange sc : db.starredChanges().byAccount(me)) {
          starred.add(sc.getChangeId());
        }
        callback.onSuccess(list(db.changes().get(starred), starred, accts));
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }

  public void toggleStars(final ToggleStarRequest req,
      final AsyncCallback<VoidResult> callback) {
    final Account.Id me = idFromCookie();
    if (me == null) {
      callback.onFailure(new NotSignedInException());
      return;
    }

    try {
      final ReviewDb db = schema.open();
      try {
        final Set<Change.Id> existing = new HashSet<Change.Id>();
        for (final StarredChange sc : db.starredChanges().byAccount(me)) {
          existing.add(sc.getChangeId());
        }

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
        callback.onSuccess(VoidResult.INSTANCE);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }

  public void myStarredChangeIds(final AsyncCallback<Set<Id>> callback) {
    final Account.Id me = idFromCookie();
    if (me == null) {
      callback.onFailure(new NotSignedInException());
      return;
    }

    try {
      final ReviewDb db = schema.open();
      try {
        final Set<Change.Id> existing = new HashSet<Change.Id>();
        for (final StarredChange sc : db.starredChanges().byAccount(me)) {
          existing.add(sc.getChangeId());
        }
        callback.onSuccess(existing);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }

  private List<ChangeInfo> list(final ResultSet<Change> rs,
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

  private static Account.Id idFromCookie() {
    final String myid = CookieAccess.getTokenText(Gerrit.ACCOUNT_COOKIE);
    if (myid != null && myid.length() > 0) {
      try {
        return new Account.Id(Integer.parseInt(myid));
      } catch (NumberFormatException e) {
      }
    }
    return null;
  }
}
