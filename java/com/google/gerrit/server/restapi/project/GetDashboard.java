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
import static com.google.gerrit.server.restapi.project.DashboardsCollection.isDefaultDashboard;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.DashboardResource;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

public class GetDashboard implements RestReadView<DashboardResource> {
  private final DashboardsCollection dashboards;
  private final ProjectAccessor.Factory projectAccessorFactory;

  @Option(name = "--inherited", usage = "include inherited dashboards")
  private boolean inherited;

  @Inject
  GetDashboard(DashboardsCollection dashboards, ProjectAccessor.Factory projectAccessorFactory) {
    this.dashboards = dashboards;
    this.projectAccessorFactory = projectAccessorFactory;
  }

  public GetDashboard setInherited(boolean inherited) {
    this.inherited = inherited;
    return this;
  }

  @Override
  public DashboardInfo apply(DashboardResource rsrc)
      throws RestApiException, IOException, PermissionBackendException {
    if (inherited && !rsrc.isProjectDefault()) {
      throw new BadRequestException("inherited flag can only be used with default");
    }

    if (rsrc.isProjectDefault()) {
      // The default is not resolved to a definition yet.
      try {
        rsrc = defaultOf(rsrc.getProjectState(), rsrc.getUser());
      } catch (ConfigInvalidException e) {
        throw new ResourceConflictException(e.getMessage());
      }
    }

    return DashboardsCollection.parse(
        rsrc.getProjectState().getProject(),
        rsrc.getRefName().substring(REFS_DASHBOARDS.length()),
        rsrc.getPathName(),
        rsrc.getConfig(),
        rsrc.getProjectState().getName(),
        true);
  }

  private DashboardResource defaultOf(ProjectState projectState, CurrentUser user)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    String id = projectState.getProject().getLocalDefaultDashboard();
    if (Strings.isNullOrEmpty(id)) {
      id = projectState.getProject().getDefaultDashboard();
    }
    if (isDefaultDashboard(id)) {
      throw new ResourceNotFoundException();
    } else if (!Strings.isNullOrEmpty(id)) {
      return parse(projectState, user, id);
    } else if (!inherited) {
      throw new ResourceNotFoundException();
    }

    for (ProjectState ps : projectState.tree()) {
      id = ps.getProject().getDefaultDashboard();
      if (isDefaultDashboard(id)) {
        throw new ResourceNotFoundException();
      } else if (!Strings.isNullOrEmpty(id)) {
        return parse(projectState, user, id);
      }
    }
    throw new ResourceNotFoundException();
  }

  private DashboardResource parse(ProjectState projectState, CurrentUser user, String id)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    List<String> p = Lists.newArrayList(Splitter.on(':').limit(2).split(id));
    String ref = Url.encode(p.get(0));
    String path = Url.encode(p.get(1));
    return dashboards.parse(
        new ProjectResource(projectAccessorFactory.create(projectState), user),
        IdString.fromUrl(ref + ':' + path));
  }
}
