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

import static com.google.gerrit.server.index.change.ChangeField.COMMIT;
import static com.google.gerrit.server.index.change.ChangeField.EXACT_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.index.FieldDef;
import com.google.gwtorm.server.OrmException;

class CommitPredicate extends ChangeIndexPredicate {
  static FieldDef<ChangeData, ?> commitField(String id) {
    if (id.length() == OBJECT_ID_STRING_LENGTH) {
      return EXACT_COMMIT;
    }
    return COMMIT;
  }

  CommitPredicate(String id) {
    super(commitField(id), id);
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
    return (exact && id.equals(rev)) || (!exact && rev != null && rev.startsWith(id));
  }

  @Override
  public int getCost() {
    return 1;
  }
}
