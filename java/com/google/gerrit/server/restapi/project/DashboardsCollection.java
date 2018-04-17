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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.reviewdb.client.RefNames.REFS_DASHBOARDS;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.api.projects.DashboardSectionInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UrlEncoded;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.DashboardResource;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class DashboardsCollection
    implements ChildCollection<ProjectResource, DashboardResource>, AcceptsCreate<ProjectResource> {
  public static final String DEFAULT_DASHBOARD_NAME = "default";

  private final GitRepositoryManager gitManager;
  private final ProjectAccessor.Factory projectAccessorFactory;
  private final DynamicMap<RestView<DashboardResource>> views;
  private final Provider<ListDashboards> list;
  private final Provider<SetDefaultDashboard.CreateDefault> createDefault;
  private final PermissionBackend permissionBackend;

  @Inject
  DashboardsCollection(
      GitRepositoryManager gitManager,
      ProjectAccessor.Factory projectAccessorFactory,
      DynamicMap<RestView<DashboardResource>> views,
      Provider<ListDashboards> list,
      Provider<SetDefaultDashboard.CreateDefault> createDefault,
      PermissionBackend permissionBackend) {
    this.gitManager = gitManager;
    this.projectAccessorFactory = projectAccessorFactory;
    this.views = views;
    this.list = list;
    this.createDefault = createDefault;
    this.permissionBackend = permissionBackend;
  }

  public static boolean isDefaultDashboard(@Nullable String id) {
    return DEFAULT_DASHBOARD_NAME.equals(id);
  }

  public static boolean isDefaultDashboard(@Nullable IdString id) {
    return id != null && isDefaultDashboard(id.toString());
  }

  @Override
  public RestView<ProjectResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @Override
  public RestModifyView<ProjectResource, ?> create(ProjectResource parent, IdString id)
      throws RestApiException {
    parent.getProjectState().checkStatePermitsWrite();
    if (isDefaultDashboard(id)) {
      return createDefault.get();
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DashboardResource parse(ProjectResource parent, IdString id)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    parent.getProjectState().checkStatePermitsRead();
    if (isDefaultDashboard(id)) {
      return DashboardResource.projectDefault(parent.getProjectAccessor(), parent.getUser());
    }

    DashboardInfo info;
    try {
      info = newDashboardInfo(id.get());
    } catch (InvalidDashboardId e) {
      throw new ResourceNotFoundException(id);
    }

    for (ProjectState ps : parent.getProjectState().tree()) {
      try {
        return parse(
            projectAccessorFactory.create(ps), parent.getProjectAccessor(), parent.getUser(), info);
      } catch (AmbiguousObjectException | ConfigInvalidException | IncorrectObjectTypeException e) {
        throw new ResourceNotFoundException(id);
      } catch (ResourceNotFoundException e) {
        continue;
      }
    }
    throw new ResourceNotFoundException(id);
  }

  public static String normalizeDashboardRef(String ref) {
    if (!ref.startsWith(REFS_DASHBOARDS)) {
      return REFS_DASHBOARDS + ref;
    }
    return ref;
  }

  private DashboardResource parse(
      ProjectAccessor parent, ProjectAccessor current, CurrentUser user, DashboardInfo info)
      throws ResourceNotFoundException, IOException, AmbiguousObjectException,
          IncorrectObjectTypeException, ConfigInvalidException, PermissionBackendException,
          ResourceConflictException {
    String ref = normalizeDashboardRef(info.ref);
    try {
      permissionBackend
          .user(user)
          .project(parent.getProjectState().getNameKey())
          .ref(ref)
          .check(RefPermission.READ);
    } catch (AuthException e) {
      // Don't leak the project's existence
      throw new ResourceNotFoundException(info.id);
    }
    if (!Repository.isValidRefName(ref)) {
      throw new ResourceNotFoundException(info.id);
    }

    parent.getProjectState().checkStatePermitsRead();

    try (Repository git = gitManager.openRepository(parent.getProjectState().getNameKey())) {
      ObjectId objId = git.resolve(ref + ":" + info.path);
      if (objId == null) {
        throw new ResourceNotFoundException(info.id);
      }
      BlobBasedConfig cfg = new BlobBasedConfig(null, git, objId);
      return new DashboardResource(current, user, ref, info.path, cfg, false);
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(info.id);
    }
  }

  @Override
  public DynamicMap<RestView<DashboardResource>> views() {
    return views;
  }

  public static DashboardInfo newDashboardInfo(String ref, String path) {
    DashboardInfo info = new DashboardInfo();
    info.ref = ref;
    info.path = path;
    info.id = Joiner.on(':').join(Url.encode(ref), Url.encode(path));
    return info;
  }

  public static class InvalidDashboardId extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidDashboardId(String id) {
      super(id);
    }
  }

  static DashboardInfo newDashboardInfo(String id) throws InvalidDashboardId {
    DashboardInfo info = new DashboardInfo();
    List<String> parts = Lists.newArrayList(Splitter.on(':').limit(2).split(id));
    if (parts.size() != 2) {
      throw new InvalidDashboardId(id);
    }
    info.id = id;
    info.ref = parts.get(0);
    info.path = parts.get(1);
    return info;
  }

  static DashboardInfo parse(
      Project definingProject,
      String refName,
      String path,
      Config config,
      String project,
      boolean setDefault) {
    DashboardInfo info = newDashboardInfo(refName, path);
    info.project = project;
    info.definingProject = definingProject.getName();
    String title = config.getString("dashboard", null, "title");
    info.title = replace(project, title == null ? info.path : title);
    info.description = replace(project, config.getString("dashboard", null, "description"));
    info.foreach = config.getString("dashboard", null, "foreach");

    if (setDefault) {
      String id = refName + ":" + path;
      info.isDefault = id.equals(defaultOf(definingProject)) ? true : null;
    }

    UrlEncoded u = new UrlEncoded("/dashboard/");
    u.put("title", MoreObjects.firstNonNull(info.title, info.path));
    if (info.foreach != null) {
      u.put("foreach", replace(project, info.foreach));
    }
    for (String name : config.getSubsections("section")) {
      DashboardSectionInfo s = new DashboardSectionInfo();
      s.name = name;
      s.query = config.getString("section", name, "query");
      u.put(s.name, replace(project, s.query));
      info.sections.add(s);
    }
    info.url = u.toString().replace("%3A", ":");

    return info;
  }

  private static String replace(String project, String input) {
    return input == null ? input : input.replace("${project}", project);
  }

  private static String defaultOf(Project proj) {
    final String defaultId =
        MoreObjects.firstNonNull(
            proj.getLocalDefaultDashboard(), Strings.nullToEmpty(proj.getDefaultDashboard()));
    if (defaultId.startsWith(REFS_DASHBOARDS)) {
      return defaultId.substring(REFS_DASHBOARDS.length());
    }
    return defaultId;
  }
}
