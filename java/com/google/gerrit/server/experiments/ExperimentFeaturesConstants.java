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

  public static String UI_FEATURE_SUBMIT_REQUIREMENTS_UI = "UiFeature__submit_requirements_ui";

  public static String GERRIT_BACKEND_REQUEST_FEATURE_REMOVE_REVISION_ETAG =
      "GerritBackendRequestFeature__remove_revision_etag";

  public static String GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION =
      "GerritBackendFeature__attach_nonce_to_documentation";

  /** Features, enabled by default in the current release. */
  public static final ImmutableSet<String> DEFAULT_ENABLED_FEATURES =
      ImmutableSet.of(UI_FEATURE_PATCHSET_COMMENTS, UI_FEATURE_SUBMIT_REQUIREMENTS_UI);
}
