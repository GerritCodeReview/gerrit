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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Singleton
public class Revisions implements ChildCollection<ChangeResource, RevisionResource> {
  private final DynamicMap<RestView<RevisionResource>> views;
  private final Provider<ReviewDb> dbProvider;
  private final RevisionEditReader editReader;

  @Inject
  Revisions(DynamicMap<RestView<RevisionResource>> views,
      Provider<ReviewDb> dbProvider,
      RevisionEditReader editReader) {
    this.views = views;
    this.dbProvider = dbProvider;
    this.editReader = editReader;
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
  public RevisionResource parse(ChangeResource change, IdString id)
      throws ResourceNotFoundException, OrmException {
    String idStr = id.get();
    boolean edit = false;
    if (idStr.endsWith(".edit")) {
      idStr = idStr.substring(0, idStr.length() - 5);
      edit = true;
    }
    if (id.equals("current")) {
      PatchSet.Id p = change.getChange().currentPatchSetId();
      PatchSet ps = p != null ? dbProvider.get().patchSets().get(p) : null;
      if (ps != null && visible(change, ps)) {
        return new RevisionResource(change, ps).doNotCache();
      }
      throw new ResourceNotFoundException(id);
    }
    List<PatchSet> match = Lists.newArrayListWithExpectedSize(2);
    for (PatchSet ps : find(change, idStr)) {
      Change.Id changeId = ps.getId().getParentKey();
      if (changeId.equals(change.getChange().getId()) && visible(change, ps)) {
        if (!edit && ps.getId().isEdit()) {
          edit = true;
        }
        match.add(ps);
      }
    }
    if (match.size() != 1) {
      throw new ResourceNotFoundException(id);
    }
    return new RevisionResource(change, match.get(0), edit);
  }

  private boolean visible(ChangeResource change, PatchSet ps)
      throws OrmException {
    return change.getControl().isPatchVisible(ps, dbProvider.get());
  }

  private List<PatchSet> find(ChangeResource change, String id)
      throws OrmException {
    ReviewDb db = dbProvider.get();

    if (id.length() < 6 && id.matches("^[1-9][0-9]{0,4}$")) {
      // Legacy patch set number syntax.
      PatchSet ps = dbProvider.get().patchSets().get(new PatchSet.Id(
          change.getChange().getId(),
          Integer.parseInt(id)));
      if (ps != null) {
        return Collections.singletonList(ps);
      }
      return Collections.emptyList();
    } else if (id.length() < 4 || id.length() > RevId.LEN) {
      // Require a minimum of 4 digits.
      // Impossibly long identifier will never match.
      return Collections.emptyList();
    } else if (id.length() >= 8) {
      // Commit names are rather unique. Query for the commit and later
      // match to the change. This is most likely going to identify 1 or
      // at most 2 patch sets to consider, which is smaller than looking
      // for all patch sets in the change.
      RevId revid = new RevId(id);
      if (revid.isComplete()) {
        List<PatchSet> list = db.patchSets().byRevision(revid).toList();
        if (list.isEmpty()) {
          // this might be a revision edit (they are not stored in the database)
          try {
            Map<PatchSet.Id, PatchSet> map = editReader.read(change.getChange());
            for (Map.Entry<PatchSet.Id, PatchSet> e : map.entrySet()) {
              if (e.getValue().getRevision().equals(revid)) {
                return Collections.singletonList(e.getValue());
              }
            }
          } catch (Exception e) {
            throw new OrmException(e);
          }
        }
        return list;
      } else {
        return db.patchSets().byRevisionRange(revid, revid.max()).toList();
      }
    } else {
      // Chance of collision rises; look at all patch sets on the change.
      List<PatchSet> out = Lists.newArrayList();
      for (PatchSet ps : db.patchSets().byChange(change.getChange().getId())) {
        if (ps.getRevision() != null && ps.getRevision().get().startsWith(id)) {
          out.add(ps);
        }
      }
      return out;
    }
  }
}
