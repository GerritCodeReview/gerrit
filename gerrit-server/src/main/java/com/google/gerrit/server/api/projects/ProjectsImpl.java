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

package com.google.gerrit.server.api.projects;

import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.inject.Inject;

import java.io.IOException;

class ProjectsImpl implements Projects {
  private final ProjectsCollection projects;
  private final ProjectApiImpl.Factory api;

  @Inject
  ProjectsImpl(ProjectsCollection projects, ProjectApiImpl.Factory api) {
    this.projects = projects;
    this.api = api;
  }

  @Override
  public ProjectApi name(String name) throws RestApiException {
    try {
      return api.create(projects.parse(name));
    } catch (UnprocessableEntityException e) {
      return api.create(name);
    } catch (IOException e) {
      throw new RestApiException("Cannot retrieve project");
    }
  }
}
