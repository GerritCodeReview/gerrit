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

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class ChangesCollection implements RestCollection<TopLevelResource, ChangeResource> {
  private final Provider<CurrentUser> user;
  private final Provider<QueryChanges> queryFactory;
  private final DynamicMap<RestView<ChangeResource>> views;
  private final ChangeFinder changeFinder;
  private final ChangeResource.Factory changeResourceFactory;
  private final PermissionBackend permissionBackend;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeData.Factory changeDataFactory;

  @Inject
  public ChangesCollection(
      Provider<CurrentUser> user,
      Provider<QueryChanges> queryFactory,
      DynamicMap<RestView<ChangeResource>> views,
      ChangeFinder changeFinder,
      ChangeResource.Factory changeResourceFactory,
      PermissionBackend permissionBackend,
      ChangeNotes.Factory changeNotesFactory,
      ChangeData.Factory changeDataFactory) {
    this.changeNotesFactory = changeNotesFactory;
    this.user = user;
    this.queryFactory = queryFactory;
    this.views = views;
    this.changeFinder = changeFinder;
    this.changeResourceFactory = changeResourceFactory;
    this.permissionBackend = permissionBackend;
    this.changeDataFactory = changeDataFactory;
  }

  @Override
  public QueryChanges list() {
    return queryFactory.get();
  }

  @Override
  public DynamicMap<RestView<ChangeResource>> views() {
    return views;
  }

  /**
   * Parses {@link ChangeResource} from {@link com.google.gerrit.entities.Change.Id}
   *
   * <p>Reads the change from index, since project is unknown.
   */
  @Override
  public ChangeResource parse(TopLevelResource root, IdString id)
      throws RestApiException, PermissionBackendException, IOException {
    List<ChangeNotes> notes = changeFinder.find(id.encoded(), 2);
    if (notes.isEmpty()) {
      throw new ResourceNotFoundException(id);
    } else if (notes.size() != 1) {
      throw new ResourceNotFoundException("Multiple changes found for " + id);
    }

    ChangeNotes change = notes.get(0);
    checkProjectStatePermitsRead(change);
    if (!canRead(change)) {
      throw new ResourceNotFoundException(id);
    }
    return changeResourceFactory.create(change, user.get());
  }

  /**
   * Parses {@link ChangeResource} from {@link com.google.gerrit.entities.Change.Id} in {@code
   * project} at {@code metaRevId}
   *
   * <p>Read change from ChangeNotesCache, so the method can be used upon creation, when the change
   * might not be yet available in the index.
   */
  public ChangeResource parse(Project.NameKey project, Change.Id id, ObjectId metaRevId)
      throws ResourceConflictException, ResourceNotFoundException, PermissionBackendException {
    ChangeNotes change = changeNotesFactory.createChecked(project, id, metaRevId);
    checkProjectStatePermitsRead(change);
    if (!canRead(change)) {
      throw new ResourceNotFoundException(toIdString(id));
    }

    return changeResourceFactory.create(change, user.get());
  }

  /**
   * Parses {@link ChangeResource} from {@link com.google.gerrit.entities.Change.Id}
   *
   * <p>Reads the change from index, since project is unknown.
   */
  public ChangeResource parse(Change.Id id)
      throws ResourceConflictException, ResourceNotFoundException, PermissionBackendException {
    List<ChangeNotes> notes = changeFinder.find(id);
    if (notes.isEmpty()) {
      throw new ResourceNotFoundException(toIdString(id));
    } else if (notes.size() != 1) {
      throw new ResourceNotFoundException("Multiple changes found for " + id);
    }

    ChangeNotes change = notes.get(0);
    checkProjectStatePermitsRead(change);
    if (!canRead(change)) {
      throw new ResourceNotFoundException(toIdString(id));
    }
    return changeResourceFactory.create(change, user.get());
  }

  private static IdString toIdString(Change.Id id) {
    return IdString.fromDecoded(id.toString());
  }

  public ChangeResource parse(ChangeNotes notes, CurrentUser user) {
    return changeResourceFactory.create(notes, user);
  }

  private boolean canRead(ChangeNotes notes) throws PermissionBackendException {
    return permissionBackend.currentUser().change(notes).test(ChangePermission.READ);
  }

  private void checkProjectStatePermitsRead(ChangeNotes changeNotes)
      throws ResourceConflictException {
    changeDataFactory.create(changeNotes).checkProjectStatePermitsRead();
  }
}
