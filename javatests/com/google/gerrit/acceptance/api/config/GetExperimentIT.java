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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.common.ExperimentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.inject.Inject;
import org.junit.Test;

public class GetExperimentIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void cannotGetAsNonAdmin() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    AuthException exception =
        assertThrows(
            AuthException.class,
            () ->
                gApi.config()
                    .server()
                    .experiment(ExperimentFeaturesConstants.ALLOW_FIX_SUGGESTIONS_IN_COMMENTS)
                    .get());
    assertThat(exception).hasMessageThat().isEqualTo("administrate server not permitted");
  }

  @Test
  public void cannotGetNonExistingExperiment() throws Exception {
    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.config().server().experiment("non-existing").get());
    assertThat(exception).hasMessageThat().isEqualTo("non-existing");
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {"GerritBackendFeature__attach_nonce_to_documentation"})
  public void getEnabled() throws Exception {
    ExperimentInfo experimentInfo =
        gApi.config()
            .server()
            .experiment(
                ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION)
            .get();
    assertThat(experimentInfo.enabled).isTrue();
  }

  @Test
  public void getDisabled() throws Exception {
    ExperimentInfo experimentInfo =
        gApi.config()
            .server()
            .experiment(
                ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION)
            .get();
    assertThat(experimentInfo.enabled).isFalse();
  }
}
