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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.GitBasicAuthPolicy;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import javax.servlet.FilterChain;
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

  @Mock private DynamicItem<WebSession> webSessionItem;

  @Mock private WebSession webSession;

  @Mock private AccountCache accountCache;

  @Mock private AccountState accountState;

  @Mock private Account account;

  @Mock private AccountManager accountManager;

  @Mock private AuthConfig authConfig;

  @Mock private FilterChain chain;

  @Captor private ArgumentCaptor<HttpServletResponse> filterResponseCaptor;

  private FakeHttpServletRequest req;
  private HttpServletResponse res;

  @Before
  public void setUp() throws Exception {
    doReturn(webSession).when(webSessionItem).get();
    doReturn(Optional.of(accountState)).when(accountCache).getByUsername(AUTH_USER);
    doReturn(account).when(accountState).account();

    req = new FakeHttpServletRequest();
    res = new FakeHttpServletResponse();
  }

  @Test
  public void shouldAllowAnonymousRequest() throws Exception {
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(webSessionItem, accountCache, accountManager, authConfig);

    basicAuthFilter.doFilter(req, res, chain);

    verify(chain).doFilter(eq(req), filterResponseCaptor.capture());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldRequestAuthenticationForBasicAuthRequest() throws Exception {
    req.addHeader("Authorization", "Basic " + AUTH_USER_B64);
    res.setStatus(HttpServletResponse.SC_OK);

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(webSessionItem, accountCache, accountManager, authConfig);

    basicAuthFilter.doFilter(req, res, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(res.getHeader("WWW-Authenticate")).contains("Basic realm=");
  }

  @Test
  public void shouldAuthenticateSucessfullyAgainstRealm() throws Exception {
    req.addHeader(
        "Authorization",
        "Basic "
            + B64_ENC.encodeToString(
                (AUTH_USER + ":" + AUTH_PASSWORD).getBytes(StandardCharsets.UTF_8)));
    res.setStatus(HttpServletResponse.SC_OK);

    AuthResult authSuccessful =
        new AuthResult(AUTH_ACCOUNT_ID, ExternalId.Key.create("username", AUTH_USER), false);
    doReturn(true).when(account).isActive();
    doReturn(authSuccessful).when(accountManager).authenticate(any());
    doReturn(GitBasicAuthPolicy.LDAP).when(authConfig).getGitBasicAuthPolicy();

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(webSessionItem, accountCache, accountManager, authConfig);

    basicAuthFilter.doFilter(req, res, chain);

    verify(accountManager).authenticate(any());

    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldNotReauthenticateIfAlreadySignedIn() throws Exception {
    req.addHeader(
        "Authorization",
        "Basic "
            + B64_ENC.encodeToString(
                (AUTH_USER + ":" + AUTH_PASSWORD).getBytes(StandardCharsets.UTF_8)));
    res.setStatus(HttpServletResponse.SC_OK);

    doReturn(true).when(webSession).isSignedIn();

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(webSessionItem, accountCache, accountManager, authConfig);

    basicAuthFilter.doFilter(req, res, chain);

    verify(accountManager, never()).authenticate(any());
    verify(chain).doFilter(eq(req), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void shouldFailedAuthenticationAgainstRealm() throws Exception {
    req.addHeader(
        "Authorization",
        "Basic "
            + B64_ENC.encodeToString(
                (AUTH_USER + ":" + AUTH_PASSWORD).getBytes(StandardCharsets.UTF_8)));

    doReturn(true).when(account).isActive();
    doThrow(new AccountException("Authentication error")).when(accountManager).authenticate(any());
    doReturn(GitBasicAuthPolicy.LDAP).when(authConfig).getGitBasicAuthPolicy();

    ProjectBasicAuthFilter basicAuthFilter =
        new ProjectBasicAuthFilter(webSessionItem, accountCache, accountManager, authConfig);

    basicAuthFilter.doFilter(req, res, chain);

    verify(accountManager).authenticate(any());

    verify(chain, never()).doFilter(any(), any());
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
  }
}
