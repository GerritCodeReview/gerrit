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

import com.google.gerrit.extensions.api.projects.DashboardApi;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.DashboardResource;
import com.google.gerrit.server.project.DashboardsCollection;
import com.google.gerrit.server.project.GetDashboard;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class DashboardApiImpl implements DashboardApi {
  interface Factory {
    DashboardApiImpl create(ProjectResource project, String name);
  }

  private final DashboardsCollection dashboards;
  private final Provider<GetDashboard> getDashboard;
  private final ProjectResource project;
  private final String name;

  @Inject
  DashboardApiImpl(
      DashboardsCollection dashboards,
      Provider<GetDashboard> getDashboard,
      @Assisted ProjectResource project,
      @Assisted String name) {
    this.dashboards = dashboards;
    this.getDashboard = getDashboard;
    this.project = project;
    this.name = name;
  }

  @Override
  public DashboardInfo get() throws RestApiException {
    return get(false);
  }

  @Override
  public DashboardInfo get(boolean inherited) throws RestApiException {
    try {
      return getDashboard.get().setInherited(inherited).apply(resource());
    } catch (IOException | PermissionBackendException | ConfigInvalidException e) {
      throw asRestApiException("Cannot read dashboard", e);
    }
  }

  private DashboardResource resource()
      throws ResourceNotFoundException, IOException, ConfigInvalidException,
          PermissionBackendException {
    return dashboards.parse(project, IdString.fromDecoded(name));
  }
}
