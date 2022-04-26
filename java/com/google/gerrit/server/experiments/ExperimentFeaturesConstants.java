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
  public static String UI_FEATURE_PATCHSET_COMMENTS = "UiFeature__patchset_comments";

  public static String GERRIT_BACKEND_REQUEST_FEATURE_REMOVE_REVISION_ETAG =
      "GerritBackendRequestFeature__remove_revision_etag";

  /**
   * When set, we compute information from All-Users repository if able, instead of computing it
   * from the change index.
   */
  public static final String GERRIT_BACKEND_REQUEST_FEATURE_COMPUTE_FROM_ALL_USERS_REPOSITORY =
      "GerritBackendRequestFeature__compute_from_all_users_repository";

  /**
   * When set, the result status of submit requirement expressions might hold the value
   * "NOT_EVALUATED". For example if the change is not applicable, the submit / override expression
   * results will be set to this value. If not set, the submit / override expressions will be set to
   * empty optionals.
   */
  public static final String GERRIT_BACKEND_REQUEST_FEATURE_SR_EXPRESSIONS_NOT_EVALUATED =
      "GerritBackendRequestFeature__sr_expressions_not_evaluated";

  /** Features, enabled by default in the current release. */
  public static final ImmutableSet<String> DEFAULT_ENABLED_FEATURES =
      ImmutableSet.of(UI_FEATURE_PATCHSET_COMMENTS);
}
