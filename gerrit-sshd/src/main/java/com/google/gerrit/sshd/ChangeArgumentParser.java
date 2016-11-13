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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeFinder;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChangeArgumentParser {
  private final CurrentUser currentUser;
  private final ChangesCollection changesCollection;
  private final ChangeFinder changeFinder;
  private final ReviewDb db;
  private final ChangeNotes.Factory changeNotesFactory;
  private final ChangeControl.GenericFactory changeControlFactory;

  @Inject
  ChangeArgumentParser(
      CurrentUser currentUser,
      ChangesCollection changesCollection,
      ChangeFinder changeFinder,
      ReviewDb db,
      ChangeNotes.Factory changeNotesFactory,
      ChangeControl.GenericFactory changeControlFactory) {
    this.currentUser = currentUser;
    this.changesCollection = changesCollection;
    this.changeFinder = changeFinder;
    this.db = db;
    this.changeNotesFactory = changeNotesFactory;
    this.changeControlFactory = changeControlFactory;
  }

  public void addChange(String id, Map<Change.Id, ChangeResource> changes)
      throws UnloggedFailure, OrmException {
    addChange(id, changes, null);
  }

  public void addChange(
      String id, Map<Change.Id, ChangeResource> changes, ProjectControl projectControl)
      throws UnloggedFailure, OrmException {
    addChange(id, changes, projectControl, true);
  }

  public void addChange(
      String id,
      Map<Change.Id, ChangeResource> changes,
      ProjectControl projectControl,
      boolean useIndex)
      throws UnloggedFailure, OrmException {
    List<ChangeControl> matched =
        useIndex ? changeFinder.find(id, currentUser) : changeFromNotesFactory(id, currentUser);
    List<ChangeControl> toAdd = new ArrayList<>(changes.size());
    for (ChangeControl ctl : matched) {
      if (!changes.containsKey(ctl.getId())
          && inProject(projectControl, ctl.getProject())
          && ctl.isVisible(db)) {
        toAdd.add(ctl);
      }
    }

    if (toAdd.isEmpty()) {
      throw new UnloggedFailure(1, "\"" + id + "\" no such change");
    } else if (toAdd.size() > 1) {
      throw new UnloggedFailure(1, "\"" + id + "\" matches multiple changes");
    }
    ChangeControl ctl = toAdd.get(0);
    changes.put(ctl.getId(), changesCollection.parse(ctl));
  }

  private List<ChangeControl> changeFromNotesFactory(String id, CurrentUser currentUser)
      throws OrmException {
    return changeNotesFactory
        .create(db, Arrays.asList(Change.Id.parse(id)))
        .stream()
        .map(changeNote -> controlForChange(changeNote, currentUser))
        .filter(changeControl -> changeControl.isPresent())
        .map(changeControl -> changeControl.get())
        .collect(toList());
  }

  private Optional<ChangeControl> controlForChange(ChangeNotes change, CurrentUser user) {
    try {
      return Optional.of(changeControlFactory.controlFor(change, user));
    } catch (NoSuchChangeException e) {
      return Optional.empty();
    }
  }

  private boolean inProject(ProjectControl projectControl, Project project) {
    if (projectControl != null) {
      return projectControl.getProject().getNameKey().equals(project.getNameKey());
    }

    // No --project option, so they want every project.
    return true;
  }
}
