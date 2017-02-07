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
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.project.DashboardsCollection.DashboardInfo;
import com.google.gerrit.server.project.SetDashboard.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
class SetDashboard implements RestModifyView<DashboardResource, Input> {
  static class Input {
    @DefaultInput String id;
    String commitMessage;
  }

  private final Provider<SetDefaultDashboard> defaultSetter;

  @Inject
  SetDashboard(Provider<SetDefaultDashboard> defaultSetter) {
    this.defaultSetter = defaultSetter;
  }

  @Override
  public Response<DashboardInfo> apply(DashboardResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
          MethodNotAllowedException, ResourceNotFoundException, IOException {
    if (resource.isProjectDefault()) {
      return defaultSetter.get().apply(resource, input);
    }

    // TODO: Implement creation/update of dashboards by API.
    throw new MethodNotAllowedException();
  }
}
