// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

class RegexFilePredicate extends OperatorPredicate<ChangeData> {
  private final Provider<ReviewDb> db;
  private final PatchListCache cache;
  private final Pattern pattern;

  RegexFilePredicate(Provider<ReviewDb> db, PatchListCache plc, String re) {
    super(ChangeQueryBuilder.FIELD_FILE, re);
    this.db = db;
    this.cache = plc;
    try {
      this.pattern = Pattern.compile(re);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Collection<String> files = object.currentFilePaths(db, cache);
    if (files != null) {
      for (String path : files) {
        if (pattern.matcher(path).find()) {
          return true;
        }
      }
      return false;

    } else {
      // The ChangeData can't do expensive lookups right now. Bypass
      // them and include the result anyway. We might be able to do
      // a narrow later on to a smaller set.
      //
      return true;
    }
  }

  @Override
  public int getCost() {
    return 1;
  }
}
