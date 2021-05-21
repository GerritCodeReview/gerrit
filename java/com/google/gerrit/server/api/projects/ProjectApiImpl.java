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
import static com.google.gerrit.server.restapi.project.DashboardsCollection.DEFAULT_DASHBOARD_NAME;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.config.AccessCheckInfo;
import com.google.gerrit.extensions.api.config.AccessCheckInput;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.CheckProjectInput;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo;
import com.google.gerrit.extensions.api.projects.ChildProjectApi;
import com.google.gerrit.extensions.api.projects.CommitApi;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.DashboardApi;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.api.projects.DeleteTagsInput;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.api.projects.HeadInput;
import com.google.gerrit.extensions.api.projects.IndexProjectInput;
import com.google.gerrit.extensions.api.projects.LabelApi;
import com.google.gerrit.extensions.api.projects.ParentInput;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.SubmitRequirementApi;
import com.google.gerrit.extensions.api.projects.TagApi;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.common.BatchLabelInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.Check;
import com.google.gerrit.server.restapi.project.CheckAccess;
import com.google.gerrit.server.restapi.project.ChildProjectsCollection;
import com.google.gerrit.server.restapi.project.CommitsCollection;
import com.google.gerrit.server.restapi.project.CommitsIncludedInRefs;
import com.google.gerrit.server.restapi.project.CreateAccessChange;
import com.google.gerrit.server.restapi.project.CreateProject;
import com.google.gerrit.server.restapi.project.DeleteBranches;
import com.google.gerrit.server.restapi.project.DeleteTags;
import com.google.gerrit.server.restapi.project.GetAccess;
import com.google.gerrit.server.restapi.project.GetConfig;
import com.google.gerrit.server.restapi.project.GetDescription;
import com.google.gerrit.server.restapi.project.GetHead;
import com.google.gerrit.server.restapi.project.GetParent;
import com.google.gerrit.server.restapi.project.Index;
import com.google.gerrit.server.restapi.project.IndexChanges;
import com.google.gerrit.server.restapi.project.ListBranches;
import com.google.gerrit.server.restapi.project.ListDashboards;
import com.google.gerrit.server.restapi.project.ListLabels;
import com.google.gerrit.server.restapi.project.ListSubmitRequirements;
import com.google.gerrit.server.restapi.project.ListTags;
import com.google.gerrit.server.restapi.project.PostLabels;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.gerrit.server.restapi.project.PutConfig;
import com.google.gerrit.server.restapi.project.PutDescription;
import com.google.gerrit.server.restapi.project.SetAccess;
import com.google.gerrit.server.restapi.project.SetHead;
import com.google.gerrit.server.restapi.project.SetParent;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProjectApiImpl implements ProjectApi {
  interface Factory {
    ProjectApiImpl create(ProjectResource project);

    ProjectApiImpl create(String name);
  }

  private final PermissionBackend permissionBackend;
  private final CreateProject createProject;
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
  private final CreateAccessChange createAccessChange;
  private final GetConfig getConfig;
  private final PutConfig putConfig;
  private final CommitsIncludedInRefs commitsIncludedInRefs;
  private final Provider<ListBranches> listBranches;
  private final Provider<ListTags> listTags;
  private final DeleteBranches deleteBranches;
  private final DeleteTags deleteTags;
  private final CommitsCollection commitsCollection;
  private final CommitApiImpl.Factory commitApi;
  private final DashboardApiImpl.Factory dashboardApi;
  private final CheckAccess checkAccess;
  private final Check check;
  private final Provider<ListDashboards> listDashboards;
  private final GetHead getHead;
  private final SetHead setHead;
  private final GetParent getParent;
  private final SetParent setParent;
  private final Index index;
  private final IndexChanges indexChanges;
  private final Provider<ListLabels> listLabels;
  private final Provider<ListSubmitRequirements> listSubmitRequirements;
  private final PostLabels postLabels;
  private final LabelApiImpl.Factory labelApi;
  private final SubmitRequirementApiImpl.Factory submitRequirementApi;

  @AssistedInject
  ProjectApiImpl(
      PermissionBackend permissionBackend,
      CreateProject createProject,
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
      CreateAccessChange createAccessChange,
      GetConfig getConfig,
      PutConfig putConfig,
      CommitsIncludedInRefs commitsIncludedInRefs,
      Provider<ListBranches> listBranches,
      Provider<ListTags> listTags,
      DeleteBranches deleteBranches,
      DeleteTags deleteTags,
      CommitsCollection commitsCollection,
      CommitApiImpl.Factory commitApi,
      DashboardApiImpl.Factory dashboardApi,
      CheckAccess checkAccess,
      Check check,
      Provider<ListDashboards> listDashboards,
      GetHead getHead,
      SetHead setHead,
      GetParent getParent,
      SetParent setParent,
      Index index,
      IndexChanges indexChanges,
      Provider<ListLabels> listLabels,
      Provider<ListSubmitRequirements> listSubmitRequirements,
      PostLabels postLabels,
      LabelApiImpl.Factory labelApi,
      SubmitRequirementApiImpl.Factory submitRequirementApi,
      @Assisted ProjectResource project) {
    this(
        permissionBackend,
        createProject,
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
        createAccessChange,
        getConfig,
        putConfig,
        commitsIncludedInRefs,
        listBranches,
        listTags,
        deleteBranches,
        deleteTags,
        project,
        commitsCollection,
        commitApi,
        dashboardApi,
        checkAccess,
        check,
        listDashboards,
        getHead,
        setHead,
        getParent,
        setParent,
        index,
        indexChanges,
        listLabels,
        listSubmitRequirements,
        postLabels,
        labelApi,
        submitRequirementApi,
        null);
  }

  @AssistedInject
  ProjectApiImpl(
      PermissionBackend permissionBackend,
      CreateProject createProject,
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
      CreateAccessChange createAccessChange,
      GetConfig getConfig,
      PutConfig putConfig,
      CommitsIncludedInRefs commitsIncludedInRefs,
      Provider<ListBranches> listBranches,
      Provider<ListTags> listTags,
      DeleteBranches deleteBranches,
      DeleteTags deleteTags,
      CommitsCollection commitsCollection,
      CommitApiImpl.Factory commitApi,
      DashboardApiImpl.Factory dashboardApi,
      CheckAccess checkAccess,
      Check check,
      Provider<ListDashboards> listDashboards,
      GetHead getHead,
      SetHead setHead,
      GetParent getParent,
      SetParent setParent,
      Index index,
      IndexChanges indexChanges,
      Provider<ListLabels> listLabels,
      Provider<ListSubmitRequirements> listSubmitRequirements,
      PostLabels postLabels,
      LabelApiImpl.Factory labelApi,
      SubmitRequirementApiImpl.Factory submitRequirementApi,
      @Assisted String name) {
    this(
        permissionBackend,
        createProject,
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
        createAccessChange,
        getConfig,
        putConfig,
        commitsIncludedInRefs,
        listBranches,
        listTags,
        deleteBranches,
        deleteTags,
        null,
        commitsCollection,
        commitApi,
        dashboardApi,
        checkAccess,
        check,
        listDashboards,
        getHead,
        setHead,
        getParent,
        setParent,
        index,
        indexChanges,
        listLabels,
        listSubmitRequirements,
        postLabels,
        labelApi,
        submitRequirementApi,
        name);
  }

  private ProjectApiImpl(
      PermissionBackend permissionBackend,
      CreateProject createProject,
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
      CreateAccessChange createAccessChange,
      GetConfig getConfig,
      PutConfig putConfig,
      CommitsIncludedInRefs commitsIncludedInRefs,
      Provider<ListBranches> listBranches,
      Provider<ListTags> listTags,
      DeleteBranches deleteBranches,
      DeleteTags deleteTags,
      ProjectResource project,
      CommitsCollection commitsCollection,
      CommitApiImpl.Factory commitApi,
      DashboardApiImpl.Factory dashboardApi,
      CheckAccess checkAccess,
      Check check,
      Provider<ListDashboards> listDashboards,
      GetHead getHead,
      SetHead setHead,
      GetParent getParent,
      SetParent setParent,
      Index index,
      IndexChanges indexChanges,
      Provider<ListLabels> listLabels,
      Provider<ListSubmitRequirements> listSubmitRequirements,
      PostLabels postLabels,
      LabelApiImpl.Factory labelApi,
      SubmitRequirementApiImpl.Factory submitRequirementApi,
      String name) {
    this.permissionBackend = permissionBackend;
    this.createProject = createProject;
    this.projectApi = projectApi;
    this.projects = projects;
    this.getDescription = getDescription;
    this.putDescription = putDescription;
    this.childApi = childApi;
    this.children = children;
    this.projectJson = projectJson;
    this.project = project;
    this.branchApi = branchApiFactory;
    this.tagApi = tagApiFactory;
    this.getAccess = getAccess;
    this.setAccess = setAccess;
    this.getConfig = getConfig;
    this.putConfig = putConfig;
    this.commitsIncludedInRefs = commitsIncludedInRefs;
    this.listBranches = listBranches;
    this.listTags = listTags;
    this.deleteBranches = deleteBranches;
    this.deleteTags = deleteTags;
    this.commitsCollection = commitsCollection;
    this.commitApi = commitApi;
    this.createAccessChange = createAccessChange;
    this.dashboardApi = dashboardApi;
    this.checkAccess = checkAccess;
    this.check = check;
    this.listDashboards = listDashboards;
    this.getHead = getHead;
    this.setHead = setHead;
    this.getParent = getParent;
    this.setParent = setParent;
    this.name = name;
    this.index = index;
    this.indexChanges = indexChanges;
    this.listLabels = listLabels;
    this.listSubmitRequirements = listSubmitRequirements;
    this.postLabels = postLabels;
    this.labelApi = labelApi;
    this.submitRequirementApi = submitRequirementApi;
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
      permissionBackend
          .currentUser()
          .checkAny(GlobalPermission.fromAnnotation(createProject.getClass()));
      createProject.apply(TopLevelResource.INSTANCE, IdString.fromDecoded(name), in);
      return projectApi.create(projects.parse(name));
    } catch (Exception e) {
      throw asRestApiException("Cannot create project: " + e.getMessage(), e);
    }
  }

  @Override
  public ProjectInfo get() throws RestApiException {
    if (project == null) {
      throw new ResourceNotFoundException(name);
    }
    return projectJson.format(project.getProjectState());
  }

  @Override
  public String description() throws RestApiException {
    try {
      return getDescription.apply(checkExists()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get description", e);
    }
  }

  @Override
  public ProjectAccessInfo access() throws RestApiException {
    try {
      return getAccess.apply(checkExists()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get access rights", e);
    }
  }

  @Override
  public ProjectAccessInfo access(ProjectAccessInput p) throws RestApiException {
    try {
      return setAccess.apply(checkExists(), p).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot put access rights", e);
    }
  }

  @Override
  public ChangeInfo accessChange(ProjectAccessInput p) throws RestApiException {
    try {
      return createAccessChange.apply(checkExists(), p).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot put access right change", e);
    }
  }

  @Override
  public AccessCheckInfo checkAccess(AccessCheckInput in) throws RestApiException {
    try {
      return checkAccess.apply(checkExists(), in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot check access rights", e);
    }
  }

  @Override
  public CheckProjectResultInfo check(CheckProjectInput in) throws RestApiException {
    try {
      return check.apply(checkExists(), in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot check project", e);
    }
  }

  @Override
  public void description(DescriptionInput in) throws RestApiException {
    try {
      putDescription.apply(checkExists(), in);
    } catch (Exception e) {
      throw asRestApiException("Cannot put project description", e);
    }
  }

  @Override
  public ConfigInfo config() throws RestApiException {
    try {
      return getConfig.apply(checkExists()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get config", e);
    }
  }

  @Override
  public ConfigInfo config(ConfigInput in) throws RestApiException {
    try {
      return putConfig.apply(checkExists(), in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot list tags", e);
    }
  }

  @Override
  public Map<String, Set<String>> commitsIn(Collection<String> commits, Collection<String> refs)
      throws RestApiException {
    try {
      commitsIncludedInRefs.addCommits(commits);
      commitsIncludedInRefs.addRefs(refs);
      return commitsIncludedInRefs.apply(project).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot list commits included in refs", e);
    }
  }

  @Override
  public ListRefsRequest<BranchInfo> branches() {
    return new ListRefsRequest<>() {
      @Override
      public List<BranchInfo> get() throws RestApiException {
        try {
          return listBranches.get().request(this).apply(checkExists()).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot list branches", e);
        }
      }
    };
  }

  @Override
  public ListRefsRequest<TagInfo> tags() {
    return new ListRefsRequest<>() {
      @Override
      public List<TagInfo> get() throws RestApiException {
        try {
          return listTags.get().request(this).apply(checkExists()).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot list tags", e);
        }
      }
    };
  }

  @Override
  public List<ProjectInfo> children() throws RestApiException {
    return children(false);
  }

  @Override
  public List<ProjectInfo> children(boolean recursive) throws RestApiException {
    try {
      return children.list().withRecursive(recursive).apply(checkExists()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot list children", e);
    }
  }

  @Override
  public List<ProjectInfo> children(int limit) throws RestApiException {
    try {
      return children.list().withLimit(limit).apply(checkExists()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot list children", e);
    }
  }

  @Override
  public ChildProjectApi child(String name) throws RestApiException {
    try {
      return childApi.create(children.parse(checkExists(), IdString.fromDecoded(name)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse child project", e);
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
    } catch (Exception e) {
      throw asRestApiException("Cannot delete branches", e);
    }
  }

  @Override
  public void deleteTags(DeleteTagsInput in) throws RestApiException {
    try {
      deleteTags.apply(checkExists(), in);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete tags", e);
    }
  }

  @Override
  public CommitApi commit(String commit) throws RestApiException {
    try {
      return commitApi.create(commitsCollection.parse(checkExists(), IdString.fromDecoded(commit)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse commit", e);
    }
  }

  @Override
  public DashboardApi dashboard(String name) throws RestApiException {
    try {
      return dashboardApi.create(checkExists(), name);
    } catch (Exception e) {
      throw asRestApiException("Cannot parse dashboard", e);
    }
  }

  @Override
  public DashboardApi defaultDashboard() throws RestApiException {
    return dashboard(DEFAULT_DASHBOARD_NAME);
  }

  @Override
  public void defaultDashboard(String name) throws RestApiException {
    try {
      dashboardApi.create(checkExists(), name).setDefault();
    } catch (Exception e) {
      throw asRestApiException("Cannot set default dashboard", e);
    }
  }

  @Override
  public void removeDefaultDashboard() throws RestApiException {
    try {
      dashboardApi.create(checkExists(), null).setDefault();
    } catch (Exception e) {
      throw asRestApiException("Cannot remove default dashboard", e);
    }
  }

  @Override
  public ListDashboardsRequest dashboards() throws RestApiException {
    return new ListDashboardsRequest() {
      @Override
      public List<DashboardInfo> get() throws RestApiException {
        try {
          List<?> r = listDashboards.get().apply(checkExists()).value();
          if (r.isEmpty()) {
            return Collections.emptyList();
          }
          if (r.get(0) instanceof DashboardInfo) {
            return r.stream().map(i -> (DashboardInfo) i).collect(toList());
          }
          throw new NotImplementedException("list with inheritance");
        } catch (Exception e) {
          throw asRestApiException("Cannot list dashboards", e);
        }
      }
    };
  }

  @Override
  public String head() throws RestApiException {
    try {
      return getHead.apply(checkExists()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get HEAD", e);
    }
  }

  @Override
  public void head(String head) throws RestApiException {
    HeadInput input = new HeadInput();
    input.ref = head;
    try {
      setHead.apply(checkExists(), input);
    } catch (Exception e) {
      throw asRestApiException("Cannot set HEAD", e);
    }
  }

  @Override
  public String parent() throws RestApiException {
    try {
      return getParent.apply(checkExists()).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get parent", e);
    }
  }

  @Override
  public void parent(String parent) throws RestApiException {
    try {
      ParentInput input = new ParentInput();
      input.parent = parent;
      setParent.apply(checkExists(), input);
    } catch (Exception e) {
      throw asRestApiException("Cannot set parent", e);
    }
  }

  @Override
  public void index(boolean indexChildren) throws RestApiException {
    try {
      IndexProjectInput input = new IndexProjectInput();
      input.indexChildren = indexChildren;
      index.apply(checkExists(), input);
    } catch (Exception e) {
      throw asRestApiException("Cannot index project", e);
    }
  }

  @Override
  public void indexChanges() throws RestApiException {
    try {
      indexChanges.apply(checkExists(), new Input());
    } catch (Exception e) {
      throw asRestApiException("Cannot index changes", e);
    }
  }

  private ProjectResource checkExists() throws ResourceNotFoundException {
    if (project == null) {
      throw new ResourceNotFoundException(name);
    }
    return project;
  }

  @Override
  public ListLabelsRequest labels() {
    return new ListLabelsRequest() {
      @Override
      public List<LabelDefinitionInfo> get() throws RestApiException {
        try {
          return listLabels.get().withInherited(inherited).apply(checkExists()).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot list labels", e);
        }
      }
    };
  }

  @Override
  public ListSubmitRequirementsRequest submitRequirements() {
    return new ListSubmitRequirementsRequest() {
      @Override
      public List<SubmitRequirementInfo> get() throws RestApiException {
        try {
          return listSubmitRequirements.get().withInherited(inherited).apply(checkExists()).value();
        } catch (Exception e) {
          throw asRestApiException("Cannot list submit requirements", e);
        }
      }
    };
  }

  @Override
  public LabelApi label(String labelName) throws RestApiException {
    try {
      return labelApi.create(checkExists(), labelName);
    } catch (Exception e) {
      throw asRestApiException("Cannot parse label", e);
    }
  }

  @Override
  public SubmitRequirementApi submitRequirement(String name) throws RestApiException {
    try {
      return submitRequirementApi.create(checkExists(), name);
    } catch (Exception e) {
      throw asRestApiException("Cannot parse submit requirement", e);
    }
  }

  @Override
  public void labels(BatchLabelInput input) throws RestApiException {
    try {
      postLabels.apply(checkExists(), input);
    } catch (Exception e) {
      throw asRestApiException("Cannot update labels", e);
    }
  }
}
