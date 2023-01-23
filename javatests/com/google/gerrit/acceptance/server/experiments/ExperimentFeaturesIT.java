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

package com.google.gerrit.acceptance.server.experiments;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.inject.Inject;
import org.junit.Test;

/** Tests for {@link ExperimentFeatures} */
public class ExperimentFeaturesIT extends AbstractDaemonTest {

  @Inject ExperimentFeatures experimentFeatures;

  @Test
  public void emptyConfig_defaultFeatures_enabled() {
    for (String defaultFeature : ExperimentFeaturesConstants.DEFAULT_ENABLED_FEATURES) {
      assertThat(experimentFeatures.isFeatureEnabled(defaultFeature)).isTrue();
    }

    assertThat(experimentFeatures.getEnabledExperimentFeatures())
        .isEqualTo(ExperimentFeaturesConstants.DEFAULT_ENABLED_FEATURES);
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"enabledFeature", "enabledThenDisabledFeature"})
  @GerritConfig(
      name = "experiments.disabled",
      values = {"enabledThenDisabledFeature"})
  public void configOverride_anyFeatureAllowed() {
    assertThat(experimentFeatures.isFeatureEnabled("enabledFeature")).isTrue();
    assertThat(experimentFeatures.isFeatureEnabled("enabledThenDisabledFeature")).isFalse();
    assertThat(experimentFeatures.isFeatureEnabled("unknownFeature")).isFalse();
    ImmutableSet<String> expectedEnabledFeatures =
        new ImmutableSet.Builder<String>()
            .addAll(ExperimentFeaturesConstants.DEFAULT_ENABLED_FEATURES)
            .add("enabledFeature")
            .build();
    assertThat(experimentFeatures.getEnabledExperimentFeatures())
        .isEqualTo(expectedEnabledFeatures);
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"enabledFeature"})
  @GerritConfig(
      name = "experiments.disabled",
      values = {"UiFeature__patchset_comments", "UiFeature__submit_requirements_ui"})
  public void configOverride_defaultFeatureDisabled() {
    assertThat(experimentFeatures.isFeatureEnabled("enabledFeature")).isTrue();
    assertThat(experimentFeatures.isFeatureEnabled("UiFeature__patchset_comments"))
        .isFalse();
    assertThat(experimentFeatures.getEnabledExperimentFeatures()).containsExactly("enabledFeature");
  }
}
