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

/** Constants for Gerrit {@link ExperimentFeatures} */
public class ExperimentFeaturesConstants {

  /** Features that are known experiments and can be referenced in the code. */
  public static String GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION =
      "GerritBackendFeature__attach_nonce_to_documentation";

  /** Features, enabled by default in the current release. */
  public static final ImmutableSet<String> DEFAULT_ENABLED_FEATURES = ImmutableSet.of();

  /**
   * If true, gerrit checks implicit merges on each merge operations.
   *
   * <p>If only this option is set (without {@link
   * #GERRIT_BACKEND_FEATURE_REJECT_IMPLICIT_MERGES_ON_MERGE}) - then the outcome of the check is
   * only logged and doesn't block merge operation. Any exceptions during the check are logged and
   * doesn't block merge operation.
   */
  public static String GERRIT_BACKEND_FEATURE_CHECK_IMPLICIT_MERGES_ON_MERGE =
      "GerritBackendFeature__check_implicit_merges_on_merge";

  /**
   * If true, gerrit rejects implicit merges on merge.
   *
   * <p>Should work together with {@link #GERRIT_BACKEND_FEATURE_CHECK_IMPLICIT_MERGES_ON_MERGE}.
   *
   * <p>If {@link #GERRIT_BACKEND_FEATURE_ALWAYS_REJECT_IMPLICIT_MERGES_ON_MERGE} is set to true
   * then implicit merges are rejected even if rejectImplicitMerges in project config is set to
   * false.
   *
   * <p>If {@link #GERRIT_BACKEND_FEATURE_ALWAYS_REJECT_IMPLICIT_MERGES_ON_MERGE} is set to false
   * then implicit merges are rejected only if rejectImplicitMerges in project config is set to
   * true.
   */
  public static String GERRIT_BACKEND_FEATURE_REJECT_IMPLICIT_MERGES_ON_MERGE =
      "GerritBackendFeature__reject_implicit_merges_on_merge";

  /** If true, gerrit ignores rejectImplicitMerges setting from the project config on merge. */
  public static String GERRIT_BACKEND_FEATURE_ALWAYS_REJECT_IMPLICIT_MERGES_ON_MERGE =
      "GerritBackendFeature__always_reject_implicit_merges_on_merge";

  /** Whether we allow fix suggestions in HumanComments. */
  public static final String ALLOW_FIX_SUGGESTIONS_IN_COMMENTS =
      "GerritBackendFeature__allow_fix_suggestions_in_comments";

  /** Whether submit_records should only be returned along with submit_requirements. */
  public static final String SKIP_SUBMIT_RECORDS_WITHOUT_SUBMIT_REQUIREMENTS =
      "GerritBackendFeature__skip_submit_records_without_submit_requirements";
}
