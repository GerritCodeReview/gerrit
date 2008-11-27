package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

import java.util.HashMap;

public class AccountCache {
  private final ReviewDb db;
  private final HashMap<Account.Id, Account> cache;

  public AccountCache(final ReviewDb schema) {
    db = schema;
    cache = new HashMap<Account.Id, Account>();
  }

  public Account get(final Account.Id id) throws OrmException {
    Account a = cache.get(id);
    if (a == null) {
      a = db.accounts().byId(id);
      cache.put(id, a);
    }
    return a;
  }
}
