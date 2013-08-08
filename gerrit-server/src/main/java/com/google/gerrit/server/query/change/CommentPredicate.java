// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

class CommentPredicate extends IndexPredicate<ChangeData> {
  private final Provider<ReviewDb> db;
  private final ChangeIndex index;

  CommentPredicate(Provider<ReviewDb> db, ChangeIndex index, String value) {
    super(ChangeField.COMMENT, value);
    this.db = db;
    this.index = index;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean match(ChangeData object) throws OrmException {
    try {
      for (ChangeData cData : index.getSource(
          Predicate.and(new LegacyChangeIdPredicate(db, object.getId()), this), 1)
          .read()) {
        if (cData.getId().equals(object.getId())) {
          return true;
        }
      }
    } catch (QueryParseException e) {
      throw new OrmException(e);
    }

    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
