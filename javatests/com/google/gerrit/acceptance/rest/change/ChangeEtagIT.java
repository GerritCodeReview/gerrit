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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.net.HttpHeaders;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.inject.Inject;
import org.junit.Test;

public class ChangeEtagIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value = ExperimentFeaturesConstants.DISABLE_CHANGE_ETAGS)
  public void changeEtagsDisabled() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    RestResponse response = adminRestSession.get(String.format("/changes/%s", changeId));
    assertThat(response.getHeader(HttpHeaders.ETAG)).isNull();

    response = adminRestSession.get(String.format("/changes/%s/detail", changeId));
    assertThat(response.getHeader(HttpHeaders.ETAG)).isNull();
  }

  @Test
  public void changeEtagsEnabled() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    RestResponse response = adminRestSession.get(String.format("/changes/%s", changeId));
    assertThat(response.getHeader(HttpHeaders.ETAG)).isNotNull();

    response = adminRestSession.get(String.format("/changes/%s/detail", changeId));
    assertThat(response.getHeader(HttpHeaders.ETAG)).isNotNull();
  }
}
