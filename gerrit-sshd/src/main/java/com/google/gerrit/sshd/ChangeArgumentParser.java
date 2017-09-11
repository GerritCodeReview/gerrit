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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeFinder;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ChangeArgumentParser {
  private final CurrentUser currentUser;
  private final ChangesCollection changesCollection;
  private final ChangeFinder changeFinder;
  private final ReviewDb db;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final PermissionBackend permissionBackend;

  @Inject
  ChangeArgumentParser(
      CurrentUser currentUser,
      ChangesCollection changesCollection,
      ChangeFinder changeFinder,
      ReviewDb db,
      ChangeNotes.Factory changeNotesFactory,
      ChangeControl.GenericFactory changeControlFactory,
      PermissionBackend permissionBackend) {
    this.currentUser = currentUser;
    this.changesCollection = changesCollection;
    this.changeFinder = changeFinder;
    this.db = db;
    this.changeNotesFactory = changeNotesFactory;
    this.changeControlFactory = changeControlFactory;
    this.permissionBackend = permissionBackend;
  }

  public void addChange(String id, Map<Change.Id, ChangeResource> changes)
      throws UnloggedFailure, OrmException, PermissionBackendException {
    addChange(id, changes, null);
  }

  public void addChange(
      String id, Map<Change.Id, ChangeResource> changes, ProjectControl projectControl)
      throws UnloggedFailure, OrmException, PermissionBackendException {
    addChange(id, changes, projectControl, true);
  }

  public void addChange(
      String id,
      Map<Change.Id, ChangeResource> changes,
      ProjectControl projectControl,
      boolean useIndex)
      throws UnloggedFailure, OrmException, PermissionBackendException {
    List<ChangeNotes> matched = useIndex ? changeFinder.find(id) : changeFromNotesFactory(id);
    List<ChangeNotes> toAdd = new ArrayList<>(changes.size());
    boolean canMaintainServer;
    try {
      permissionBackend.user(currentUser).check(GlobalPermission.MAINTAIN_SERVER);
      canMaintainServer = true;
    } catch (AuthException | PermissionBackendException e) {
      canMaintainServer = false;
    }
    for (ChangeNotes notes : matched) {
      if (!changes.containsKey(notes.getChangeId())
          && inProject(projectControl, notes.getProjectName())
          && (canMaintainServer
              || permissionBackend
                  .user(currentUser)
                  .change(notes)
                  .database(db)
                  .test(ChangePermission.READ))) {
        toAdd.add(notes);
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
    } catch (ResourceNotFoundException e) {
      throw new UnloggedFailure(1, "\"" + id + "\" no such change");
    }
    changes.put(cId, changeResource);
  }

  private List<ChangeNotes> changeFromNotesFactory(String id) throws OrmException, UnloggedFailure {
    return changeNotesFactory.create(db, parseId(id)).stream().collect(toList());
  }

  private List<Change.Id> parseId(String id) throws UnloggedFailure {
    try {
      return Arrays.asList(new Change.Id(Integer.parseInt(id)));
    } catch (NumberFormatException e) {
      throw new UnloggedFailure(2, "Invalid change ID " + id, e);
    }
  }

  private boolean inProject(ProjectControl projectControl, Project.NameKey project) {
    if (projectControl != null) {
      return projectControl.getProject().getNameKey().equals(project);
    }

    // No --project option, so they want every project.
    return true;
  }
}
