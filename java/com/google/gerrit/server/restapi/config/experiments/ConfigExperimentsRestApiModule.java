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

package com.google.gerrit.server.restapi.config.experiments;

import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;
import static com.google.gerrit.server.config.ExperimentResource.EXPERIMENT_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/** Guice module that binds all REST endpoints for {@code /config/server/experiments}. */
public class ConfigExperimentsRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), EXPERIMENT_KIND);

    /** List experiments {@code GET /config/server/experiments}. */
    child(CONFIG_KIND, "experiments").to(ExperimentsCollection.class);

    /** Get experiment {@code GET /config/server/experiments/<experiment-id>}. */
    get(EXPERIMENT_KIND).to(GetExperiment.class);
  }
}
