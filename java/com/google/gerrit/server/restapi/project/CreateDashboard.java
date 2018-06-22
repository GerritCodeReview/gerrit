// Copyright (C) 2018 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCreateView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.DashboardResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.kohsuke.args4j.Option;

@Singleton
public class CreateDashboard
    implements RestCreateView<ProjectResource, DashboardResource, SetDashboardInput> {
  private final Provider<SetDefaultDashboard> setDefault;

  @Option(name = "--inherited", usage = "set dashboard inherited by children")
  private boolean inherited;

  @Inject
  CreateDashboard(Provider<SetDefaultDashboard> setDefault) {
    this.setDefault = setDefault;
  }

  @Override
  public Response<DashboardInfo> apply(ProjectResource parent, IdString id, SetDashboardInput input)
      throws RestApiException, IOException, PermissionBackendException {
    parent.getProjectState().checkStatePermitsWrite();
    if (!DashboardsCollection.isDefaultDashboard(id)) {
      throw new ResourceNotFoundException(id);
    }
    SetDefaultDashboard set = setDefault.get();
    set.inherited = inherited;
    return set.apply(
        DashboardResource.projectDefault(parent.getProjectState(), parent.getUser()), input);
  }
}
