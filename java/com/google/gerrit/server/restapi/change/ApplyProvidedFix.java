// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.ApplyProvidedFixInput;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.CommitModification;
import com.google.gerrit.server.fixes.FixReplacementInterpreter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Repository;

/** Applies a fix that is provided as part of the request body. */
@Singleton
public class ApplyProvidedFix implements RestModifyView<RevisionResource, ApplyProvidedFixInput> {
  private final GitRepositoryManager gitRepositoryManager;
  private final FixReplacementInterpreter fixReplacementInterpreter;
  private final ChangeEditModifier changeEditModifier;
  private final ChangeEditJson changeEditJson;
  private final ProjectCache projectCache;

  @Inject
  public ApplyProvidedFix(
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
  public Response<EditInfo> apply(
      RevisionResource revisionResource, ApplyProvidedFixInput applyProvidedFixInput)
      throws AuthException,
          BadRequestException,
          ResourceConflictException,
          IOException,
          ResourceNotFoundException,
          PermissionBackendException {
    if (applyProvidedFixInput == null) {
      throw new BadRequestException("applyProvidedFixInput is required");
    }
    if (applyProvidedFixInput.fixReplacementInfos == null) {
      throw new BadRequestException("applyProvidedFixInput.fixReplacementInfos is required");
    }
    Project.NameKey project = revisionResource.getProject();
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    PatchSet patchSet = revisionResource.getPatchSet();

    ChangeNotes changeNotes = revisionResource.getNotes();

    List<FixReplacement> fixReplacements =
        applyProvidedFixInput.fixReplacementInfos.stream()
            .map(fix -> new FixReplacement(fix.path, new Range(fix.range), fix.replacement))
            .collect(Collectors.toList());

    try (Repository repository = gitRepositoryManager.openRepository(project)) {
      CommitModification commitModification =
          fixReplacementInterpreter.toCommitModification(
              repository, projectState, patchSet.commitId(), fixReplacements);
      ChangeEdit changeEdit =
          changeEditModifier.combineWithModifiedPatchSetTree(
              repository, changeNotes, patchSet, commitModification);

      return Response.ok(changeEditJson.toEditInfo(changeEdit, false));
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
