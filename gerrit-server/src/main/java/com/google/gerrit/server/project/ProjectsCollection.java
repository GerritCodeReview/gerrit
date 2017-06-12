// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Constants;

@Singleton
public class ProjectsCollection
    implements RestCollection<TopLevelResource, ProjectResource>, AcceptsCreate<TopLevelResource> {
  private final DynamicMap<RestView<ProjectResource>> views;
  private final Provider<ListProjects> list;
  private final ProjectControl.GenericFactory controlFactory;
  private final Provider<CurrentUser> user;
  private final CreateProject.Factory createProjectFactory;

  @Inject
  ProjectsCollection(
      DynamicMap<RestView<ProjectResource>> views,
      Provider<ListProjects> list,
      ProjectControl.GenericFactory controlFactory,
      CreateProject.Factory factory,
      Provider<CurrentUser> user) {
    this.views = views;
    this.list = list;
    this.controlFactory = controlFactory;
    this.user = user;
    this.createProjectFactory = factory;
  }

  @Override
  public RestView<TopLevelResource> list() {
    return list.get().setFormat(OutputFormat.JSON);
  }

  @Override
  public ProjectResource parse(TopLevelResource parent, IdString id)
      throws ResourceNotFoundException, IOException {
    ProjectResource rsrc = _parse(id.get());
    if (rsrc == null) {
      throw new ResourceNotFoundException(id);
    }
    return rsrc;
  }

  /**
   * Parses a project ID from a request body and returns the project.
   *
   * @param id ID of the project, can be a project name
   * @return the project
   * @throws UnprocessableEntityException thrown if the project ID cannot be resolved or if the
   *     project is not visible to the calling user
   * @throws IOException thrown when there is an error.
   */
  public ProjectResource parse(String id) throws UnprocessableEntityException, IOException {
    ProjectResource rsrc = _parse(id);
    if (rsrc == null) {
      throw new UnprocessableEntityException(String.format("Project Not Found: %s", id));
    }
    return rsrc;
  }

  private ProjectResource _parse(String id) throws IOException {
    if (id.endsWith(Constants.DOT_GIT_EXT)) {
      id = id.substring(0, id.length() - Constants.DOT_GIT_EXT.length());
    }
    ProjectControl ctl;
    try {
      ctl = controlFactory.controlFor(new Project.NameKey(id), user.get());
    } catch (NoSuchProjectException e) {
      return null;
    }
    if (!ctl.isVisible() && !ctl.isOwner()) {
      return null;
    }
    return new ProjectResource(ctl);
  }

  @Override
  public DynamicMap<RestView<ProjectResource>> views() {
    return views;
  }

  @SuppressWarnings("unchecked")
  @Override
  public CreateProject create(TopLevelResource parent, IdString name) {
    return createProjectFactory.create(name.get());
  }
}
