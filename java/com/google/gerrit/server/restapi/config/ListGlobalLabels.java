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
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.LabelDefinitionJson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class ListGlobalLabels implements RestReadView<ConfigResource> {
  private final Provider<CurrentUser> user;
  private final PluginSetContext<LabelType> globalLabelTypes;

  @Inject
  ListGlobalLabels(Provider<CurrentUser> user, PluginSetContext<LabelType> globalLabelTypes) {
    this.user = user;
    this.globalLabelTypes = globalLabelTypes;
  }

  @Override
  public Response<ImmutableList<LabelDefinitionInfo>> apply(ConfigResource resource)
      throws AuthException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    return Response.ok(
        globalLabelTypes.stream()
            .sorted(comparing(LabelType::getName))
            .map(
                globalLabelType ->
                    LabelDefinitionJson.format(/* projectName= */ null, globalLabelType))
            .collect(toImmutableList()));
  }
}
