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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.FixResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.fixes.FixReplacementInterpreter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class ApplyFix implements RestModifyView<FixResource, Void> {

  private final GitRepositoryManager gitRepositoryManager;
  private final FixReplacementInterpreter fixReplacementInterpreter;
  private final ChangeEditModifier changeEditModifier;
  private final ChangeEditJson changeEditJson;
  private final ProjectCache projectCache;

  @Inject
  public ApplyFix(
      GitRepositoryManager gitRepositoryManager,
      FixReplacementInterpreter fixReplacementInterpreter,
      ChangeEditModifier changeEditModifier,
      ChangeEditJson changeEditJson,
      ProjectCache projectCache) {
    this.gitRepositoryManager = gitRepositoryManager;
    this.fixReplacementInterpreter = fixReplacementInterpreter;
    this.changeEditModifier = changeEditModifier;
    this.changeEditJson = changeEditJson;
    this.projectCache = projectCache;
  }

  @Override
  public Response<EditInfo> apply(FixResource fixResource, Void nothing)
      throws AuthException, StorageException, ResourceConflictException, IOException,
          ResourceNotFoundException, PermissionBackendException {
    RevisionResource revisionResource = fixResource.getRevisionResource();
    Project.NameKey project = revisionResource.getProject();
    ProjectState projectState = projectCache.checkedGet(project);
    PatchSet patchSet = revisionResource.getPatchSet();
    ObjectId patchSetCommitId = ObjectId.fromString(patchSet.getRevision().get());

    try (Repository repository = gitRepositoryManager.openRepository(project)) {
      List<TreeModification> treeModifications =
          fixReplacementInterpreter.toTreeModifications(
              repository, projectState, patchSetCommitId, fixResource.getFixReplacements());
      ChangeEdit changeEdit =
          changeEditModifier.combineWithModifiedPatchSetTree(
              repository, revisionResource.getNotes(), patchSet, treeModifications);
      return Response.ok(changeEditJson.toEditInfo(changeEdit, false));
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
