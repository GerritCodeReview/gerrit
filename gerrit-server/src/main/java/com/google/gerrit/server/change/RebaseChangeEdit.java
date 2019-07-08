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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsPost;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class RebaseChangeEdit
    implements ChildCollection<ChangeResource, ChangeEditResource>, AcceptsPost<ChangeResource> {

  private final Rebase rebase;

  @Inject
  RebaseChangeEdit(Rebase rebase) {
    this.rebase = rebase;
  }

  @Override
  public DynamicMap<RestView<ChangeEditResource>> views() {
    throw new NotImplementedException();
  }

  @Override
  public RestView<ChangeResource> list() {
    throw new NotImplementedException();
  }

  @Override
  public ChangeEditResource parse(ChangeResource parent, IdString id) {
    throw new NotImplementedException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Rebase post(ChangeResource parent) throws RestApiException {
    return rebase;
  }

  @Singleton
  public static class Rebase implements RestModifyView<ChangeResource, Rebase.Input> {
    public static class Input {}

    private final GitRepositoryManager repositoryManager;
    private final ChangeEditModifier editModifier;

    @Inject
    Rebase(GitRepositoryManager repositoryManager, ChangeEditModifier editModifier) {
      this.repositoryManager = repositoryManager;
      this.editModifier = editModifier;
    }

    @Override
    public Response<?> apply(ChangeResource rsrc, Rebase.Input in)
        throws AuthException, ResourceConflictException, IOException, OrmException {
      Project.NameKey project = rsrc.getProject();
      try (Repository repository = repositoryManager.openRepository(project)) {
        editModifier.rebaseEdit(repository, rsrc.getControl());
      } catch (InvalidChangeOperationException e) {
        throw new ResourceConflictException(e.getMessage());
      }
      return Response.none();
    }
  }
}
