// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class CherryPickUtil {

  private final ChangeNotes.Factory notesFactory;
  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final PatchSetUtil psUtil;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  CherryPickUtil(
      ChangeNotes.Factory notesFactory,
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      PatchSetUtil psUtil,
      Provider<InternalChangeQuery> queryProvider) {
    this.notesFactory = notesFactory;
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.psUtil = psUtil;
    this.queryProvider = queryProvider;
  }

  public CherryPickDestination parseDestination(ProjectControl projectControl, String destination)
      throws OrmException, IOException, ResourceConflictException, AuthException,
          BadRequestException, ResourceNotFoundException {
    Project.NameKey project = projectControl.getProject().getNameKey();

    // Try parsing the destination as a branch.
    try (Repository repo = repoManager.openRepository(project)) {
      Ref ref = repo.getRefDatabase().exactRef(RefNames.fullName(destination));
      if (ref != null) {
        return CherryPickDestination.create(ref.getObjectId(), ref.getName(), false);
      }
    }

    // Try parsing the destination as a change number.
    Integer changeId = Ints.tryParse(destination);
    if (changeId != null) {
      List<ChangeData> destChanges =
          queryProvider.get().setLimit(2).byLegacyChangeId(new Id(changeId));
      if (destChanges.size() > 1) {
        // As change numeric id is unique for changes, this should never happen.
        throw new ResourceConflictException(
            String.format("multiple changes with id %s are found", changeId));
      } else if (destChanges.size() == 0) {
        throw new ResourceNotFoundException(String.format("change %s not found", changeId));
      }

      ReviewDb reviewDb = dbProvider.get();
      ChangeNotes notes = notesFactory.createChecked(reviewDb, project, new Change.Id(changeId));
      ChangeControl changeControl = projectControl.controlFor(notes);
      if (!changeControl.isVisible(reviewDb)) {
        throw new AuthException(String.format("change %s is not accessible", changeId));
      }

      Change change = changeControl.getChange();
      if (change.getStatus() != Status.NEW) {
        throw new ResourceConflictException(String.format("change %s is not open", changeId));
      }

      PatchSet currPatchSet = psUtil.current(reviewDb, notes);
      if (!changeControl.isPatchVisible(currPatchSet, reviewDb)) {
        throw new AuthException(
            String.format("current revision of the change %s is not accessible", changeId));
      }

      return CherryPickDestination.create(
          ObjectId.fromString(currPatchSet.getRevision().get()), change.getDest().get(), true);
    }

    throw new ResourceConflictException("cannot resolve cherry-pick destination: " + destination);
  }
}
