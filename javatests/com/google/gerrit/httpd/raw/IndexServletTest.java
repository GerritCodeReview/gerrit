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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.config.Config;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.config.CloneCommand;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.experiments.ConfigExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.server.restapi.config.GetServerInfo;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexServletTest {
  private static final String FAKE_USER1 = "user1";
  private static final String FAKE_USER2 = "user2";
  private static final FakeCurrentUser FAKE_CURRENT_USER1 = new FakeCurrentUser(FAKE_USER1);
  private static final FakeCurrentUser FAKE_CURRENT_USER2 = new FakeCurrentUser(FAKE_USER2);

  @Mock Server serverApi;

  @Mock GerritApi gerritApi;

  @Mock Config configApi;

  @Mock Accounts accountsApi;

  @Inject ThreadLocalRequestContext threadLocalRequestContext;

  @Inject GetServerInfo getServerInfo;

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
      return username;
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

  private static class FakeDownloadScheme extends DownloadScheme {
    @Override
    public String getUrl(String project) {
      return "some-protocol://" + project;
    }

    @Override
    public boolean isAuthRequired() {
      return true;
    }

    @Override
    public boolean isAuthSupported() {
      return true;
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    @Override
    public boolean isHidden() {
      return false;
    }
  }

  private static class FakeDownloadCommand extends DownloadCommand {

    @Inject Provider<CurrentUser> currentUserProvider;

    @Override
    public String getCommand(DownloadScheme scheme, String project, String ref) {
      CurrentUser currentUser = currentUserProvider.get();
      String url = scheme.getUrl(project);
      if (currentUser.isIdentifiedUser()) {
        url = url + "/" + currentUser.getUserName().orElseThrow();
      }
      return String.format("fake git fetch %s %s", url, ref);
    }
  }

  private static class FakeCloneCommand extends CloneCommand {

    @Inject Provider<CurrentUser> currentUserProvider;

    @Override
    public String getCommand(DownloadScheme scheme, String project) {
      CurrentUser currentUser = currentUserProvider.get();
      String url = scheme.getUrl(project);
      if (currentUser.isIdentifiedUser()) {
        url = url + "/" + currentUser.getUserName().orElseThrow();
      }
      return String.format("fake git clone %s", url);
    }
  }

  @Before
  public void setup() throws RestApiException {
    Injector injector =
        Guice.createInjector(
            new InMemoryModule() {
              @Override
              protected void configure() {
                configure(false);
                bind(GerritApi.class).toInstance(gerritApi);
                bind(DownloadScheme.class)
                    .annotatedWith(Exports.named("testscheme"))
                    .to(FakeDownloadScheme.class);
                bind(DownloadCommand.class)
                    .annotatedWith(Exports.named("checkout"))
                    .to(FakeDownloadCommand.class);
                bind(CloneCommand.class)
                    .annotatedWith(Exports.named("clone"))
                    .to(FakeCloneCommand.class);
              }
            });
    injector.injectMembers(this);
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
    IndexServlet servlet =
        new IndexServlet(
            testCanonicalUrl, testCdnPath, testFaviconURL, gerritApi, experimentFeatures);

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
  public void downloadInfoIsTheSameForTwoAnonymousUsers() throws Exception {
    try (ManualRequestContext ctx = mockGerritApi(new AnonymousUser())) {

      IndexServlet servlet =
          new IndexServlet(
              null,
              null,
              null,
              gerritApi,
              new ConfigExperimentFeatures(new org.eclipse.jgit.lib.Config()));

      FakeHttpServletResponse indexHtmlResponse1 = new FakeHttpServletResponse();
      servlet.doGet(new FakeHttpServletRequest(), indexHtmlResponse1);
      FakeHttpServletResponse indexHtmlResponse2 = new FakeHttpServletResponse();
      servlet.doGet(new FakeHttpServletRequest(), indexHtmlResponse2);

      assertThat(indexHtmlResponse1.getActualBodyString())
          .isEqualTo(indexHtmlResponse2.getActualBodyString());
    }
  }

  @Test
  public void downloadInfoIsNotCachedForIdentifiedUsers() throws Exception {
    FakeHttpServletResponse indexHtmlResponse1 = new FakeHttpServletResponse();
    FakeHttpServletResponse indexHtmlResponse2 = new FakeHttpServletResponse();

    IndexServlet servlet =
        new IndexServlet(
            null,
            null,
            null,
            gerritApi,
            new ConfigExperimentFeatures(new org.eclipse.jgit.lib.Config()));

    try (ManualRequestContext ctx = mockGerritApi(FAKE_CURRENT_USER1)) {
      servlet.doGet(new FakeHttpServletRequest(), indexHtmlResponse1);
    }

    try (ManualRequestContext ctx = mockGerritApi(FAKE_CURRENT_USER2)) {
      servlet.doGet(new FakeHttpServletRequest(), indexHtmlResponse2);
    }

    String indexBodyUser1 = indexHtmlResponse1.getActualBodyString();
    String indexBodyUser2 = indexHtmlResponse2.getActualBodyString();
    assertThat(indexBodyUser1).contains(FAKE_USER1);
    assertThat(indexBodyUser2).contains(FAKE_USER2);
    assertThat(indexBodyUser1).isNotEqualTo(indexBodyUser2);
    verify(serverApi, times(2)).getInfo();
  }

  private ManualRequestContext mockGerritApi(CurrentUser currentUser) throws RestApiException {
    ManualRequestContext ctx = new ManualRequestContext(currentUser, threadLocalRequestContext);
    if (currentUser.isIdentifiedUser()) {
      AccountApi accountApi = mock(AccountApi.class);
      lenient().when(accountsApi.self()).thenReturn(accountApi);
    } else {
      lenient()
          .when(accountsApi.self())
          .thenThrow(new AuthException("user needs to be authenticated"));
    }

    lenient().when(serverApi.getVersion()).thenReturn("123");
    lenient().when(serverApi.topMenus()).thenReturn(ImmutableList.of(), ImmutableList.of());
    lenient()
        .when(serverApi.getInfo())
        .thenAnswer((m) -> getServerInfo.apply(new ConfigResource()).value());
    lenient().when(configApi.server()).thenReturn(serverApi);
    lenient().when(gerritApi.accounts()).thenReturn(accountsApi);
    lenient().when(gerritApi.config()).thenReturn(configApi);
    return ctx;
  }
}
