// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.server.index.ChangeField.EXACT_COMMIT;

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.Schema;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.Constants;

class CommitPredicate extends IndexPredicate<ChangeData> {
  static FieldDef<ChangeData, ?> commitField(Schema<ChangeData> schema, String id) {
    if (id.length() == Constants.OBJECT_ID_STRING_LENGTH
        && schema.hasField(EXACT_COMMIT)) {
      return EXACT_COMMIT;
    }
    return ChangeField.COMMIT;
  }

  CommitPredicate(Schema<ChangeData> schema, String id) {
    super(commitField(schema, id), id);
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    String id = getValue().toLowerCase();
    for (PatchSet p : object.patchSets()) {
      if (equals(p, id)) {
        return true;
      }
    }
    return false;
  }

  private boolean equals(PatchSet p, String id) {
    boolean exact = getField() == EXACT_COMMIT;
    String rev = p.getRevision() != null ? p.getRevision().get() : null;
    return (exact && id.equals(rev))
        || (!exact && rev != null && rev.startsWith(id));
  }

  @Override
  public int getCost() {
    return 1;
  }
}
