// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.query.ObjectIdPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;

class CommitPredicate extends ObjectIdPredicate<ChangeData> {
  private final Provider<ReviewDb> dbProvider;

  CommitPredicate(Provider<ReviewDb> dbProvider, AbbreviatedObjectId id) {
    super(ChangeQueryBuilder.FIELD_COMMIT, id);
    this.dbProvider = dbProvider;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    for (PatchSet p : object.patches(dbProvider)) {
      if (p.getRevision() != null && p.getRevision().get() != null) {
        final ObjectId id = ObjectId.fromString(p.getRevision().get());
        if (abbreviated().prefixCompare(id) == 0) {
          return true;
        }
      }
    }
    return false;
  }
}
