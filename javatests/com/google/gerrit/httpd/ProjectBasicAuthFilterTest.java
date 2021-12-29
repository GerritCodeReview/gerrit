// Copyright (C) 2021 The Android Open Source Project
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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.GitBasicAuthPolicy;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.PasswordVerifier;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectBasicAuthFilterTest {
  private static final Base64.Encoder B64_ENC = Base64.getEncoder();
  private static final Account.Id AUTH_ACCOUNT_ID = Account.id(1000);
  private static final String AUTH_USER = "johndoe";
  private static final String AUTH_USER_B64 =
      B64_ENC.encodeToString(AUTH_USER.getBytes(StandardCharsets.UTF_8));
  private static final String AUTH_PASSWORD = "jd123";
  private static final String GERRIT_COOKIE_KEY = "GerritAccount";
  private static final String AUTH_COOKIE_VALUE = "gerritcookie";

  @Mock private DynamicItem<WebSession> webSessionItem;

  @Mock private AccountCache accountCache;

  @Mock private AccountState accountState;

  @Mock private Account account;

  @Mock private AccountManager accountManager;

  @Mock private AuthConfig authConfig;

  @Mock private FilterChain chain;

  @Captor private ArgumentCaptor<HttpServletResponse> filterResponseCaptor;

  @Mock private IdentifiedUser.RequestFactory userRequestFactory;

  @Mock private WebSessionManager webSessionManager;

  private WebSession webSession;
  private FakeHttpServletRequest req;
  private HttpServletResponse res;
  private AuthResult authSuccessful;
  private ExternalIdFactory extIdFactory;
  private ExternalIdKeyFactory extIdKeyFactory;
  private PasswordVerifier pwdVerifier;
  private AuthRequest.Factory authRequestFactory;

  @Before
  public void setUp() throws Exception {
    req = new FakeHttpServletRequest("gerrit.example.com", 80, "", "");
    res = new FakeHttpServletResponse();

    extIdKeyFactory = new ExternalIdKeyFactory(new ExternalIdKeyFactory.ConfigImpl(authConfig));
    extIdFactory = new ExternalIdFactory(extIdKeyFactory, authConfig);
    authRequestFactory = new AuthRequest.Factory(extIdKeyFactory);
    pwdVerifier = new PasswordVerifier(extIdKeyFactory, authConfig);

    authSuccessful =
        new AuthResult(AUTH_ACCOUNT_ID, extIdKeyFactory.create("username", AUTH_USER), false);
    doReturn(Optional.of(accountState)).when(accountCache).getByUsername(AUTH_USER);
    doReturn(Optional.of(accountState)).when(accountCache).get(AUTH_ACCOUNT_ID);
    doReturn(account).when(accountState).account();
    doReturn(true).when(account).isActive();
    doReturn(authSuccessful).when(accountManager).authenticate(any());

    doReturn(new WebSessionManager.Key(AUTH_COOKIE_VALUE)).when(webSessionManager).createKey(any());
    WebSessionManager.Val webSessionValue =
        WebSessionManager.Val.builder()
            .accountId(AUTH_ACCOUNT_ID)
            .refreshCookieAt(0L)
            .persistentCookie(false)
            .externalId(null)
            .expiresAt(0L)
            .sessionId("")
            .auth("")
            .build();
    doReturn(webSessionValue)
        .when(webSessionManager)
        .createVal(any(), any(), eq(false), any(), any(), any());
  }

  @Test
  public void shouldAllowAnonymousRequest() throws Exception {
    initMockedWebSession();
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(
            webSessionItem,
            accountCache,
            accountManager,
            authConfig,
            authRequestFactory,
            pwdVerifier);

    basicAuthFilter.doFilter(req, res, chain);

    verify(chain).doFilter(eq(req), filterResponseCaptor.capture());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldRequestAuthenticationForBasicAuthRequest() throws Exception {
    initMockedWebSession();
    req.addHeader("Authorization", "Basic " + AUTH_USER_B64);
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(
            webSessionItem,
            accountCache,
            accountManager,
            authConfig,
            authRequestFactory,
            pwdVerifier);

    basicAuthFilter.doFilter(req, res, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(res.getHeader("WWW-Authenticate")).contains("Basic realm=");
  }

  @Test
  public void shouldAuthenticateSucessfullyAgainstRealmAndReturnCookie() throws Exception {
    initWebSessionWithoutCookie();
    requestBasicAuth(req);
    res.setStatus(HttpServletResponse.SC_OK);

    doReturn(true).when(account).isActive();
    doReturn(GitBasicAuthPolicy.LDAP).when(authConfig).getGitBasicAuthPolicy();

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(
            webSessionItem,
            accountCache,
            accountManager,
            authConfig,
            authRequestFactory,
            pwdVerifier);

    basicAuthFilter.doFilter(req, res, chain);

    verify(accountManager).authenticate(any());

    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(res.getHeader("Set-Cookie")).contains(GERRIT_COOKIE_KEY);
  }

  @Test
  public void shouldValidateUserPasswordAndNotReturnCookie() throws Exception {
    initWebSessionWithoutCookie();
    requestBasicAuth(req);
    initMockedUsernamePasswordExternalId();
    doReturn(GitBasicAuthPolicy.HTTP).when(authConfig).getGitBasicAuthPolicy();
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(
            webSessionItem,
            accountCache,
            accountManager,
            authConfig,
            authRequestFactory,
            pwdVerifier);

    basicAuthFilter.doFilter(req, res, chain);

    verify(accountManager, never()).authenticate(any());

    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(res.getHeader("Set-Cookie")).isNull();
  }

  @Test
  public void shouldNotReauthenticateForGitPostRequest() throws Exception {
    req.setPathInfo("/a/project.git/git-upload-pack");
    req.setMethod("POST");
    req.addHeader("Content-Type", "application/x-git-upload-pack-request");
    doFilterForRequestWhenAlreadySignedIn();

    verify(accountManager, never()).authenticate(any());
    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldReauthenticateForRegularRequestEvenIfAlreadySignedIn() throws Exception {
    doReturn(GitBasicAuthPolicy.LDAP).when(authConfig).getGitBasicAuthPolicy();
    doFilterForRequestWhenAlreadySignedIn();

    verify(accountManager).authenticate(any());
    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldReauthenticateEvenIfHasExistingCookie() throws Exception {
    initWebSessionWithCookie("GerritAccount=" + AUTH_COOKIE_VALUE);
    res.setStatus(HttpServletResponse.SC_OK);
    requestBasicAuth(req);
    doReturn(GitBasicAuthPolicy.LDAP).when(authConfig).getGitBasicAuthPolicy();

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(
            webSessionItem,
            accountCache,
            accountManager,
            authConfig,
            authRequestFactory,
            pwdVerifier);

    basicAuthFilter.doFilter(req, res, chain);

    verify(accountManager).authenticate(any());
    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldFailedAuthenticationAgainstRealm() throws Exception {
    initMockedWebSession();
    requestBasicAuth(req);

    doReturn(true).when(account).isActive();
    doThrow(new AccountException("Authentication error")).when(accountManager).authenticate(any());
    doReturn(GitBasicAuthPolicy.LDAP).when(authConfig).getGitBasicAuthPolicy();

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(
            webSessionItem,
            accountCache,
            accountManager,
            authConfig,
            authRequestFactory,
            pwdVerifier);

    basicAuthFilter.doFilter(req, res, chain);

    verify(accountManager).authenticate(any());

    verify(chain, never()).doFilter(any(), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }

  private void doFilterForRequestWhenAlreadySignedIn()
      throws IOException, ServletException, AccountException {
    initMockedWebSession();
    doReturn(true).when(account).isActive();
    doReturn(true).when(webSession).isSignedIn();
    doReturn(authSuccessful).when(accountManager).authenticate(any());
    requestBasicAuth(req);
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(
            webSessionItem,
            accountCache,
            accountManager,
            authConfig,
            authRequestFactory,
            pwdVerifier);

    basicAuthFilter.doFilter(req, res, chain);
  }

  private void initWebSessionWithCookie(String cookie) {
    req.addHeader("Cookie", cookie);
    initWebSessionWithoutCookie();
  }

  private void initWebSessionWithoutCookie() {
    webSession =
        new CacheBasedWebSession(
            req, res, webSessionManager, authConfig, null, userRequestFactory, accountCache) {};
    doReturn(webSession).when(webSessionItem).get();
  }

  private void initMockedWebSession() {
    webSession = mock(WebSession.class);
    doReturn(webSession).when(webSessionItem).get();
  }

  private void initMockedUsernamePasswordExternalId() {
    ExternalId extId =
        extIdFactory.createWithPassword(
            extIdKeyFactory.create(ExternalId.SCHEME_USERNAME, AUTH_USER),
            AUTH_ACCOUNT_ID,
            null,
            AUTH_PASSWORD);
    doReturn(ImmutableSet.builder().add(extId).build()).when(accountState).externalIds();
  }

  private void requestBasicAuth(FakeHttpServletRequest fakeReq) {
    fakeReq.addHeader(
        "Authorization",
        "Basic "
            + B64_ENC.encodeToString(
                (AUTH_USER + ":" + AUTH_PASSWORD).getBytes(StandardCharsets.UTF_8)));
  }
}
