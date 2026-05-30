// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.config.Config;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.ServerConfigCache;
import com.google.gerrit.server.config.ServerConfigCacheImpl;
import com.google.gerrit.server.experiments.ConfigExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexServletTest {

  @Mock GerritApi gerritApi;

  @Mock Server serverApi;

  @Mock Config configApi;

  @Mock Accounts accountsApi;

  @Inject ServerConfigCache serverConfigCache;

  @Before
  public void setup() throws RestApiException {
    mockGerritApi();
    Injector injector =
        Guice.createInjector(
            new InMemoryModule() {
              @Override
              protected void configure() {
                configure(false);
                bind(GerritApi.class).toInstance(gerritApi);
              }
            });
    injector.injectMembers(this);
  }

  @Test
  public void renderTemplate() throws Exception {
    String testCanonicalUrl = "foo-url";
    String testCdnPath = "bar-cdn";
    String testFaviconURL = "zaz-url";

    assertThat(ExperimentFeaturesConstants.DEFAULT_ENABLED_FEATURES).isEmpty();

    org.eclipse.jgit.lib.Config serverConfig = new org.eclipse.jgit.lib.Config();
    serverConfig.setStringList(
        "experiments", null, "enabled", ImmutableList.of("NewFeature", "DisabledFeature"));
    serverConfig.setStringList(
        "experiments", null, "disabled", ImmutableList.of("DisabledFeature"));
    ExperimentFeatures experimentFeatures = new ConfigExperimentFeatures(serverConfig);
    ServerInfo serverInfo = new ServerInfo();
    serverInfo.defaultTheme = "my-default-theme";
    IndexServlet servlet =
        new IndexServlet(
            testCanonicalUrl,
            testCdnPath,
            testFaviconURL,
            gerritApi,
            experimentFeatures,
            () -> ServerConfigCacheImpl.ServerConfigData.create(serverInfo, "123"));

    FakeHttpServletResponse response = new FakeHttpServletResponse();

    servlet.doGet(new FakeHttpServletRequest(), response);

    String output = response.getActualBodyString();
    assertThat(output).contains("<!DOCTYPE html>");
    assertThat(output).contains("window.CANONICAL_PATH = '" + testCanonicalUrl);
    assertThat(output).contains("<link rel=\"preload\" href=\"" + testCdnPath);
    assertThat(output)
        .contains(
            "<link rel=\"icon\" type=\"image/x-icon\" href=\""
                + testCanonicalUrl
                + "/"
                + testFaviconURL);
    assertThat(output)
        .contains(
            "window.INITIAL_DATA = JSON.parse("
                + "'\\x7b\\x22foo-url\\/config\\/server\\/top-menus\\x22: \\x5b\\x5d, "
                + "\\x22foo-url\\/config\\/server\\/info\\x22: \\x7b\\x22default_theme\\x22:"
                + "\\x22my-default-theme\\x22\\x7d, \\x22foo-url\\/config\\/server\\/version\\x22: "
                + "\\x22123\\x22\\x7d');");
    ImmutableSet<String> enabledDefaults =
        ExperimentFeaturesConstants.DEFAULT_ENABLED_FEATURES.stream()
            .collect(ImmutableSet.toImmutableSet());
    List<String> expectedEnabled = new ArrayList<>();
    expectedEnabled.add("NewFeature");
    expectedEnabled.addAll(enabledDefaults);
    assertThat(output)
        .contains(
            "window.ENABLED_EXPERIMENTS = JSON.parse('\\x5b\\x22"
                + String.join("\\x22,\\x22", expectedEnabled)
                + "\\x22\\x5d');</script>");
  }

  @Test
  public void serverConfigIsCached() throws Exception {
    when(serverApi.topMenus()).thenReturn(ImmutableList.of(), ImmutableList.of());

    IndexServlet servlet =
        new IndexServlet(
            "foo-url",
            "bar-cdn",
            "zaz-url",
            gerritApi,
            new ConfigExperimentFeatures(new org.eclipse.jgit.lib.Config()),
            serverConfigCache);

    servlet.doGet(new FakeHttpServletRequest(), new FakeHttpServletResponse());
    servlet.doGet(new FakeHttpServletRequest(), new FakeHttpServletResponse());

    verify(serverApi, times(1)).getInfo();
    verify(serverApi, times(1)).getVersion();
    // topMenus is no longer cached, so it should be called dynamically per user request
    verify(serverApi, times(2)).topMenus();
  }

  private void mockGerritApi() throws RestApiException {
    when(accountsApi.self()).thenThrow(new AuthException("user needs to be authenticated"));
    when(serverApi.getVersion()).thenReturn("123");
    ServerInfo serverInfo = new ServerInfo();
    serverInfo.defaultTheme = "my-default-theme";
    when(serverApi.getInfo()).thenReturn(serverInfo);
    when(configApi.server()).thenReturn(serverApi);
    when(gerritApi.accounts()).thenReturn(accountsApi);
    when(gerritApi.config()).thenReturn(configApi);
  }
}
