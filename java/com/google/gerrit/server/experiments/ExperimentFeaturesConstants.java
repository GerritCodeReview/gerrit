package com.google.gerrit.server.experiments;

import com.google.common.collect.ImmutableSet;

/** Constants for Gerrit {@link ExperimentFeatures} */
public class ExperimentFeaturesConstants {

  /** Features that are known experiments and can be referenced in the code. */
  public static String UI_FEATURE_PATCHSET_COMMENTS = "UiFeature__patchset_comments";

  public static String UI_FEATURE_PATCHSET_CHOICE_FOR_COMMENT_LINKS =
      "UiFeature__patchset_choice_for_comment_links";

  /** Features, enabled by default in the current release. */
  public static final ImmutableSet<String> DEFAULT_ENABLED_FEATURES =
      ImmutableSet.of(UI_FEATURE_PATCHSET_COMMENTS, UI_FEATURE_PATCHSET_CHOICE_FOR_COMMENT_LINKS);
}
