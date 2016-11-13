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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.project.DashboardsCollection.DashboardInfo;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
class DeleteDashboard implements RestModifyView<DashboardResource, SetDashboard.Input> {
  private final Provider<SetDefaultDashboard> defaultSetter;

  @Inject
  DeleteDashboard(Provider<SetDefaultDashboard> defaultSetter) {
    this.defaultSetter = defaultSetter;
  }

  @Override
  public Response<DashboardInfo> apply(DashboardResource resource, SetDashboard.Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
          ResourceNotFoundException, MethodNotAllowedException, IOException {
    if (resource.isProjectDefault()) {
      SetDashboard.Input in = new SetDashboard.Input();
      in.commitMessage = input != null ? input.commitMessage : null;
      return defaultSetter.get().apply(resource, in);
    }

    // TODO: Implement delete of dashboards by API.
    throw new MethodNotAllowedException();
  }
}
