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

package com.google.gerrit.server;

import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Singleton
public class ChangeFinder {
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  ChangeFinder(Provider<InternalChangeQuery> queryProvider) {
    this.queryProvider = queryProvider;
  }

  /**
   * Find changes matching the given identifier.
   *
   * @param id change identifier, either a numeric ID, a Change-Id, or project~branch~id triplet.
   * @param user user to wrap in controls.
   * @return possibly-empty list of controls for all matching changes, corresponding to the given
   *     user; may or may not be visible.
   * @throws OrmException if an error occurred querying the database.
   */
  public List<ChangeControl> find(String id, CurrentUser user) throws OrmException {
    // Use the index to search for changes, but don't return any stored fields,
    // to force rereading in case the index is stale.
    InternalChangeQuery query = queryProvider.get().noFields();

    // Try legacy id
    if (!id.isEmpty() && id.charAt(0) != '0') {
      Integer n = Ints.tryParse(id);
      if (n != null) {
        return asChangeControls(query.byLegacyChangeId(new Change.Id(n)), user);
      }
    }

    // Try isolated changeId
    if (!id.contains("~")) {
      return asChangeControls(query.byKeyPrefix(id), user);
    }

    // Try change triplet
    Optional<ChangeTriplet> triplet = ChangeTriplet.parse(id);
    if (triplet.isPresent()) {
      return asChangeControls(query.byBranchKey(triplet.get().branch(), triplet.get().id()), user);
    }

    return Collections.emptyList();
  }

  public ChangeControl findOne(Change.Id id, CurrentUser user)
      throws OrmException, NoSuchChangeException {
    List<ChangeControl> ctls = find(id, user);
    if (ctls.size() != 1) {
      throw new NoSuchChangeException(id);
    }
    return ctls.get(0);
  }

  public List<ChangeControl> find(Change.Id id, CurrentUser user) throws OrmException {
    // Use the index to search for changes, but don't return any stored fields,
    // to force rereading in case the index is stale.
    InternalChangeQuery query = queryProvider.get().noFields();
    return asChangeControls(query.byLegacyChangeId(id), user);
  }

  private List<ChangeControl> asChangeControls(List<ChangeData> cds, CurrentUser user)
      throws OrmException {
    List<ChangeControl> ctls = new ArrayList<>(cds.size());
    for (ChangeData cd : cds) {
      ctls.add(cd.changeControl(user));
    }
    return ctls;
  }
}
