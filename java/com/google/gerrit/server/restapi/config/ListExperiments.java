// Copyright (C) 2024 The Android Open Source Project
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
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.common.ExperimentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import org.kohsuke.args4j.Option;

/** List capabilities visible to the calling user. */
public class ListExperiments implements RestReadView<ConfigResource> {
  public static ImmutableList<String> getExperiments() {
    return Arrays.stream(ExperimentFeaturesConstants.class.getDeclaredFields())
        .filter(field -> field.getType().equals(String.class))
        .map(
            field -> {
              try {
                return (String) field.get(null);
              } catch (IllegalAccessException e) {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .sorted()
        .collect(toImmutableList());
  }

  private final PermissionBackend permissionBackend;
  private final ExperimentFeatures experimentFeatures;
  private final GetExperiment getExperiment;

  private boolean enabledOnly;

  @Option(name = "--enabled-only", usage = "only return enabled experiments")
  public void setEnabledOnly(boolean enabledOnly) {
    this.enabledOnly = enabledOnly;
  }

  @Inject
  public ListExperiments(
      PermissionBackend permissionBackend,
      ExperimentFeatures experimentFeatures,
      GetExperiment getExperiment) {
    this.permissionBackend = permissionBackend;
    this.experimentFeatures = experimentFeatures;
    this.getExperiment = getExperiment;
  }

  @Override
  public Response<ImmutableMap<String, ExperimentInfo>> apply(ConfigResource resource)
      throws AuthException, PermissionBackendException {
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    return Response.ok(
        getExperiments().stream()
            .filter(
                experimentName ->
                    !enabledOnly || experimentFeatures.isFeatureEnabled(experimentName))
            .collect(toImmutableMap(Function.identity(), getExperiment::getExperimentInfo)));
  }
}
