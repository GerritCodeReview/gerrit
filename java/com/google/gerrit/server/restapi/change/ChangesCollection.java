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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;

@Singleton
public class ChangesCollection implements RestCollection<TopLevelResource, ChangeResource> {
  private final Provider<CurrentUser> user;
  private final Provider<QueryChanges> queryFactory;
  private final DynamicMap<RestView<ChangeResource>> views;
  private final ChangeFinder changeFinder;
  private final ChangeResource.Factory changeResourceFactory;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;

  @Inject
  public ChangesCollection(
      Provider<CurrentUser> user,
      Provider<QueryChanges> queryFactory,
      DynamicMap<RestView<ChangeResource>> views,
      ChangeFinder changeFinder,
      ChangeResource.Factory changeResourceFactory,
      PermissionBackend permissionBackend,
      ProjectCache projectCache) {
    this.user = user;
    this.queryFactory = queryFactory;
    this.views = views;
    this.changeFinder = changeFinder;
    this.changeResourceFactory = changeResourceFactory;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
  }

  @Override
  public QueryChanges list() {
    return queryFactory.get();
  }

  @Override
  public DynamicMap<RestView<ChangeResource>> views() {
    return views;
  }

  @Override
  public ChangeResource parse(TopLevelResource root, IdString id)
      throws RestApiException, PermissionBackendException, IOException {
    List<ChangeNotes> notes = changeFinder.find(id.encoded(), true);
    if (notes.isEmpty()) {
      throw new ResourceNotFoundException(id);
    } else if (notes.size() != 1) {
      throw new ResourceNotFoundException("Multiple changes found for " + id);
    }

    ChangeNotes change = notes.get(0);
    if (!canRead(change)) {
      throw new ResourceNotFoundException(id);
    }
    checkProjectStatePermitsRead(change.getProjectName());
    return changeResourceFactory.create(change, user.get());
  }

  public ChangeResource parse(Change.Id id)
      throws ResourceConflictException, ResourceNotFoundException, PermissionBackendException,
          IOException {
    List<ChangeNotes> notes = changeFinder.find(id);
    if (notes.isEmpty()) {
      throw new ResourceNotFoundException(toIdString(id));
    } else if (notes.size() != 1) {
      throw new ResourceNotFoundException("Multiple changes found for " + id);
    }

    ChangeNotes change = notes.get(0);
    if (!canRead(change)) {
      throw new ResourceNotFoundException(toIdString(id));
    }
    checkProjectStatePermitsRead(change.getProjectName());
    return changeResourceFactory.create(change, user.get());
  }

  private static IdString toIdString(Change.Id id) {
    return IdString.fromDecoded(id.toString());
  }

  public ChangeResource parse(ChangeNotes notes, CurrentUser user) {
    return changeResourceFactory.create(notes, user);
  }

  private boolean canRead(ChangeNotes notes) throws PermissionBackendException, IOException {
    try {
      permissionBackend.currentUser().change(notes).check(ChangePermission.READ);
    } catch (AuthException e) {
      return false;
    }
    ProjectState projectState = projectCache.checkedGet(notes.getProjectName());
    if (projectState == null) {
      return false;
    }
    return projectState.statePermitsRead();
  }

  private void checkProjectStatePermitsRead(Project.NameKey project)
      throws IOException, ResourceNotFoundException, ResourceConflictException {
    ProjectState projectState = projectCache.checkedGet(project);
    if (projectState == null) {
      throw new ResourceNotFoundException("project not found: " + project.get());
    }
    projectState.checkStatePermitsRead();
  }
}
