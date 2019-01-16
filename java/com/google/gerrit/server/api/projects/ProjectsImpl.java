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

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.project.ListProjects;
import com.google.gerrit.server.restapi.project.ListProjects.FilterType;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.gerrit.server.restapi.project.QueryProjects;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.SortedMap;

@Singleton
class ProjectsImpl implements Projects {
  private final ProjectsCollection projects;
  private final ProjectApiImpl.Factory api;
  private final Provider<ListProjects> listProvider;
  private final Provider<QueryProjects> queryProvider;

  @Inject
  ProjectsImpl(
      ProjectsCollection projects,
      ProjectApiImpl.Factory api,
      Provider<ListProjects> listProvider,
      Provider<QueryProjects> queryProvider) {
    this.projects = projects;
    this.api = api;
    this.listProvider = listProvider;
    this.queryProvider = queryProvider;
  }

  @Override
  public ProjectApi name(String name) throws RestApiException {
    try {
      return api.create(projects.parse(name));
    } catch (UnprocessableEntityException e) {
      return api.create(name);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve project", e);
    }
  }

  @Override
  public ProjectApi create(String name) throws RestApiException {
    ProjectInput in = new ProjectInput();
    in.name = name;
    return create(in);
  }

  @Override
  public ProjectApi create(ProjectInput in) throws RestApiException {
    if (in.name == null) {
      throw new BadRequestException("input.name is required");
    }
    return name(in.name).create(in);
  }

  @Override
  public ListRequest list() {
    return new ListRequest() {
      @Override
      public SortedMap<String, ProjectInfo> getAsMap() throws RestApiException {
        try {
          return list(this);
        } catch (Exception e) {
          throw asRestApiException("project list unavailable", e);
        }
      }
    };
  }

  private SortedMap<String, ProjectInfo> list(ListRequest request)
      throws RestApiException, PermissionBackendException {
    ListProjects lp = listProvider.get();
    lp.setShowDescription(request.getDescription());
    lp.setLimit(request.getLimit());
    lp.setStart(request.getStart());
    lp.setMatchPrefix(request.getPrefix());

    lp.setMatchSubstring(request.getSubstring());
    lp.setMatchRegex(request.getRegex());
    lp.setShowTree(request.getShowTree());
    for (String branch : request.getBranches()) {
      lp.addShowBranch(branch);
    }

    FilterType type;
    switch (request.getFilterType()) {
      case ALL:
        type = FilterType.ALL;
        break;
      case CODE:
        type = FilterType.CODE;
        break;
      case PERMISSIONS:
        type = FilterType.PERMISSIONS;
        break;
      default:
        throw new BadRequestException("Unknown filter type: " + request.getFilterType());
    }
    lp.setFilterType(type);

    lp.setAll(request.isAll());

    lp.setState(request.getState());

    return lp.apply();
  }

  @Override
  public QueryRequest query() {
    return new QueryRequest() {
      @Override
      public List<ProjectInfo> get() throws RestApiException {
        return ProjectsImpl.this.query(this);
      }
    };
  }

  @Override
  public QueryRequest query(String query) {
    return query().withQuery(query);
  }

  private List<ProjectInfo> query(QueryRequest r) throws RestApiException {
    try {
      return queryProvider
          .get()
          .withQuery(r.getQuery())
          .withLimit(r.getLimit())
          .withStart(r.getStart())
          .apply();
    } catch (StorageException e) {
      throw new RestApiException("Cannot query projects", e);
    }
  }
}
