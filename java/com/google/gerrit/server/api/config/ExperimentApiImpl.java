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

package com.google.gerrit.server.api.config;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.config.ExperimentApi;
import com.google.gerrit.extensions.common.ExperimentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.ExperimentResource;
import com.google.gerrit.server.restapi.config.GetExperiment;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class ExperimentApiImpl implements ExperimentApi {
  interface Factory {
    ExperimentApiImpl create(ExperimentResource r);
  }

  private final ExperimentResource experiment;
  private final GetExperiment getExperiment;

  @Inject
  ExperimentApiImpl(GetExperiment getExperiment, @Assisted ExperimentResource r) {
    this.getExperiment = getExperiment;
    this.experiment = r;
  }

  @Override
  public ExperimentInfo get() throws RestApiException {
    try {
      return getExperiment.apply(experiment).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get experiment", e);
    }
  }
}
