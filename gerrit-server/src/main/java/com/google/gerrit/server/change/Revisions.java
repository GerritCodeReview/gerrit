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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Singleton
public class Revisions implements ChildCollection<ChangeResource, RevisionResource> {
  private final DynamicMap<RestView<RevisionResource>> views;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeEditUtil editUtil;

  @Inject
  Revisions(DynamicMap<RestView<RevisionResource>> views,
      Provider<ReviewDb> dbProvider,
      ChangeEditUtil editUtil) {
    this.views = views;
    this.dbProvider = dbProvider;
    this.editUtil = editUtil;
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
    if (id.equals("current")) {
      PatchSet.Id p = change.getChange().currentPatchSetId();
      PatchSet ps = p != null ? dbProvider.get().patchSets().get(p) : null;
      if (ps != null && visible(change, ps)) {
        return new RevisionResource(change, ps).doNotCache();
      }
      throw new ResourceNotFoundException(id);
    }
    List<RevisionResource> match = Lists.newArrayListWithExpectedSize(2);
    for (RevisionResource rsrc : find(change, id.get())) {
      Change.Id changeId = rsrc.getChange().getId();
      if (changeId.equals(change.getChange().getId())
          && visible(change, rsrc.getPatchSet())) {
        match.add(rsrc);
      }
    }
    switch (match.size()) {
      case 0:
        throw new ResourceNotFoundException(id);
      case 1:
        return match.get(0);
      default:
        throw new ResourceNotFoundException(
            "Multiple patch sets for \"" + id.get() + "\": "
            + Joiner.on("; ").join(match));
    }
  }

  private boolean visible(ChangeResource change, PatchSet ps)
      throws OrmException {
    return change.getControl().isPatchVisible(ps, dbProvider.get());
  }

  private List<RevisionResource> find(ChangeResource change, String id)
      throws OrmException {
    ReviewDb db = dbProvider.get();

    if (id.equals("0")) {
      return loadEdit(change, null);
    } else if (id.length() < 6 && id.matches("^[1-9][0-9]{0,4}$")) {
      // Legacy patch set number syntax.
      PatchSet ps = dbProvider.get().patchSets().get(new PatchSet.Id(
          change.getChange().getId(),
          Integer.parseInt(id)));
      if (ps != null) {
        return toResources(change, ps);
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
        List<RevisionResource> list =
            toResources(change, db.patchSets().byRevision(revid));
        if (list.isEmpty()) {
          return loadEdit(change, revid);
        }
        return list;
      } else {
        return toResources(
            change, db.patchSets().byRevisionRange(revid, revid.max()));
      }
    } else {
      // Chance of collision rises; look at all patch sets on the change.
      List<RevisionResource> out = Lists.newArrayList();
      for (PatchSet ps : db.patchSets().byChange(change.getChange().getId())) {
        if (ps.getRevision() != null && ps.getRevision().get().startsWith(id)) {
          out.add(new RevisionResource(change, ps));
        }
      }
      return out;
    }
  }

  private List<RevisionResource> loadEdit(ChangeResource change, RevId revid)
      throws OrmException {
    try {
      Optional<ChangeEdit> edit = editUtil.byChange(change.getChange());
      if (edit.isPresent()) {
        PatchSet ps = new PatchSet(new PatchSet.Id(
            change.getChange().getId(), 0));
        ps.setRevision(edit.get().getRevision());
        if (revid == null || edit.get().getRevision().equals(revid)) {
          return Collections.singletonList(
              new RevisionResource(change, ps, edit));
        }
      }
    } catch (AuthException | IOException e) {
      throw new OrmException(e);
    }
    return Collections.emptyList();
  }

  private static List<RevisionResource> toResources(final ChangeResource change,
      Iterable<PatchSet> patchSets) {
    return FluentIterable.from(patchSets)
        .transform(new Function<PatchSet, RevisionResource>() {
          @Override
          public RevisionResource apply(PatchSet in) {
            return new RevisionResource(change, in);
          }
        }).toList();
  }

  private static List<RevisionResource> toResources(ChangeResource change,
      PatchSet ps) {
    return Collections.singletonList(new RevisionResource(change, ps));
  }
}
