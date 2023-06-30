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

  /** On BatchUpdate, do not await index completion before returning to the user */
  public static String GERRIT_BACKEND_REQUEST_FEATURE_DO_NOT_AWAIT_CHANGE_INDEXING =
      "GerritBackendRequestFeature__do_not_await_change_indexing";

  /**
   * Sets ChangeInfo.id to "'<project>~<_number>'", instead of "'<project>~<branch>~<Change-Id>'",
   * spearing an index lookup if the id is used in the follow-up API calls.
   */
  public static String GERRIT_BACKEND_FEATURE_RETURN_NEW_CHANGE_INFO_ID =
      "GerritBackendFeature__return_new_change_info_id";

  /**
   * Restrict all account API to accept exact identifiers for accounts, namely self, exact ID or
   * email.
   */
  public static final String GERRIT_BACKEND_FEATURE_RESTRICT_ACCOUNT_API_EXACT =
      "GerritBackendFeature__restrict_account_api_exact";
}
