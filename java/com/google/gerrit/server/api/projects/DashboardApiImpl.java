// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.projects.DashboardApi;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.common.SetDashboardInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.DashboardResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.DashboardsCollection;
import com.google.gerrit.server.restapi.project.GetDashboard;
import com.google.gerrit.server.restapi.project.SetDashboard;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class DashboardApiImpl implements DashboardApi {
  interface Factory {
    DashboardApiImpl create(ProjectResource project, String id);
  }

  private final DashboardsCollection dashboards;
  private final Provider<GetDashboard> get;
  private final SetDashboard set;
  private final ProjectResource project;
  private final String id;

  @Inject
  DashboardApiImpl(
      DashboardsCollection dashboards,
      Provider<GetDashboard> get,
      SetDashboard set,
      @Assisted ProjectResource project,
      @Assisted @Nullable String id) {
    this.dashboards = dashboards;
    this.get = get;
    this.set = set;
    this.project = project;
    this.id = id;
  }

  @Override
  public DashboardInfo get() throws RestApiException {
    return get(false);
  }

  @Override
  public DashboardInfo get(boolean inherited) throws RestApiException {
    try {
      return get.get().setInherited(inherited).apply(resource());
    } catch (IOException | PermissionBackendException | ConfigInvalidException e) {
      throw asRestApiException("Cannot read dashboard", e);
    }
  }

  @Override
  public void setDefault() throws RestApiException {
    SetDashboardInput input = new SetDashboardInput();
    input.id = id;
    try {
      set.apply(
          DashboardResource.projectDefault(project.getProjectAccessor(), project.getUser()), input);
    } catch (Exception e) {
      String msg = String.format("Cannot %s default dashboard", id != null ? "set" : "remove");
      throw asRestApiException(msg, e);
    }
  }

  private DashboardResource resource()
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    return dashboards.parse(project, IdString.fromDecoded(id));
  }
}
