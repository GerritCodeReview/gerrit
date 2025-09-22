// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Optional;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectOAuthFilterTest {
  private static final Base64.Encoder B64_ENC = Base64.getEncoder();
  private static final Account.Id AUTH_ACCOUNT_ID = Account.id(1000);
  private static final String AUTH_USER = "johndoe";
  private static final String AUTH_USER_B64 =
      B64_ENC.encodeToString(AUTH_USER.getBytes(StandardCharsets.UTF_8));
  private static final String OAUTH_TOKEN =
      B64_ENC.encodeToString("{\"sub\":\"johndoe\"}".getBytes(UTF_8));
  private static final String AUTH_USER_PWD_B64 =
      B64_ENC.encodeToString(
          String.format("%s:%s", AUTH_USER, OAUTH_TOKEN).getBytes(StandardCharsets.UTF_8));
  private static final OAuthUserInfo OAUTH_USER_INFO =
      new OAuthUserInfo(
          String.format("oauth-test:%s", AUTH_USER),
          AUTH_USER,
          "johndoe@example.com",
          "John Doe",
          null);

  @Mock private DynamicItem<WebSession> webSessionItem;

  @Mock private AccountCache accountCache;

  @Mock private AccountManager accountManager;

  @Mock private AuthConfig authConfig;

  @Mock private FilterChain chain;

  @Captor private ArgumentCaptor<HttpServletResponse> filterResponseCaptor;

  @Mock private IdentifiedUser.RequestFactory userRequestFactory;

  @Mock private WebSessionManager webSessionManager;

  @Mock private DynamicMap<OAuthLoginProvider> pluginsProvider;

  private WebSession webSession;
  private FakeHttpServletRequest req;
  private HttpServletResponse res;
  private AuthResult authSuccessful;
  private ExternalIdKeyFactory extIdKeyFactory;
  private AuthRequest.Factory authRequestFactory;
  private Config gerritConfig = new Config();

  @Before
  public void setUp() throws Exception {
    req = new FakeHttpServletRequest("gerrit.example.com", 80, "", "");
    res = new FakeHttpServletResponse();

    extIdKeyFactory = new ExternalIdKeyFactory(new ExternalIdKeyFactory.ConfigImpl(authConfig));
    authRequestFactory = new AuthRequest.Factory(extIdKeyFactory);

    authSuccessful =
        new AuthResult(AUTH_ACCOUNT_ID, extIdKeyFactory.create("username", AUTH_USER), false);
    doReturn(authSuccessful).when(accountManager).authenticate(any());

    doReturn(
            new OAuthLoginProvider() {
              @Override
              public OAuthUserInfo login(String username, String secret) throws IOException {
                if (username.equals(AUTH_USER) && secret.equals(OAUTH_TOKEN)) {
                  return OAUTH_USER_INFO;
                }
                throw new IOException("Authentication error");
              }
            })
        .when(pluginsProvider)
        .get(any(), any());
    gerritConfig.setString("auth", null, "gitOAuthProvider", "oauth:test");
  }

  @Test
  public void shouldAllowAnonymousRequest() throws Exception {
    initAccount();
    initWebSession();
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectOAuthFilter oAuthFilter =
        new ProjectOAuthFilter(
            webSessionItem,
            pluginsProvider,
            accountCache,
            accountManager,
            gerritConfig,
            authRequestFactory);

    oAuthFilter.init(null);
    oAuthFilter.doFilter(req, res, chain);

    verify(chain).doFilter(eq(req), filterResponseCaptor.capture());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldRequestAuthenticationForBasicAuthRequest() throws Exception {
    initAccount();
    initWebSession();
    req.addHeader("Authorization", "Basic " + AUTH_USER_B64);
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectOAuthFilter oAuthFilter =
        new ProjectOAuthFilter(
            webSessionItem,
            pluginsProvider,
            accountCache,
            accountManager,
            gerritConfig,
            authRequestFactory);

    oAuthFilter.init(null);
    oAuthFilter.doFilter(req, res, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(res.getHeader("WWW-Authenticate")).contains("Basic realm=");
  }

  @Test
  public void shouldRequestAuthenticationForValidBasicAuthRequest() throws Exception {
    initAccount();
    initWebSession();
    req.addHeader("Authorization", "Basic " + AUTH_USER_PWD_B64);
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectOAuthFilter oAuthFilter =
        new ProjectOAuthFilter(
            webSessionItem,
            pluginsProvider,
            accountCache,
            accountManager,
            gerritConfig,
            authRequestFactory);

    oAuthFilter.init(null);
    oAuthFilter.doFilter(req, res, chain);

    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldRequestAuthenticationForBearerTokenAuthRequest() throws Exception {
    initAccount();
    initWebSession();
    req.addHeader("Authorization", "Bearer " + OAUTH_TOKEN);
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectOAuthFilter oAuthFilter =
        new ProjectOAuthFilter(
            webSessionItem,
            pluginsProvider,
            accountCache,
            accountManager,
            gerritConfig,
            authRequestFactory);

    oAuthFilter.init(null);
    oAuthFilter.doFilter(req, res, chain);

    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldFailAuthenticationIfGerritUserDoesNotExist() throws Exception {
    doReturn(Optional.empty()).when(accountCache).getByUsername(AUTH_USER);
    initWebSession();
    requestBasicAuth(req, OAUTH_TOKEN);

    ProjectOAuthFilter oAuthFilter =
        new ProjectOAuthFilter(
            webSessionItem,
            pluginsProvider,
            accountCache,
            accountManager,
            gerritConfig,
            authRequestFactory);

    oAuthFilter.init(null);
    oAuthFilter.doFilter(req, res, chain);
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void shouldAuthenticateSucessfullyAgainstRealm() throws Exception {
    initAccount();
    initWebSession();
    requestBasicAuth(req, OAUTH_TOKEN);
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectOAuthFilter oAuthFilter =
        new ProjectOAuthFilter(
            webSessionItem,
            pluginsProvider,
            accountCache,
            accountManager,
            gerritConfig,
            authRequestFactory);

    oAuthFilter.init(null);
    oAuthFilter.doFilter(req, res, chain);

    verify(accountManager).authenticate(any());

    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldFailedAuthenticationAgainstRealm() throws Exception {
    initAccount();
    initWebSession();
    requestBasicAuth(req, OAUTH_TOKEN);

    doThrow(new AccountException("Authentication error")).when(accountManager).authenticate(any());

    ProjectOAuthFilter oAuthFilter =
        new ProjectOAuthFilter(
            webSessionItem,
            pluginsProvider,
            accountCache,
            accountManager,
            gerritConfig,
            authRequestFactory);

    oAuthFilter.init(null);
    oAuthFilter.doFilter(req, res, chain);

    verify(accountManager).authenticate(any());

    verify(chain, never()).doFilter(any(), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  private void initAccount() throws Exception {
    initAccount(ImmutableSet.of());
  }

  private void initAccount(Collection<ExternalId> extIds) throws Exception {
    Account account = Account.builder(AUTH_ACCOUNT_ID, Instant.now()).build();
    AccountState accountState = AccountState.forAccount(account, extIds);
    doReturn(Optional.of(accountState)).when(accountCache).getByUsername(AUTH_USER);
  }

  private void initWebSession() {
    webSession =
        new CacheBasedWebSession(
            req, res, webSessionManager, authConfig, null, userRequestFactory, accountCache) {};
    doReturn(webSession).when(webSessionItem).get();
  }

  private void requestBasicAuth(FakeHttpServletRequest fakeReq, String secret) {
    fakeReq.addHeader(
        "Authorization",
        "Basic "
            + B64_ENC.encodeToString((AUTH_USER + ":" + secret).getBytes(StandardCharsets.UTF_8)));
  }
}
