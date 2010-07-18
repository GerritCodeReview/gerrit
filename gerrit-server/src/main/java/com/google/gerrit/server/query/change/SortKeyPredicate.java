// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

abstract class SortKeyPredicate extends OperatorPredicate<ChangeData> {
  protected final Provider<ReviewDb> dbProvider;

  SortKeyPredicate(Provider<ReviewDb> dbProvider, String name, String value) {
    super(name, value);
    this.dbProvider = dbProvider;
  }

  @Override
  public int getCost() {
    return 1;
  }

  static class Before extends SortKeyPredicate {
    Before(Provider<ReviewDb> dbProvider, String value) {
      super(dbProvider, "sortkey_before", value);
    }

    @Override
    public boolean match(ChangeData cd) throws OrmException {
      Change change = cd.change(dbProvider);
      return change != null && change.getSortKey().compareTo(getValue()) < 0;
    }
  }

  static class After extends SortKeyPredicate {
    After(Provider<ReviewDb> dbProvider, String value) {
      super(dbProvider, "sortkey_after", value);
    }

    @Override
    public boolean match(ChangeData cd) throws OrmException {
      Change change = cd.change(dbProvider);
      return change != null && change.getSortKey().compareTo(getValue()) > 0;
    }
  }
}
