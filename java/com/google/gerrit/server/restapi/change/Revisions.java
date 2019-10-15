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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class Revisions implements ChildCollection<ChangeResource, RevisionResource> {
  private final DynamicMap<RestView<RevisionResource>> views;
  private final ChangeEditUtil editUtil;
  private final PatchSetUtil psUtil;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;

  @Inject
  Revisions(
      DynamicMap<RestView<RevisionResource>> views,
      ChangeEditUtil editUtil,
      PatchSetUtil psUtil,
      PermissionBackend permissionBackend,
      ProjectCache projectCache) {
    this.views = views;
    this.editUtil = editUtil;
    this.psUtil = psUtil;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
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
      throws ResourceNotFoundException, AuthException, IOException, PermissionBackendException {
    if (id.get().equals("current")) {
      PatchSet ps = psUtil.current(change.getNotes());
      if (ps != null && visible(change)) {
        return RevisionResource.createNonCacheable(change, ps);
      }
      throw new ResourceNotFoundException(id);
    }

    List<RevisionResource> match = Lists.newArrayListWithExpectedSize(2);
    for (RevisionResource rsrc : find(change, id.get())) {
      if (visible(change)) {
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
            "Multiple patch sets for \"" + id.get() + "\": " + Joiner.on("; ").join(match));
    }
  }

  private boolean visible(ChangeResource change) throws PermissionBackendException, IOException {
    try {
      permissionBackend
          .user(change.getUser())
          .change(change.getNotes())
          .check(ChangePermission.READ);
      return projectCache.checkedGet(change.getProject()).statePermitsRead();
    } catch (AuthException e) {
      return false;
    }
  }

  private List<RevisionResource> find(ChangeResource change, String id)
      throws IOException, AuthException {
    if (id.equals("0") || id.equals("edit")) {
      return loadEdit(change, null);
    } else if (id.length() < 6 && id.matches("^[1-9][0-9]{0,4}$")) {
      // Legacy patch set number syntax.
      return byLegacyPatchSetId(change, id);
    } else if (id.length() < 4 || id.length() > ObjectIds.STR_LEN) {
      // Require a minimum of 4 digits.
      // Impossibly long identifier will never match.
      return Collections.emptyList();
    } else {
      List<RevisionResource> out = new ArrayList<>();
      for (PatchSet ps : psUtil.byChange(change.getNotes())) {
        if (ObjectIds.matchesAbbreviation(ps.commitId(), id)) {
          out.add(new RevisionResource(change, ps));
        }
      }
      // Not an existing patch set on a change, but might be an edit.
      if (out.isEmpty() && ObjectId.isId(id)) {
        return loadEdit(change, ObjectId.fromString(id));
      }
      return out;
    }
  }

  private List<RevisionResource> byLegacyPatchSetId(ChangeResource change, String id) {
    PatchSet ps = psUtil.get(change.getNotes(), PatchSet.id(change.getId(), Integer.parseInt(id)));
    if (ps != null) {
      return Collections.singletonList(new RevisionResource(change, ps));
    }
    return Collections.emptyList();
  }

  private List<RevisionResource> loadEdit(ChangeResource change, @Nullable ObjectId commitId)
      throws AuthException, IOException {
    Optional<ChangeEdit> edit = editUtil.byChange(change.getNotes(), change.getUser());
    if (edit.isPresent()) {
      RevCommit editCommit = edit.get().getEditCommit();
      PatchSet ps =
          PatchSet.builder()
              .id(PatchSet.id(change.getId(), 0))
              .commitId(editCommit)
              .uploader(change.getUser().getAccountId())
              .createdOn(new Timestamp(editCommit.getCommitterIdent().getWhen().getTime()))
              .build();
      if (commitId == null || editCommit.equals(commitId)) {
        return Collections.singletonList(new RevisionResource(change, ps, edit));
      }
    }
    return Collections.emptyList();
  }
}
