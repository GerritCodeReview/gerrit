// Copyright (C) 2015 The Android Open Source Project
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
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GerritConfigs;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.config.GetServerInfo.ServerInfo;

import org.junit.Test;

public class ServerInfoIT extends AbstractDaemonTest {

  @Test
  @GerritConfigs({
    // Auth
    @GerritConfig(name = "auth.type", value = "HTTP"),
    @GerritConfig(name = "auth.contributorAgreements", value = "true"),
    @GerritConfig(name = "auth.loginUrl", value = "https://example.com/login"),
    @GerritConfig(name = "auth.loginText", value = "All is fine, folks!"),
    // Download
    @GerritConfig(name = "download.archive", values = {"tar",
        "tbz2", "tgz", "txz"}),
  })
  public void serverConfig() throws Exception {
    RestResponse r = adminSession.get("/config/server/info/");
    ServerInfo i = newGson().fromJson(r.getReader(), ServerInfo.class);

    // Auth section
    assertThat(i.auth.authType).isEqualTo(AuthType.HTTP);
    assertThat(i.auth.useContributorAgreements).isTrue();
    assertThat(i.auth.loginUrl).isEqualTo("https://example.com/login");
    assertThat(i.auth.loginText).isEqualTo("All is fine, folks!");
    assertThat(i.auth.switchAccountUrl).isNull();
    assertThat(i.auth.registerUrl).isNull();
    assertThat(i.auth.registerText).isNull();
    assertThat(i.auth.editFullNameUrl).isNull();
    assertThat(i.auth.httpPasswordUrl).isNull();
    assertThat(i.auth.isGitBasicAuth).isNull();

    // Download section
    assertThat(i.download.archives).hasSize(4);

    // To be continued ...
  }
}
