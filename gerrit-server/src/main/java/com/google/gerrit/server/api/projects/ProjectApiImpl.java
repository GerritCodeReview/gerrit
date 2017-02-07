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

import static com.google.gerrit.server.account.CapabilityUtils.checkRequiresCapability;

import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ChildProjectApi;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.TagApi;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ChildProjectsCollection;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.DeleteBranches;
import com.google.gerrit.server.project.GetAccess;
import com.google.gerrit.server.project.GetConfig;
import com.google.gerrit.server.project.GetDescription;
import com.google.gerrit.server.project.ListBranches;
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ListTags;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.server.project.PutConfig;
import com.google.gerrit.server.project.PutDescription;
import com.google.gerrit.server.project.SetAccess;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class ProjectApiImpl implements ProjectApi {
  interface Factory {
    ProjectApiImpl create(ProjectResource project);

    ProjectApiImpl create(String name);
  }

  private final CurrentUser user;
  private final CreateProject.Factory createProjectFactory;
  private final ProjectApiImpl.Factory projectApi;
  private final ProjectsCollection projects;
  private final GetDescription getDescription;
  private final PutDescription putDescription;
  private final ChildProjectApiImpl.Factory childApi;
  private final ChildProjectsCollection children;
  private final ProjectResource project;
  private final ProjectJson projectJson;
  private final String name;
  private final BranchApiImpl.Factory branchApi;
  private final TagApiImpl.Factory tagApi;
  private final GetAccess getAccess;
  private final SetAccess setAccess;
  private final GetConfig getConfig;
  private final PutConfig putConfig;
  private final ListBranches listBranches;
  private final ListTags listTags;
  private final DeleteBranches deleteBranches;

  @AssistedInject
  ProjectApiImpl(
      CurrentUser user,
      CreateProject.Factory createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      GetDescription getDescription,
      PutDescription putDescription,
      ChildProjectApiImpl.Factory childApi,
      ChildProjectsCollection children,
      ProjectJson projectJson,
      BranchApiImpl.Factory branchApiFactory,
      TagApiImpl.Factory tagApiFactory,
      GetAccess getAccess,
      SetAccess setAccess,
      GetConfig getConfig,
      PutConfig putConfig,
      ListBranches listBranches,
      ListTags listTags,
      DeleteBranches deleteBranches,
      @Assisted ProjectResource project) {
    this(
        user,
        createProjectFactory,
        projectApi,
        projects,
        getDescription,
        putDescription,
        childApi,
        children,
        projectJson,
        branchApiFactory,
        tagApiFactory,
        getAccess,
        setAccess,
        getConfig,
        putConfig,
        listBranches,
        listTags,
        deleteBranches,
        project,
        null);
  }

  @AssistedInject
  ProjectApiImpl(
      CurrentUser user,
      CreateProject.Factory createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      GetDescription getDescription,
      PutDescription putDescription,
      ChildProjectApiImpl.Factory childApi,
      ChildProjectsCollection children,
      ProjectJson projectJson,
      BranchApiImpl.Factory branchApiFactory,
      TagApiImpl.Factory tagApiFactory,
      GetAccess getAccess,
      SetAccess setAccess,
      GetConfig getConfig,
      PutConfig putConfig,
      ListBranches listBranches,
      ListTags listTags,
      DeleteBranches deleteBranches,
      @Assisted String name) {
    this(
        user,
        createProjectFactory,
        projectApi,
        projects,
        getDescription,
        putDescription,
        childApi,
        children,
        projectJson,
        branchApiFactory,
        tagApiFactory,
        getAccess,
        setAccess,
        getConfig,
        putConfig,
        listBranches,
        listTags,
        deleteBranches,
        null,
        name);
  }

  private ProjectApiImpl(
      CurrentUser user,
      CreateProject.Factory createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      GetDescription getDescription,
      PutDescription putDescription,
      ChildProjectApiImpl.Factory childApi,
      ChildProjectsCollection children,
      ProjectJson projectJson,
      BranchApiImpl.Factory branchApiFactory,
      TagApiImpl.Factory tagApiFactory,
      GetAccess getAccess,
      SetAccess setAccess,
      GetConfig getConfig,
      PutConfig putConfig,
      ListBranches listBranches,
      ListTags listTags,
      DeleteBranches deleteBranches,
      ProjectResource project,
      String name) {
    this.user = user;
    this.createProjectFactory = createProjectFactory;
    this.projectApi = projectApi;
    this.projects = projects;
    this.getDescription = getDescription;
    this.putDescription = putDescription;
    this.childApi = childApi;
    this.children = children;
    this.projectJson = projectJson;
    this.project = project;
    this.name = name;
    this.branchApi = branchApiFactory;
    this.tagApi = tagApiFactory;
    this.getAccess = getAccess;
    this.setAccess = setAccess;
    this.getConfig = getConfig;
    this.putConfig = putConfig;
    this.listBranches = listBranches;
    this.listTags = listTags;
    this.deleteBranches = deleteBranches;
  }

  @Override
  public ProjectApi create() throws RestApiException {
    return create(new ProjectInput());
  }

  @Override
  public ProjectApi create(ProjectInput in) throws RestApiException {
    try {
      if (name == null) {
        throw new ResourceConflictException("Project already exists");
      }
      if (in.name != null && !name.equals(in.name)) {
        throw new BadRequestException("name must match input.name");
      }
      checkRequiresCapability(user, null, CreateProject.class);
      createProjectFactory.create(name).apply(TopLevelResource.INSTANCE, in);
      return projectApi.create(projects.parse(name));
    } catch (IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot create project: " + e.getMessage(), e);
    }
  }

  @Override
  public ProjectInfo get() throws RestApiException {
    if (project == null) {
      throw new ResourceNotFoundException(name);
    }
    return projectJson.format(project);
  }

  @Override
  public String description() throws RestApiException {
    return getDescription.apply(checkExists());
  }

  @Override
  public ProjectAccessInfo access() throws RestApiException {
    try {
      return getAccess.apply(checkExists());
    } catch (IOException e) {
      throw new RestApiException("Cannot get access rights", e);
    }
  }

  @Override
  public ProjectAccessInfo access(ProjectAccessInput p) throws RestApiException {
    try {
      return setAccess.apply(checkExists(), p);
    } catch (IOException e) {
      throw new RestApiException("Cannot put access rights", e);
    }
  }

  @Override
  public void description(DescriptionInput in) throws RestApiException {
    try {
      putDescription.apply(checkExists(), in);
    } catch (IOException e) {
      throw new RestApiException("Cannot put project description", e);
    }
  }

  @Override
  public ConfigInfo config() throws RestApiException {
    return getConfig.apply(checkExists());
  }

  @Override
  public ConfigInfo config(ConfigInput in) throws RestApiException {
    return putConfig.apply(checkExists(), in);
  }

  @Override
  public ListRefsRequest<BranchInfo> branches() {
    return new ListRefsRequest<BranchInfo>() {
      @Override
      public List<BranchInfo> get() throws RestApiException {
        return listBranches(this);
      }
    };
  }

  private List<BranchInfo> listBranches(ListRefsRequest<BranchInfo> request)
      throws RestApiException {
    listBranches.setLimit(request.getLimit());
    listBranches.setStart(request.getStart());
    listBranches.setMatchSubstring(request.getSubstring());
    listBranches.setMatchRegex(request.getRegex());
    try {
      return listBranches.apply(checkExists());
    } catch (IOException e) {
      throw new RestApiException("Cannot list branches", e);
    }
  }

  @Override
  public ListRefsRequest<TagInfo> tags() {
    return new ListRefsRequest<TagInfo>() {
      @Override
      public List<TagInfo> get() throws RestApiException {
        return listTags(this);
      }
    };
  }

  private List<TagInfo> listTags(ListRefsRequest<TagInfo> request) throws RestApiException {
    listTags.setLimit(request.getLimit());
    listTags.setStart(request.getStart());
    listTags.setMatchSubstring(request.getSubstring());
    listTags.setMatchRegex(request.getRegex());
    try {
      return listTags.apply(checkExists());
    } catch (IOException e) {
      throw new RestApiException("Cannot list tags", e);
    }
  }

  @Override
  public List<ProjectInfo> children() throws RestApiException {
    return children(false);
  }

  @Override
  public List<ProjectInfo> children(boolean recursive) throws RestApiException {
    ListChildProjects list = children.list();
    list.setRecursive(recursive);
    return list.apply(checkExists());
  }

  @Override
  public ChildProjectApi child(String name) throws RestApiException {
    try {
      return childApi.create(children.parse(checkExists(), IdString.fromDecoded(name)));
    } catch (IOException e) {
      throw new RestApiException("Cannot parse child project", e);
    }
  }

  @Override
  public BranchApi branch(String ref) throws ResourceNotFoundException {
    return branchApi.create(checkExists(), ref);
  }

  @Override
  public TagApi tag(String ref) throws ResourceNotFoundException {
    return tagApi.create(checkExists(), ref);
  }

  @Override
  public void deleteBranches(DeleteBranchesInput in) throws RestApiException {
    try {
      deleteBranches.apply(checkExists(), in);
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot delete branches", e);
    }
  }

  private ProjectResource checkExists() throws ResourceNotFoundException {
    if (project == null) {
      throw new ResourceNotFoundException(name);
    }
    return project;
  }
}
