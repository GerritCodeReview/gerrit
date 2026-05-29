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

import com.google.gerrit.extensions.common.ExperimentInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ExperimentResource;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetExperiment implements RestReadView<ExperimentResource> {
  private final ExperimentFeatures experimentFeatures;

  @Inject
  public GetExperiment(ExperimentFeatures experimentFeatures) {
    this.experimentFeatures = experimentFeatures;
  }

  @Override
  public Response<ExperimentInfo> apply(ExperimentResource resource) {
    return Response.ok(getExperimentInfo(resource.getName()));
  }

  public ExperimentInfo getExperimentInfo(String experimentName) {
    ExperimentInfo experimentInfo = new ExperimentInfo();
    experimentInfo.enabled = experimentFeatures.isFeatureEnabled(experimentName);
    return experimentInfo;
  }
}
