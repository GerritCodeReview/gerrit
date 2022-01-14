// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.api.config.DashboardPreferencesInfo;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;

public class GetDashboardPreferencesIT extends AbstractDaemonTest {

  @Test
  public void noSubmitRequirementColumns() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/dashboard");
    r.assertOK();
    DashboardPreferencesInfo result =
        newGson().fromJson(r.getReader(), new TypeToken<DashboardPreferencesInfo>() {}.getType());

    assertThat(result.submitRequirementsColumns).isEmpty();
  }

  @Test
  @GerritConfig(name = "dashboard.submitRequirementsColumns", value = "Code-Review")
  public void oneSubmitRequirementColumn() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/dashboard");
    r.assertOK();
    DashboardPreferencesInfo result =
        newGson().fromJson(r.getReader(), new TypeToken<DashboardPreferencesInfo>() {}.getType());

    assertThat(result.submitRequirementsColumns).containsExactly("Code-Review");
  }

  @Test
  @GerritConfig(
      name = "dashboard.submitRequirementsColumns",
      values = {"Code-Review", "Verified"})
  public void multipleSubmitRequirementColumn() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/dashboard");
    r.assertOK();
    DashboardPreferencesInfo result =
        newGson().fromJson(r.getReader(), new TypeToken<DashboardPreferencesInfo>() {}.getType());

    assertThat(result.submitRequirementsColumns).containsExactly("Code-Review", "Verified");
  }
}
