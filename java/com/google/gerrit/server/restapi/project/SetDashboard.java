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

import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.api.projects.SetDashboardInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.DashboardResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class SetDashboard implements RestModifyView<DashboardResource, SetDashboardInput> {
  private final Provider<SetDefaultDashboard> defaultSetter;

  @Inject
  SetDashboard(Provider<SetDefaultDashboard> defaultSetter) {
    this.defaultSetter = defaultSetter;
  }

  @Override
  public Response<DashboardInfo> apply(DashboardResource resource, SetDashboardInput input)
      throws RestApiException, IOException, PermissionBackendException {
    if (resource.isProjectDefault()) {
      return defaultSetter.get().apply(resource, input);
    }

    // TODO: Implement creation/update of dashboards by API.
    throw new MethodNotAllowedException();
  }
}
