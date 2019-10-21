// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.IndexedChangeQuery;

/** Predicate to match changes that contains specified text in commit messages body. */
public class MessagePredicate extends ChangeIndexPredicate {
  protected final ChangeIndex index;

  public MessagePredicate(ChangeIndex index, String value) {
    super(ChangeField.COMMIT_MESSAGE, value);
    this.index = index;
  }

  @Override
  public boolean match(ChangeData object) {
    try {
      Predicate<ChangeData> p =
          Predicate.and(
              index.getSchema().useLegacyNumericFields()
                  ? new LegacyChangeIdPredicate(object.getId())
                  : new LegacyChangeIdStrPredicate(object.getId()),
              this);
      for (ChangeData cData : index.getSource(p, IndexedChangeQuery.oneResult()).read()) {
        if (cData.getId().equals(object.getId())) {
          return true;
        }
      }
    } catch (QueryParseException e) {
      throw new StorageException(e);
    }

    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
