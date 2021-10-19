// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.experiments;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/**
 * An implementation of {@link ExperimentFeatures} that uses gerrit.config to evaluate the status of
 * the feature.
 */
@Singleton
public class ConfigExperimentFeatures implements ExperimentFeatures {

  public static class ConfigExperimentFeaturesModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(ExperimentFeatures.class).to(ConfigExperimentFeatures.class);
    }
  }

  private ImmutableSet<String> enabledExperimentFeatures;

  @Inject
  public ConfigExperimentFeatures(@GerritServerConfig Config gerritServerConfig) {
    Set<String> enabledExperiments = new HashSet<>();
    Arrays.stream(gerritServerConfig.getStringList("experiments", null, "enabled"))
        .forEach(enabledExperiments::add);
    ExperimentFeaturesConstants.DEFAULT_ENABLED_FEATURES.forEach(enabledExperiments::add);
    Arrays.stream(gerritServerConfig.getStringList("experiments", null, "disabled"))
        .forEach(enabledExperiments::remove);
    enabledExperimentFeatures = ImmutableSet.copyOf(enabledExperiments);
  }

  @Override
  public boolean isFeatureEnabled(String featureFlag) {
    return getEnabledExperimentFeatures().contains(featureFlag);
  }

  @Override
  public ImmutableSet<String> getEnabledExperimentFeatures() {
    return enabledExperimentFeatures;
  }
}
