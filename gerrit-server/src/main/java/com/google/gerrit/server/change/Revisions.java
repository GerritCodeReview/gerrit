// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;

class Revisions implements ChildCollection<ChangeResource, RevisionResource> {
  private final DynamicMap<RestView<RevisionResource>> views;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  Revisions(DynamicMap<RestView<RevisionResource>> views,
      Provider<ReviewDb> dbProvider) {
    this.views = views;
    this.dbProvider = dbProvider;
  }

  @Override
  public DynamicMap<RestView<RevisionResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public RevisionResource parse(ChangeResource change, String id)
      throws ResourceNotFoundException, Exception {
    if (id.matches("^[1-9][0-9]{0,4}$")) {
      PatchSet ps = dbProvider.get().patchSets().get(new PatchSet.Id(
          change.getChange().getId(),
          Integer.parseInt(id)));
      if (ps != null
          && change.getControl().isPatchVisible(ps, dbProvider.get())) {
        return new RevisionResource(change, ps);
      }
      throw new ResourceNotFoundException(id);
    }

    for (PatchSet ps : find(id)) {
      Change.Id changeId = ps.getId().getParentKey();
      if (changeId.equals(change.getChange().getId())) {
        if (change.getControl().isPatchVisible(ps, dbProvider.get())) {
          return new RevisionResource(change, ps);
        }
        break;
      }
    }
    throw new ResourceNotFoundException(id);
  }

  private List<PatchSet> find(String id) throws OrmException {
    ReviewDb db = dbProvider.get();
    RevId revid = new RevId(id);
    if (revid.isComplete()) {
      return db.patchSets().byRevision(revid).toList();
    } else {
      return db.patchSets().byRevisionRange(revid, revid.max()).toList();
    }
  }
}
