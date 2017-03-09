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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.fixes.FixReplacementInterpreter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class ApplyFix implements RestModifyView<FixResource, Void> {

  private final GitRepositoryManager gitRepositoryManager;
  private final FixReplacementInterpreter fixReplacementInterpreter;
  private final ChangeEditModifier changeEditModifier;

  @Inject
  public ApplyFix(
      GitRepositoryManager gitRepositoryManager,
      FixReplacementInterpreter fixReplacementInterpreter,
      ChangeEditModifier changeEditModifier) {
    this.gitRepositoryManager = gitRepositoryManager;
    this.fixReplacementInterpreter = fixReplacementInterpreter;
    this.changeEditModifier = changeEditModifier;
  }

  @Override
  public Response<?> apply(FixResource fixResource, Void nothing)
      throws AuthException, OrmException, ResourceConflictException, IOException,
          ResourceNotFoundException {
    RevisionResource revisionResource = fixResource.getRevisionResource();
    Project.NameKey project = revisionResource.getProject();
    ProjectState projectState = revisionResource.getControl().getProjectControl().getProjectState();
    PatchSet patchSet = revisionResource.getPatchSet();
    ObjectId patchSetCommitId = ObjectId.fromString(patchSet.getRevision().get());

    try (Repository repository = gitRepositoryManager.openRepository(project)) {
      TreeModification treeModification =
          fixReplacementInterpreter.toTreeModification(
              repository, projectState, patchSetCommitId, fixResource.getFixReplacements());
      changeEditModifier.combineWithModifiedPatchSetTree(
          repository, revisionResource.getControl(), patchSet, treeModification);
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
    return Response.none();
  }
}
