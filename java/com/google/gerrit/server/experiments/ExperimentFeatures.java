package com.google.gerrit.server.experiments;

import com.google.common.collect.ImmutableSet;

/**
 * Features that can be enabled/disabled on Gerrit (e. g. experiments to research new behavior in
 * the current release).
 */
public interface ExperimentFeatures {

  /**
   * Given the name of the feature, returns if it is enabled on the Gerrit server.
   *
   * @param featureFlag the name of the feature to test.
   * @return if the feature is enabled.
   */
  boolean isFeatureEnabled(String featureFlag);

  /**
   * Returns the names of the features that are enabled on Gerrit instance (either by default or via
   * gerrit.config).
   */
  ImmutableSet<String> getEnabledExperimentFeatures();
}
