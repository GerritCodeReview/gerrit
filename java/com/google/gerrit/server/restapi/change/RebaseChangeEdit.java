// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.RebaseChangeEditInput;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class RebaseChangeEdit implements RestModifyView<ChangeResource, RebaseChangeEditInput> {
  private final GitRepositoryManager repositoryManager;
  private final ChangeEditModifier editModifier;
  private final ChangeEditUtil editUtil;
  private final ChangeEditJson editJson;

  @Inject
  RebaseChangeEdit(
      GitRepositoryManager repositoryManager,
      ChangeEditModifier editModifier,
      ChangeEditUtil editUtil,
      ChangeEditJson editJson) {
    this.repositoryManager = repositoryManager;
    this.editModifier = editModifier;
    this.editUtil = editUtil;
    this.editJson = editJson;
  }

  @Override
  public Response<EditInfo> apply(ChangeResource rsrc, RebaseChangeEditInput input)
      throws AuthException, ResourceConflictException, IOException, PermissionBackendException {
    if (input == null) {
      input = new RebaseChangeEditInput();
    }

    Project.NameKey project = rsrc.getProject();
    try (Repository repository = repositoryManager.openRepository(project)) {
      CodeReviewCommit rebasedChangeEditCommit =
          editModifier.rebaseEdit(repository, rsrc.getNotes(), input);

      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getNotes(), rsrc.getUser());
      checkState(edit.isPresent(), "change edit missing after rebase");
      EditInfo editInfo = editJson.toEditInfo(edit.get(), /* downloadCommands= */ false);
      if (!rebasedChangeEditCommit.getFilesWithGitConflicts().isEmpty()) {
        editInfo.containsGitConflicts = true;
      }
      return Response.ok(editInfo);
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }
}
