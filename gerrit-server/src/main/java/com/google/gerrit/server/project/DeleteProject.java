// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.project.DeleteProject.Input;
import com.google.gerrit.server.project.delete.CacheDeleteHandler;
import com.google.gerrit.server.project.delete.CannotDeleteProjectException;
import com.google.gerrit.server.project.delete.DatabaseDeleteHandler;
import com.google.gerrit.server.project.delete.FilesystemDeleteHandler;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;

import java.io.IOException;

@RequiresCapability(GlobalCapability.CREATE_PROJECT)
class DeleteProject implements RestModifyView<ProjectResource, Input>,
    UiAction<ProjectResource> {
  static class Input {
    boolean preserve;
    boolean force;
  }

  protected final AllProjectsName allProjectsName;
  private final DatabaseDeleteHandler dbHandler;
  private final FilesystemDeleteHandler fsHandler;
  private final CacheDeleteHandler cacheHandler;

  @Inject
  DeleteProject(AllProjectsNameProvider allProjectsNameProvider,
      DatabaseDeleteHandler dbHandler,
      FilesystemDeleteHandler fsHandler,
      CacheDeleteHandler cacheHandler) {
    this.allProjectsName = allProjectsNameProvider.get();
    this.dbHandler = dbHandler;
    this.fsHandler = fsHandler;
    this.cacheHandler = cacheHandler;
  }

  @Override
  public Object apply(ProjectResource rsrc, Input input)
      throws ResourceNotFoundException, ResourceConflictException,
      MethodNotAllowedException, OrmException, IOException {
    Project project = rsrc.getControl().getProject();
    if (project.getNameKey().equals(allProjectsName)) {
      throw new MethodNotAllowedException();
    }

    try {
      dbHandler.assertCanDelete(project);
    } catch (CannotDeleteProjectException e) {
      throw new ResourceConflictException(e.getMessage());
    }

    if (input == null || !input.force) {
      if (dbHandler.hasOpenedChanged(project)) {
        throw new ResourceConflictException(String.format(
            "Project %s has open changes", project.getName()));
      }
    }

    dbHandler.delete(project);
    try {
      fsHandler.delete(project, input == null ? false : input.preserve);
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException();
    }
    cacheHandler.delete(project);
    return Response.none();
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Delete...")
        .setTitle(isAllProjects(rsrc)
            ? String.format("No deletion of %s project",
                allProjectsName)
            : String.format("Delete project %s", rsrc.getName()))
        .setEnabled(!isAllProjects(rsrc));
  }

  protected boolean isAllProjects(ProjectResource rsrc) {
    return (rsrc.getControl().getProject()
        .getNameKey().equals(allProjectsName));
  }
}
