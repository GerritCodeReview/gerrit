// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import com.google.gerrit.extensions.api.config.DashboardPreferencesInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.Config;

public class GetDashboardPreferences implements RestReadView<ConfigResource> {
  private final List<String> submitRequirementsColumns;

  @Inject
  GetDashboardPreferences(@GerritServerConfig Config config) {
    submitRequirementsColumns =
        Arrays.asList(config.getStringList("dashboard", null, "submitRequirementsColumns"));
  }

  @Override
  public Response<DashboardPreferencesInfo> apply(ConfigResource resource)
      throws ResourceNotFoundException {
    DashboardPreferencesInfo info = new DashboardPreferencesInfo();
    info.submitRequirementsColumns = submitRequirementsColumns;
    return Response.ok(info);
  }
}
