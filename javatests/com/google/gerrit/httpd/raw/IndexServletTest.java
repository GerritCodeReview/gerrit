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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.config.Config;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.common.DownloadInfo;
import com.google.gerrit.extensions.common.DownloadSchemeInfo;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.ServerConfigCache;
import com.google.gerrit.server.config.ServerConfigCacheImpl;
import com.google.gerrit.server.experiments.ConfigExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexServletTest {
  private static final boolean ANONYMOUS_USER = false;
  private static final boolean IDENTIFIED_USER = true;
  private static final DownloadInfo DOWNLOAD_INFO_USER1 =
      getDownloadInfo("http://user1@gerrit/a/repo");
  private static final DownloadInfo DOWNLOAD_INFO_USER2 =
      getDownloadInfo("http://user2@gerrit/a/repo");
  private static final FakeCurrentUser FAKE_CURRENT_USER1 = new FakeCurrentUser("user1");
  private static final FakeCurrentUser FAKE_CURRENT_USER2 = new FakeCurrentUser("user2");

  @Mock Provider<DownloadInfo> downloadInfoProviderMock;

  @Mock Provider<RequestContext> requestContextProviderMock;

  @Mock Provider<ThreadLocalRequestContext> localContextProviderMock;

  @Mock Server serverApi;

  Cache<String, ServerConfigCache.ServerConfigData> cache = CacheBuilder.newBuilder().build();

  private static class FakeCurrentUser extends CurrentUser {
    private static final GroupMembership groups = new ListGroupMembership(List.of());

    private final String username;

    FakeCurrentUser(String name) {
      username = name;
    }

    @Override
    public GroupMembership getEffectiveGroups() {
      return groups;
    }

    @Override
    public Object getCacheKey() {
      return new Object();
    }

    @Override
    public Optional<String> getUserName() {
      return Optional.ofNullable(username);
    }

    @Override
    public boolean isIdentifiedUser() {
      return true;
    }
  }

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
    ServerConfigCache serverConfigCache =
        new ServerConfigCacheImpl(
            cache,
            gerritApi,
            downloadInfoProviderMock,
            IndexServletTest::anonymousUserRequestContext,
            IndexServletTest::anonymousUserThreadLocalContext);
    IndexServlet servlet =
        new IndexServlet(
            testCanonicalUrl,
            testCdnPath,
            testFaviconURL,
            gerritApi,
            experimentFeatures,
            serverConfigCache);

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
    GerritApi gerritApi = mockGerritApi(ANONYMOUS_USER);

    ServerConfigCache serverConfigCache =
        new ServerConfigCacheImpl(
            cache,
            gerritApi,
            downloadInfoProviderMock,
            requestContextProviderMock,
            localContextProviderMock);

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

  @Test
  public void downloadInfoIsCachedForAnonymousUsers() throws Exception {
    GerritApi gerritApi = mockGerritApi(ANONYMOUS_USER);
    ServerConfigCache serverConfigCache =
        new ServerConfigCacheImpl(
            cache,
            gerritApi,
            downloadInfoProviderMock,
            requestContextProviderMock,
            localContextProviderMock);

    IndexServlet servlet =
        new IndexServlet(
            "foo-url",
            "bar-cdn",
            "zaz-url",
            gerritApi,
            new ConfigExperimentFeatures(new org.eclipse.jgit.lib.Config()),
            serverConfigCache);

    FakeHttpServletResponse indexHtmlResponse1 = new FakeHttpServletResponse();
    servlet.doGet(new FakeHttpServletRequest(), indexHtmlResponse1);
    FakeHttpServletResponse indexHtmlResponse2 = new FakeHttpServletResponse();
    servlet.doGet(new FakeHttpServletRequest(), indexHtmlResponse2);

    assertThat(indexHtmlResponse1.getActualBodyString())
        .isEqualTo(indexHtmlResponse2.getActualBodyString());
    verify(downloadInfoProviderMock, never()).get();
  }

  @Test
  public void downloadInfoIsNotCachedForIdentifiedUsers() throws Exception {
    when(downloadInfoProviderMock.get()).thenReturn(DOWNLOAD_INFO_USER1, DOWNLOAD_INFO_USER2);
    GerritApi gerritApi = mockGerritApi(IDENTIFIED_USER);

    ServerConfigCache serverConfigCache =
        new ServerConfigCacheImpl(
            cache,
            gerritApi,
            downloadInfoProviderMock,
            requestContextProviderMock,
            localContextProviderMock);

    IndexServlet servlet =
        new IndexServlet(
            "foo-url",
            "bar-cdn",
            "zaz-url",
            gerritApi,
            new ConfigExperimentFeatures(new org.eclipse.jgit.lib.Config()),
            serverConfigCache);

    FakeHttpServletResponse indexHtmlResponse1 = new FakeHttpServletResponse();
    servlet.doGet(new FakeHttpServletRequest(), indexHtmlResponse1);
    FakeHttpServletResponse indexHtmlResponse2 = new FakeHttpServletResponse();
    servlet.doGet(new FakeHttpServletRequest(), indexHtmlResponse2);

    assertThat(indexHtmlResponse1.getActualBodyString())
        .isNotEqualTo(indexHtmlResponse2.getActualBodyString());
    verify(downloadInfoProviderMock, times(2)).get();
  }

  private GerritApi mockGerritApi(boolean isIdentifiedUser) throws RestApiException {
    Accounts accountsApi = mock(Accounts.class);

    if (isIdentifiedUser) {
      AccountApi accountApi = mock(AccountApi.class);
      when(accountsApi.self()).thenReturn(accountApi);
      when(requestContextProviderMock.get())
          .thenReturn(
              userRequestContext(FAKE_CURRENT_USER1), userRequestContext(FAKE_CURRENT_USER2));
    } else {
      when(accountsApi.self()).thenThrow(new AuthException("user needs to be authenticated"));
      when(requestContextProviderMock.get()).thenReturn(anonymousUserRequestContext());
    }
    when(localContextProviderMock.get()).thenReturn(new ThreadLocalRequestContext());

    when(serverApi.getVersion()).thenReturn("123");
    when(serverApi.topMenus()).thenReturn(ImmutableList.of(), ImmutableList.of());
    ServerInfo serverInfo = new ServerInfo();
    serverInfo.defaultTheme = "my-default-theme";
    when(serverApi.getInfo()).thenReturn(serverInfo);

    Config configApi = mock(Config.class);
    when(configApi.server()).thenReturn(serverApi);

    GerritApi gerritApi = mock(GerritApi.class);
    when(gerritApi.accounts()).thenReturn(accountsApi);
    when(gerritApi.config()).thenReturn(configApi);
    return gerritApi;
  }

  private static DownloadInfo getDownloadInfo(String url) {
    DownloadInfo downloadInfo1 = new DownloadInfo();
    DownloadSchemeInfo downloadSchemeInfo1 = new DownloadSchemeInfo();
    downloadSchemeInfo1.url = url;
    downloadInfo1.schemes = Map.of("http", downloadSchemeInfo1);
    return downloadInfo1;
  }

  private static RequestContext anonymousUserRequestContext() {
    return AnonymousUser::new;
  }

  private static RequestContext userRequestContext(CurrentUser user) {
    return () -> user;
  }

  private static ThreadLocalRequestContext anonymousUserThreadLocalContext() {
    ThreadLocalRequestContext ctx = new ThreadLocalRequestContext();
    RequestContext ignored = ctx.setContext(AnonymousUser::new);
    return ctx;
  }
}
