package com.google.gerrit.server.config;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/**
 * Features that can be enabled/disabled on Gerrit (e. g. experiments to research new behavior in
 * the current release).
 */
@Singleton
public class ExperimentFeatures {

  /** Features that are known experiments and can be referenced in the code. */
  public static String UI_FEATURE_PATCHSET_COMMENTS = "UiFeature__patchset_comments";

  public static String UI_FEATURE_PATCHSET_CHOICE_FOR_COMMENT_LINKS =
      "UiFeature__patchset_choice_for_comment_links";

  public static final ImmutableSet<String> DEFAULT_ENABLED_FEATURES =
      ImmutableSet.of(UI_FEATURE_PATCHSET_COMMENTS, UI_FEATURE_PATCHSET_CHOICE_FOR_COMMENT_LINKS);

  Config gerritServerConfig;

  @Inject
  public ExperimentFeatures(@GerritServerConfig Config gerritConfig) {
    this.gerritServerConfig = gerritConfig;
  }

  public boolean isFeatureEnabled(String featureFlag) {
    return getEnabledExperimentFeatures().contains(featureFlag);
  }

  /**
   * Returns the names of the features that are enabled on Gerrit instance (either by default or via
   * gerrit.config).
   */
  public ImmutableSet<String> getEnabledExperimentFeatures() {
    Set<String> enabledExperiments = new HashSet<>();
    Arrays.stream(gerritServerConfig.getStringList("experiments", null, "enabled"))
        .forEach(enabledExperiments::add);
    DEFAULT_ENABLED_FEATURES.forEach(enabledExperiments::add);
    Arrays.stream(gerritServerConfig.getStringList("experiments", null, "disabled"))
        .forEach(enabledExperiments::remove);
    return ImmutableSet.copyOf(enabledExperiments);
  }
}
