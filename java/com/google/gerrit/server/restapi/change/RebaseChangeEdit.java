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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class RebaseChangeEdit extends RetryingRestModifyView<ChangeResource, Input, Response<?>> {
  private final GitRepositoryManager repositoryManager;
  private final ChangeEditModifier editModifier;

  @Inject
  RebaseChangeEdit(
      RetryHelper retryHelper,
      GitRepositoryManager repositoryManager,
      ChangeEditModifier editModifier) {
    super(retryHelper);
    this.repositoryManager = repositoryManager;
    this.editModifier = editModifier;
  }

  @Override
  protected Response<?> applyImpl(BatchUpdate.Factory updateFactory, ChangeResource rsrc, Input in)
      throws AuthException, ResourceConflictException, IOException, StorageException,
          PermissionBackendException {
    Project.NameKey project = rsrc.getProject();
    try (Repository repository = repositoryManager.openRepository(project)) {
      editModifier.rebaseEdit(repository, rsrc.getNotes());
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
    return Response.none();
  }
}
