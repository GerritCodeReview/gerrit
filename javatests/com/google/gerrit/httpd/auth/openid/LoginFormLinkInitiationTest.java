// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.openid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.template.SiteHeaderFooter;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import com.google.inject.Provider;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginFormLinkInitiationTest {
  private static final String OPENID_ID = "http://provider.example/id";
  private static final String LAUNCHPAD_ID = "https://login.launchpad.net/+openid";
  private static final String OAUTH_ID = "plugin_oauth";
  private static final String XSRF = "session-xsrf";

  @Mock private OpenIdServiceImpl openIdService;
  @Mock private SiteHeaderFooter header;
  @Mock private OAuthSessionOverOpenID oauthSession;
  @Mock private CurrentUser currentUser;
  @Mock private DynamicItem<WebSession> webSessionItem;
  @Mock private WebSession webSession;
  @Mock private DynamicMap<OAuthServiceProvider> oauthServiceProviders;
  @Mock private OAuthServiceProvider oauthProvider;

  private LoginForm loginForm;

  @Before
  public void setUp() throws Exception {
    AuthConfig authConfig = mock(AuthConfig.class);
    when(authConfig.getAuthType()).thenReturn(AuthType.OPENID);
    when(openIdService.isAllowedOpenID(anyString())).thenReturn(true);
    when(openIdService.discover(any(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(new DiscoveryResult("http://provider.example/authorize", Map.of()));
    when(header.parse(eq(LoginForm.class), eq("LoginForm.html")))
        .thenAnswer(unused -> HtmlDomUtil.parseFile(LoginForm.class, "LoginForm.html"));
    when(webSessionItem.get()).thenReturn(webSession);
    when(webSession.getXGerritAuth()).thenReturn(XSRF);

    when(oauthServiceProviders.plugins()).thenReturn(new TreeSet<>(Set.of("plugin")));
    NavigableMap<String, Provider<OAuthServiceProvider>> providers = new TreeMap<>();
    providers.put("oauth", () -> oauthProvider);
    when(oauthServiceProviders.byPlugin("plugin")).thenReturn(providers);
    when(oauthProvider.getName()).thenReturn("OAuth Provider");

    loginForm =
        new LoginForm(
            () -> "http://gerrit.example/",
            new Config(),
            authConfig,
            openIdService,
            header,
            () -> oauthSession,
            () -> currentUser,
            webSessionItem,
            oauthServiceProviders);
  }

  @Test
  public void openIdLinkGetPrefillsFormAndDoesNotStartDiscovery() throws Exception {
    FakeHttpServletRequest req = loginRequest("link&id=http%3A%2F%2Fprovider.example%2Fid");
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    loginForm.doGet(req, res);

    assertThat(res.getStatus()).isEqualTo(401);
    String body = res.getActualBodyString();
    assertThat(body).contains(OPENID_ID);
    assertThat(body).contains("<button");
    assertThat(body).contains("name=\"provider_id\"");
    assertThat(body).contains("value=\"" + LAUNCHPAD_ID + "\"");
    assertThat(body).doesNotContain("href=\"?id=https://login.launchpad.net/%2Bopenid&link\"");
    verify(openIdService, never()).discover(any(), anyString(), any(), anyBoolean(), any());
  }

  @Test
  public void oauthOverOpenIdLinkGetPrefillsFormAndDoesNotStartOAuthLogin() throws Exception {
    FakeHttpServletRequest req = loginRequest("link&id=" + OAUTH_ID);
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    loginForm.doGet(req, res);

    assertThat(res.getStatus()).isEqualTo(401);
    String body = res.getActualBodyString();
    assertThat(body).contains(OAUTH_ID);
    assertThat(body).contains("<button");
    assertThat(body).contains("name=\"provider_id\"");
    assertThat(body).doesNotContain("href=\"?id=" + OAUTH_ID + "&link\"");
    verify(oauthSession, never()).setLinkMode(true);
    verify(oauthSession, never()).login(req, res, oauthProvider);
  }

  @Test
  public void openIdLinkPostWithoutSignedInSessionDoesNotStartDiscovery() throws Exception {
    when(currentUser.isIdentifiedUser()).thenReturn(false);
    when(webSession.isSignedIn()).thenReturn(false);
    FakeHttpServletRequest req =
        postLoginRequest("link&id=http%3A%2F%2Fprovider.example%2Fid&xsrfKey=" + XSRF);
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    loginForm.doPost(req, res);

    assertThat(res.getStatus()).isEqualTo(401);
    verify(openIdService, never()).discover(any(), anyString(), any(), anyBoolean(), any());
  }

  @Test
  public void openIdLinkPostWithMissingXsrfDoesNotStartDiscovery() throws Exception {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(webSession.isSignedIn()).thenReturn(true);
    FakeHttpServletRequest req = postLoginRequest("link&id=http%3A%2F%2Fprovider.example%2Fid");
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    loginForm.doPost(req, res);

    assertThat(res.getStatus()).isEqualTo(401);
    verify(openIdService, never()).discover(any(), anyString(), any(), anyBoolean(), any());
  }

  @Test
  public void openIdLinkPostWithInvalidXsrfDoesNotStartDiscovery() throws Exception {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(webSession.isSignedIn()).thenReturn(true);
    when(webSession.isValidXGerritAuth("bad-xsrf")).thenReturn(false);
    FakeHttpServletRequest req =
        postLoginRequest("link&id=http%3A%2F%2Fprovider.example%2Fid&xsrfKey=bad-xsrf");
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    loginForm.doPost(req, res);

    assertThat(res.getStatus()).isEqualTo(401);
    verify(openIdService, never()).discover(any(), anyString(), any(), anyBoolean(), any());
  }

  @Test
  public void openIdLinkPostWithValidXsrfStartsDiscovery() throws Exception {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(webSession.isSignedIn()).thenReturn(true);
    when(webSession.isValidXGerritAuth(XSRF)).thenReturn(true);
    FakeHttpServletRequest req =
        postLoginRequest("link&id=http%3A%2F%2Fprovider.example%2Fid&xsrfKey=" + XSRF);
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    loginForm.doPost(req, res);

    verify(openIdService)
        .discover(same(req), eq(OPENID_ID), eq(SignInMode.LINK_IDENTIY), eq(false), any());
  }

  @Test
  public void openIdLinkPostWithEmptyTextFieldUsesProviderButton() throws Exception {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(webSession.isSignedIn()).thenReturn(true);
    when(webSession.isValidXGerritAuth(XSRF)).thenReturn(true);
    FakeHttpServletRequest req =
        postLoginRequest(
            "link&id=&provider_id=https%3A%2F%2Flogin.launchpad.net%2F%2Bopenid&xsrfKey=" + XSRF);
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    loginForm.doPost(req, res);

    verify(openIdService)
        .discover(same(req), eq(LAUNCHPAD_ID), eq(SignInMode.LINK_IDENTIY), eq(false), any());
  }

  @Test
  public void oauthOverOpenIdLinkPostWithValidXsrfStartsOAuthLogin() throws Exception {
    when(currentUser.isIdentifiedUser()).thenReturn(true);
    when(webSession.isSignedIn()).thenReturn(true);
    when(webSession.isValidXGerritAuth(XSRF)).thenReturn(true);
    FakeHttpServletRequest req =
        postLoginRequest("link&provider_id=" + OAUTH_ID + "&xsrfKey=" + XSRF);
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    loginForm.doPost(req, res);

    verify(oauthSession).setServiceProvider(oauthProvider);
    verify(oauthSession).setLinkMode(true);
    verify(oauthSession).login(req, res, oauthProvider);
  }

  private static FakeHttpServletRequest loginRequest(String queryString) {
    FakeHttpServletRequest req =
        new FakeHttpServletRequest("gerrit.example.com", 80, "", "/login").setPathInfo("");
    req.setQueryString(queryString);
    return req;
  }

  private static FakeHttpServletRequest postLoginRequest(String queryString) {
    FakeHttpServletRequest req = loginRequest(queryString);
    req.setMethod("POST");
    return req;
  }
}
