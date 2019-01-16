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

package com.google.gerrit.sshd;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ChangeArgumentParser {
  private final ChangesCollection changesCollection;
  private final ChangeFinder changeFinder;
  private final ChangeNotes.Factory changeNotesFactory;
  private final PermissionBackend permissionBackend;

  @Inject
  ChangeArgumentParser(
      ChangesCollection changesCollection,
      ChangeFinder changeFinder,
      ChangeNotes.Factory changeNotesFactory,
      PermissionBackend permissionBackend) {
    this.changesCollection = changesCollection;
    this.changeFinder = changeFinder;
    this.changeNotesFactory = changeNotesFactory;
    this.permissionBackend = permissionBackend;
  }

  public void addChange(String id, Map<Change.Id, ChangeResource> changes)
      throws UnloggedFailure, StorageException, PermissionBackendException, IOException {
    addChange(id, changes, null);
  }

  public void addChange(
      String id, Map<Change.Id, ChangeResource> changes, ProjectState projectState)
      throws UnloggedFailure, StorageException, PermissionBackendException, IOException {
    addChange(id, changes, projectState, true);
  }

  public void addChange(
      String id,
      Map<Change.Id, ChangeResource> changes,
      ProjectState projectState,
      boolean useIndex)
      throws UnloggedFailure, StorageException, PermissionBackendException, IOException {
    List<ChangeNotes> matched = useIndex ? changeFinder.find(id) : changeFromNotesFactory(id);
    List<ChangeNotes> toAdd = new ArrayList<>(changes.size());
    boolean canMaintainServer;
    try {
      permissionBackend.currentUser().check(GlobalPermission.MAINTAIN_SERVER);
      canMaintainServer = true;
    } catch (AuthException | PermissionBackendException e) {
      canMaintainServer = false;
    }
    for (ChangeNotes notes : matched) {
      if (!changes.containsKey(notes.getChangeId())
          && inProject(projectState, notes.getProjectName())) {
        if (canMaintainServer) {
          toAdd.add(notes);
          continue;
        }

        if (!projectState.statePermitsRead()) {
          continue;
        }

        try {
          permissionBackend.currentUser().change(notes).check(ChangePermission.READ);
          toAdd.add(notes);
        } catch (AuthException e) {
          // Do nothing.
        }
      }
    }

    if (toAdd.isEmpty()) {
      throw new UnloggedFailure(1, "\"" + id + "\" no such change");
    } else if (toAdd.size() > 1) {
      throw new UnloggedFailure(1, "\"" + id + "\" matches multiple changes");
    }
    Change.Id cId = toAdd.get(0).getChangeId();
    ChangeResource changeResource;
    try {
      changeResource = changesCollection.parse(cId);
    } catch (RestApiException e) {
      throw new UnloggedFailure(1, "\"" + id + "\" no such change");
    }
    changes.put(cId, changeResource);
  }

  private List<ChangeNotes> changeFromNotesFactory(String id)
      throws StorageException, UnloggedFailure {
    return changeNotesFactory.create(parseId(id));
  }

  private List<Change.Id> parseId(String id) throws UnloggedFailure {
    try {
      return Arrays.asList(new Change.Id(Integer.parseInt(id)));
    } catch (NumberFormatException e) {
      throw new UnloggedFailure(2, "Invalid change ID " + id, e);
    }
  }

  private boolean inProject(ProjectState projectState, Project.NameKey project) {
    if (projectState != null) {
      return projectState.getNameKey().equals(project);
    }

    // No --project option, so they want every project.
    return true;
  }
}
