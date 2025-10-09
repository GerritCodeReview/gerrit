// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.common.ExperimentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class ListExperimentsIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void cannotListAsNonAdmin() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    AuthException exception =
        assertThrows(AuthException.class, () -> gApi.config().server().listExperiments().get());
    assertThat(exception).hasMessageThat().isEqualTo("administrate server not permitted");
  }

  @Test
  public void listAll() throws Exception {
    ImmutableMap<String, ExperimentInfo> experiments =
        gApi.config().server().listExperiments().get();
    assertThat(experiments.keySet())
        .containsExactly(
            ExperimentFeaturesConstants.ALLOW_FIX_SUGGESTIONS_IN_COMMENTS,
            ExperimentFeaturesConstants
                .GERRIT_BACKEND_FEATURE_ALWAYS_REJECT_IMPLICIT_MERGES_ON_MERGE,
            ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION,
            ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_CHECK_IMPLICIT_MERGES_ON_MERGE,
            ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_REJECT_IMPLICIT_MERGES_ON_MERGE,
            ExperimentFeaturesConstants.ASYNC_SUBMIT_REQUIREMENTS,
            ExperimentFeaturesConstants.PARALLEL_DASHBOARD_REQUESTS)
        .inOrder();

    // "GerritBackendFeature__check_implicit_merges_on_merge",
    // "GerritBackendFeature__reject_implicit_merges_on_merge" and
    // "GerritBackendFeature__always_reject_implicit_merges_on_merge" are enabled via
    // AbstractDaemonTest#beforeTest
    assertThat(
            experiments.get(
                    ExperimentFeaturesConstants
                        .GERRIT_BACKEND_FEATURE_ALWAYS_REJECT_IMPLICIT_MERGES_ON_MERGE)
                .enabled)
        .isTrue();
    assertThat(
            experiments.get(
                    ExperimentFeaturesConstants
                        .GERRIT_BACKEND_FEATURE_CHECK_IMPLICIT_MERGES_ON_MERGE)
                .enabled)
        .isTrue();
    assertThat(
            experiments.get(
                    ExperimentFeaturesConstants
                        .GERRIT_BACKEND_FEATURE_REJECT_IMPLICIT_MERGES_ON_MERGE)
                .enabled)
        .isTrue();

    assertThat(
            experiments.get(ExperimentFeaturesConstants.ALLOW_FIX_SUGGESTIONS_IN_COMMENTS).enabled)
        .isFalse();
    assertThat(
            experiments.get(
                    ExperimentFeaturesConstants
                        .GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION)
                .enabled)
        .isFalse();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"GerritBackendFeature__attach_nonce_to_documentation"})
  // "GerritBackendFeature__check_implicit_merges_on_merge",
  // "GerritBackendFeature__reject_implicit_merges_on_merge" and
  // "GerritBackendFeature__always_reject_implicit_merges_on_merge" are enabled via
  // AbstractDaemonTest#beforeTest
  public void listEnabled_noneEnabled() throws Exception {
    ImmutableMap<String, ExperimentInfo> experiments =
        gApi.config().server().listExperiments().enabledOnly().get();
    assertThat(experiments.keySet())
        .containsExactly(
            ExperimentFeaturesConstants
                .GERRIT_BACKEND_FEATURE_ALWAYS_REJECT_IMPLICIT_MERGES_ON_MERGE,
            ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION,
            ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_CHECK_IMPLICIT_MERGES_ON_MERGE,
            ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_REJECT_IMPLICIT_MERGES_ON_MERGE)
        .inOrder();
    for (ExperimentInfo experimentInfo : experiments.values()) {
      assertThat(experimentInfo.enabled).isTrue();
    }
  }
}
