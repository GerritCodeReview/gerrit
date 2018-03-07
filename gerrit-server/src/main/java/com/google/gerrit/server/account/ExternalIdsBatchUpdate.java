// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.ExternalId.toAccountExternalIds;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import java.util.HashSet;
import java.util.Set;

/** This class allows to do batch updates to external IDs. */
public class ExternalIdsBatchUpdate {
  private final Set<ExternalId> toAdd = new HashSet<>();
  private final Set<ExternalId> toDelete = new HashSet<>();

  /** Adds an external ID replacement to the batch. */
  public void replace(ExternalId extIdToDelete, ExternalId extIdToAdd) {
    ExternalIdsUpdate.checkSameAccount(ImmutableSet.of(extIdToDelete, extIdToAdd));
    toAdd.add(extIdToAdd);
    toDelete.add(extIdToDelete);
  }

  /**
   * Commits this batch.
   *
   * <p>This means external ID replacements which were prepared by invoking {@link
   * #replace(ExternalId, ExternalId)} are now executed. Deletion of external IDs is done before
   * adding the new external IDs. This means if an external ID is specified for deletion and an
   * external ID with the same key is specified to be added, the old external ID with that key is
   * deleted first and then the new external ID is added (so the external ID for that key is
   * replaced).
   */
  public void commit(ReviewDb db) throws OrmException {
    if (toDelete.isEmpty() && toAdd.isEmpty()) {
      return;
    }

    db.accountExternalIds().delete(toAccountExternalIds(toDelete));
    db.accountExternalIds().insert(toAccountExternalIds(toAdd));
    toAdd.clear();
    toDelete.clear();
  }
}
