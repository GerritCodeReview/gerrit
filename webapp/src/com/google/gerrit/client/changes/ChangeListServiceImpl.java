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
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.CookieAccess;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.SchemaFactory;

import java.util.ArrayList;
import java.util.List;

public class ChangeListServiceImpl implements ChangeListService {
  private final SchemaFactory<ReviewDb> schema;

  public ChangeListServiceImpl(final SchemaFactory<ReviewDb> rdf) {
    schema = rdf;
  }

  public void forAccount(Account.Id id,
      AsyncCallback<AccountDashboardInfo> callback) {
    if (id == null) {
      id = idFromCookie();
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

        final ChangeAccess changes = db.changes();
        final AccountDashboardInfo d;

        d = new AccountDashboardInfo(new AccountInfo(user));
        d.setByOwner(toInfoList(changes.byOwnerOpen(user.getId()), accts));
        d.setClosed(toInfoList(changes.byOwnerMerged(user.getId()), accts));
        callback.onSuccess(d);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }

  private List<ChangeInfo> toInfoList(final ResultSet<Change> rs,
      final AccountCache accts) throws OrmException {
    final ArrayList<ChangeInfo> r = new ArrayList<ChangeInfo>();
    for (final Change c : rs) {
      r.add(new ChangeInfo(c, accts));
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
