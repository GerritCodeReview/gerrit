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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.project.ListProjects;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Constants;

import java.io.IOException;
import java.util.List;

@Singleton
class ProjectsImpl extends Projects.NotImplemented implements Projects {
  private final ProjectsCollection projects;
  private final ProjectApiImpl.Factory api;
  private final Provider<ListProjects> listProvider;

  @Inject
  ProjectsImpl(ProjectsCollection projects,
      ProjectApiImpl.Factory api,
      Provider<ListProjects> listProvider) {
    this.projects = projects;
    this.api = api;
    this.listProvider = listProvider;
  }

  @Override
  public ProjectApi name(String name) throws RestApiException {
    if (name.endsWith(Constants.DOT_GIT_EXT)) {
      name = name.substring(0, name.length() - Constants.DOT_GIT_EXT.length());
    }

    try {
      return api.create(projects.parse(name));
    } catch (UnprocessableEntityException e) {
      return api.create(name);
    } catch (IOException e) {
      throw new RestApiException("Cannot retrieve project");
    }
  }

  @Override
  public ListRequest list() {
    return new ListRequest() {
      @Override
      public List<ProjectInfo> get() throws RestApiException {
        return list(this);
      }
    };
  }

  private List<ProjectInfo> list(ListRequest request) throws RestApiException {
    ListProjects lp = listProvider.get();
    lp.setShowDescription(request.getDescription());
    lp.setLimit(request.getLimit());
    lp.setStart(request.getStart());
    lp.setMatchPrefix(request.getPrefix());

    return ImmutableList.copyOf(lp.apply().values());
  }
}
