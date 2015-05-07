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

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;

class CommitPredicate extends IndexPredicate<ChangeData> {
  private final AbbreviatedObjectId abbrevId;

  CommitPredicate(AbbreviatedObjectId id) {
    super(ChangeField.COMMIT, id.name());
    this.abbrevId = id;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    for (PatchSet p : object.patchSets()) {
      if (p.getRevision() != null && p.getRevision().get() != null) {
        final ObjectId id = ObjectId.fromString(p.getRevision().get());
        if (abbrevId.prefixCompare(id) == 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
