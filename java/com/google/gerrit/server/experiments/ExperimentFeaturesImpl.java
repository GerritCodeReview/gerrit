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
public class ExperimentFeaturesImpl implements ExperimentFeatures {

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(ExperimentFeatures.class).to(ExperimentFeaturesImpl.class);
    }
  }

  private ImmutableSet<String> enabledExperimentFeatures;

  @Inject
  public ExperimentFeaturesImpl(@GerritServerConfig Config gerritServerConfig) {
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
