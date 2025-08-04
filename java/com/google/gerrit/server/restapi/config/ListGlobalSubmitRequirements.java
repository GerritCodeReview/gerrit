// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.SubmitRequirementJson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class ListGlobalSubmitRequirements implements RestReadView<ConfigResource> {
  private final Provider<CurrentUser> user;
  private final PluginSetContext<SubmitRequirement> globalSubmitRequirement;

  @Inject
  ListGlobalSubmitRequirements(
      Provider<CurrentUser> user, PluginSetContext<SubmitRequirement> globalSubmitRequirement) {
    this.user = user;
    this.globalSubmitRequirement = globalSubmitRequirement;
  }

  @Override
  public Response<ImmutableList<SubmitRequirementInfo>> apply(ConfigResource resource)
      throws AuthException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    return Response.ok(
        globalSubmitRequirement.stream()
            .sorted(comparing(SubmitRequirement::name))
            .map(
                globalSubmitRequirement ->
                    SubmitRequirementJson.format(/* projectName= */ null, globalSubmitRequirement))
            .collect(toImmutableList()));
  }
}
