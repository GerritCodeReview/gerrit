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

  /** Enable storing submit requirements in NoteDb when the change is merged. */
  public static final String GERRIT_BACKEND_REQUEST_FEATURE_STORE_SUBMIT_REQUIREMENTS_ON_MERGE =
      "GerritBackendRequestFeature__store_submit_requirements_on_merge";

  /**
   * Allow legacy {@link com.google.gerrit.entities.SubmitRecord}s to be converted and returned as
   * submit requirements by the {@link
   * com.google.gerrit.server.project.SubmitRequirementsEvaluator}.
   */
  public static final String GERRIT_BACKEND_REQUEST_FEATURE_ENABLE_SUBMIT_REQUIREMENTS =
      "GerritBackendRequestFeature__enable_submit_requirements";

  /**
   * Allow SubmitRequirements to be computed freshly on dashboards irrespective of the value we
   * retrieved from the change index.
   */
  public static final String
      GERRIT_BACKEND_REQUEST_FEATURE_ENABLE_SUBMIT_REQUIREMENTS_BACKFILLING_ON_DASHBOARD =
          "GerritBackendRequestFeature__enable_submit_requirements_backfilling_on_dashboard";

  /** Features, enabled by default in the current release. */
  public static final ImmutableSet<String> DEFAULT_ENABLED_FEATURES =
      ImmutableSet.of(UI_FEATURE_PATCHSET_COMMENTS);
}
