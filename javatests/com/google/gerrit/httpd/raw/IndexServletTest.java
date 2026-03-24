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
import static com.google.template.soy.data.ordainers.GsonOrdainer.serializeObject;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.experiments.ConfigExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.server.restapi.config.CachedServerConfig;
import com.google.gerrit.server.restapi.config.GetServerInfo;
import com.google.gerrit.server.restapi.config.GetVersion;
import com.google.gerrit.server.restapi.config.ListTopMenus;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import com.google.gson.Gson;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class IndexServletTest {

  @Test
  public void renderTemplate() throws Exception {
    Accounts accountsApi = mock(Accounts.class);
    when(accountsApi.self()).thenThrow(new AuthException("user needs to be authenticated"));

    Server serverApi = mock(Server.class);
    when(serverApi.getVersion()).thenReturn("123");
    when(serverApi.topMenus()).thenReturn(ImmutableList.of());
    ServerInfo serverInfo = new ServerInfo();
    serverInfo.defaultTheme = "my-default-theme";
    when(serverApi.getInfo()).thenReturn(serverInfo);

    Config configApi = mock(Config.class);
    when(configApi.server()).thenReturn(serverApi);

    GerritApi gerritApi = mock(GerritApi.class);
    when(gerritApi.accounts()).thenReturn(accountsApi);
    when(gerritApi.config()).thenReturn(configApi);

    CachedServerConfig cachedServerConfig = mock(CachedServerConfig.class);
    Gson GSON = OutputFormat.JSON_COMPACT.newGson();
    CachedServerConfig.ServerConfigData configData = new CachedServerConfig.ServerConfigData(
        serializeObject(GSON, serverInfo),
        serializeObject(GSON, "123"),
        serializeObject(GSON, ImmutableList.of())
    );
    when(cachedServerConfig.getServerConfig()).thenReturn(configData);

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
    IndexServlet servlet =
        new IndexServlet(
            testCanonicalUrl,
            testCdnPath,
            testFaviconURL,
            gerritApi,
            cachedServerConfig,
            experimentFeatures);

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
  public void cachingBehavior() throws Exception {
    Accounts accountsApi = mock(Accounts.class);
    when(accountsApi.self()).thenThrow(new AuthException("user needs to be authenticated"));

    GerritApi gerritApi = mock(GerritApi.class);
    when(gerritApi.accounts()).thenReturn(accountsApi);

    @SuppressWarnings("unchecked")
    Provider<GetServerInfo> getServerInfoProvider = mock(Provider.class);
    @SuppressWarnings("unchecked")
    Provider<GetVersion> getVersionProvider = mock(Provider.class);
    @SuppressWarnings("unchecked")
    Provider<ListTopMenus> listTopMenusProvider = mock(Provider.class);

    GetServerInfo getServerInfo = mock(GetServerInfo.class);
    GetVersion getVersion = mock(GetVersion.class);
    ListTopMenus listTopMenus = mock(ListTopMenus.class);

    when(getServerInfoProvider.get()).thenReturn(getServerInfo);
    when(getVersionProvider.get()).thenReturn(getVersion);
    when(listTopMenusProvider.get()).thenReturn(listTopMenus);

    when(getServerInfo.apply(any())).thenReturn(Response.ok(new ServerInfo()));
    when(getVersion.apply(any())).thenReturn(Response.ok("123"));
    when(listTopMenus.apply(any())).thenReturn(Response.ok(ImmutableList.of()));

    CachedServerConfig cachedServerConfig = new CachedServerConfig(
        getServerInfoProvider, getVersionProvider, listTopMenusProvider);

    ExperimentFeatures experimentFeatures = new ConfigExperimentFeatures(new org.eclipse.jgit.lib.Config());
    IndexServlet servlet =
        new IndexServlet(null, null, null, gerritApi, cachedServerConfig, experimentFeatures);

    servlet.doGet(new FakeHttpServletRequest(), new FakeHttpServletResponse());
    servlet.doGet(new FakeHttpServletRequest(), new FakeHttpServletResponse());

    verify(getServerInfo, times(1)).apply(any());
  }
}
