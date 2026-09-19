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

package com.google.gerrit.httpd.auth.oauth;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.template.SiteHeaderFooter;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import com.google.inject.Provider;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.servlet.FilterChain;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class OAuthWebFilterLinkInitiationTest {
  private static final String XSRF = "session-xsrf";

  @Mock private DynamicItem<WebSession> webSessionItem;
  @Mock private WebSession webSession;
  @Mock private DynamicMap<OAuthServiceProvider> oauthServiceProviders;
  @Mock private Provider<OAuthSession> oauthSessionProvider;
  @Mock private OAuthSession oauthSession;
  @Mock private OAuthServiceProvider oauthProvider;
  @Mock private SiteHeaderFooter header;
  @Mock private FilterChain chain;

  private OAuthWebFilter filter;

  @Before
  public void setUp() throws Exception {
    when(webSessionItem.get()).thenReturn(webSession);
    when(webSession.getXGerritAuth()).thenReturn(XSRF);
    when(oauthSessionProvider.get()).thenReturn(oauthSession);
    when(oauthSession.getServiceProvider()).thenReturn(null);
    when(oauthProvider.getName()).thenReturn("OAuth Provider");
    when(header.parse(eq(OAuthWebFilter.class), eq("LoginForm.html")))
        .thenAnswer(unused -> HtmlDomUtil.parseFile(OAuthWebFilter.class, "LoginForm.html"));

    when(oauthServiceProviders.plugins()).thenReturn(new TreeSet<>(Set.of("plugin")));
    NavigableMap<String, Provider<OAuthServiceProvider>> providers = new TreeMap<>();
    providers.put("oauth", () -> oauthProvider);
    when(oauthServiceProviders.byPlugin("plugin")).thenReturn(providers);

    filter =
        new OAuthWebFilter(
            () -> "http://gerrit.example/",
            webSessionItem,
            oauthServiceProviders,
            oauthSessionProvider,
            header);
  }

  @Test
  public void linkGetDoesNotEnterLinkModeOrStartOAuthLogin() throws Exception {
    FakeHttpServletRequest req = loginRequest("link&provider=plugin_oauth");
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(401);
    verify(oauthSession, never()).setLinkMode(true);
    verify(oauthSession, never()).login(req, res, oauthProvider);
  }

  @Test
  public void linkPostWithoutSignedInSessionDoesNotEnterLinkModeOrStartOAuthLogin()
      throws Exception {
    when(webSession.isSignedIn()).thenReturn(false);
    FakeHttpServletRequest req = postLoginRequest("link&provider=plugin_oauth&xsrfKey=" + XSRF);
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(401);
    verify(oauthSession, never()).setLinkMode(true);
    verify(oauthSession, never()).login(req, res, oauthProvider);
  }

  @Test
  public void linkPostWithMissingXsrfDoesNotEnterLinkModeOrStartOAuthLogin() throws Exception {
    when(webSession.isSignedIn()).thenReturn(true);
    FakeHttpServletRequest req = postLoginRequest("link&provider=plugin_oauth");
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(401);
    verify(oauthSession, never()).setLinkMode(true);
    verify(oauthSession, never()).login(req, res, oauthProvider);
  }

  @Test
  public void linkPostWithInvalidXsrfDoesNotEnterLinkModeOrStartOAuthLogin() throws Exception {
    when(webSession.isSignedIn()).thenReturn(true);
    when(webSession.isValidXGerritAuth("bad-xsrf")).thenReturn(false);
    FakeHttpServletRequest req = postLoginRequest("link&provider=plugin_oauth&xsrfKey=bad-xsrf");
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(401);
    verify(oauthSession, never()).setLinkMode(true);
    verify(oauthSession, never()).login(req, res, oauthProvider);
  }

  @Test
  public void linkPostWithValidXsrfEntersLinkModeAndStartsOAuthLogin() throws Exception {
    when(webSession.isSignedIn()).thenReturn(true);
    when(webSession.isValidXGerritAuth(XSRF)).thenReturn(true);
    FakeHttpServletRequest req = postLoginRequest("link&provider=plugin_oauth&xsrfKey=" + XSRF);
    FakeHttpServletResponse res = new FakeHttpServletResponse();

    filter.doFilter(req, res, chain);

    verify(oauthSession).setLinkMode(true);
    verify(oauthSession).setServiceProvider(null);
    verify(oauthSession).setServiceProvider(oauthProvider);
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
