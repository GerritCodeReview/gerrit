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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
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
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChangeArgumentParser {
  private final ChangesCollection changesCollection;
  private final ChangeFinder changeFinder;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> currentUserProvider;

  @Inject
  ChangeArgumentParser(
      ChangesCollection changesCollection,
      ChangeFinder changeFinder,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> currentUserProvider) {
    this.changesCollection = changesCollection;
    this.changeFinder = changeFinder;
    this.permissionBackend = permissionBackend;
    this.currentUserProvider = currentUserProvider;
  }

  public void addChange(String id, Map<Change.Id, ChangeResource> changes)
      throws UnloggedFailure, PermissionBackendException {
    addChange(id, changes, null);
  }

  public void addChange(
      String id, Map<Change.Id, ChangeResource> changes, @Nullable ProjectState projectState)
      throws UnloggedFailure, PermissionBackendException {
    addChange(id, changes, projectState, true);
  }

  public void addChange(
      String id,
      Map<Change.Id, ChangeResource> changes,
      @Nullable ProjectState projectState,
      @SuppressWarnings(
              "unused") /* Issue 325821304: the useIndex parameter was introduced back in Gerrit
                         * v2.13
                         * when ReviewDb was around and the changeFinder was purely relying on
                         * Lucene.
                         * Fast-forward to v3.7 and the situation is exactly the opposite:
                         * changeFinder uses Lucene or NoteDb depending on the format of the
                         * change id.
                         * TODO: The useIndex is effectively useless right now, but the method
                         * signature needs to be preserved in a stable (almost EOL) release
                         * like v3.7.
                         * The method signature can be amended the parameter removed once this
                         * change is merged to master. */
          boolean useIndex)
      throws UnloggedFailure, PermissionBackendException {
    List<ChangeNotes> matched = changeFinder.find(id);
    List<ChangeNotes> toAdd = new ArrayList<>(changes.size());
    boolean canMaintainServer;
    try {
      canMaintainServer = permissionBackend.currentUser().test(GlobalPermission.MAINTAIN_SERVER);
    } catch (PermissionBackendException e) {
      canMaintainServer = false;
    }
    for (ChangeNotes notes : matched) {
      if (!changes.containsKey(notes.getChangeId())
          && inProject(projectState, notes.getProjectName())) {
        if (canMaintainServer) {
          toAdd.add(notes);
          continue;
        }

        if (projectState != null && !projectState.statePermitsRead()) {
          continue;
        }

        if (permissionBackend.currentUser().change(notes).test(ChangePermission.READ)) {
          toAdd.add(notes);
        }
      }
    }

    if (toAdd.isEmpty()) {
      throw new UnloggedFailure(1, "\"" + id + "\" no such change");
    } else if (toAdd.size() > 1) {
      throw new UnloggedFailure(1, "\"" + id + "\" matches multiple changes");
    }
    ChangeNotes changeNotes = toAdd.get(0);
    ChangeResource changeResource;
    changeResource = changesCollection.parse(changeNotes, currentUserProvider.get());
    changes.put(changeNotes.getChangeId(), changeResource);
  }

  private boolean inProject(ProjectState projectState, Project.NameKey project) {
    if (projectState != null) {
      return projectState.getNameKey().equals(project);
    }

    // No --project option, so they want every project.
    return true;
  }
}
