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
      values = {"UiFeature__patchset_comments"})
  public void configOverride_defaultFeatureDisabled() {
    assertThat(experimentFeatures.isFeatureEnabled("enabledFeature")).isTrue();
    assertThat(
            experimentFeatures.isFeatureEnabled(
                ExperimentFeaturesConstants.UI_FEATURE_PATCHSET_CHOICE_FOR_COMMENT_LINKS))
        .isTrue();

    assertThat(
            experimentFeatures.isFeatureEnabled(
                ExperimentFeaturesConstants.UI_FEATURE_PATCHSET_COMMENTS))
        .isFalse();
    assertThat(experimentFeatures.getEnabledExperimentFeatures())
        .containsExactly(
            "enabledFeature",
            ExperimentFeaturesConstants.UI_FEATURE_PATCHSET_CHOICE_FOR_COMMENT_LINKS);
  }
}
