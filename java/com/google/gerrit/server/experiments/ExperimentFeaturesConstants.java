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

  /** Whether we allow fix suggestions in HumanComments. */
  public static final String ALLOW_FIX_SUGGESTIONS_IN_COMMENTS =
      "GerritBackendFeature__allow_fix_suggestions_in_comments";

  /** Whether to enforce a timeout during file diff computation. */
  public static final String TIMEOUT_FILE_DIFF_COMPUTATION =
      "GerritBackendFeature__timeout_file_diff_computation";

  /** Whether submit_records should only be returned along with submit_requirements. */
  public static final String SKIP_SUBMIT_RECORDS_WITHOUT_SUBMIT_REQUIREMENTS =
      "GerritBackendFeature__skip_submit_records_without_submit_requirements";

  /** Whether to consider votes of deleted accounts. */
  public static final String CONSIDER_VOTES_OF_DELETED_ACCOUNTS =
      "GerritBackendFeature__consider_votes_of_deleted_accounts";

  /** Whether we restrict the creation of branch permissions. */
  public static final String GERRIT_BACKEND_FEATURE_RESTRICT_BRANCH_PERMISSIONS =
      "GerritBackendFeature__restrict_branch_permissions";
}
