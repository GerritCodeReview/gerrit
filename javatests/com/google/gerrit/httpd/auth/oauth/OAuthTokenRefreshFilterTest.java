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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.auth.oauth.OAuthTokenRefresher;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthRevokedException;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.util.Providers;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Covers only the filter's request-time responsibilities: the gate (who is refreshed) and the
 * response to a revoked grant. The refresh mechanics themselves live in {@link OAuthTokenRefresher}
 * and are covered by its own test.
 */
@RunWith(MockitoJUnitRunner.class)
public class OAuthTokenRefreshFilterTest {
  private static final Account.Id ACCOUNT = Account.id(1);

  @Mock private CurrentUser currentUser;
  @Mock private WebSession webSession;
  @Mock private OAuthTokenRefresher refresher;
  @Mock private FilterChain chain;
  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse res;

  private OAuthTokenRefreshFilter filter;

  @Before
  public void setUp() {
    filter =
        new OAuthTokenRefreshFilter(
            Providers.of(currentUser), DynamicItem.itemOf(WebSession.class, webSession), refresher);
    // Happy-path gates; lenient so tests that short-circuit earlier don't trip strict stubbing.
    lenient().when(req.getContextPath()).thenReturn("");
    lenient().when(webSession.isSignedIn()).thenReturn(true);
    lenient().when(currentUser.isIdentifiedUser()).thenReturn(true);
    // REST_API is the path PolyGerrit browser requests carry; WEB_BROWSER is never assigned
    // by Gerrit (see AccessPath / RestApiServlet), so the gate excludes GIT rather than
    // requiring WEB_BROWSER.
    lenient().when(currentUser.getAccessPath()).thenReturn(AccessPath.REST_API);
    lenient().when(currentUser.getAccountId()).thenReturn(ACCOUNT);
  }

  @Test
  public void signedInInteractiveRequest_refreshesAndProceeds() throws Exception {
    filter.doFilter(req, res, chain);

    verify(refresher).refreshIfExpired(ACCOUNT);
    verify(webSession, never()).logout();
    verify(chain).doFilter(req, res);
  }

  @Test
  public void unknownAccessPath_refreshesAndProceeds() throws Exception {
    // The host page carries UNKNOWN (not GIT), so it is still an interactive request.
    when(currentUser.getAccessPath()).thenReturn(AccessPath.UNKNOWN);

    filter.doFilter(req, res, chain);

    verify(refresher).refreshIfExpired(ACCOUNT);
    verify(chain).doFilter(req, res);
  }

  @Test
  public void revokedGrant_endsSession_andRedirectsToLogin() throws Exception {
    doThrow(new OAuthRevokedException("grant gone")).when(refresher).refreshIfExpired(ACCOUNT);

    filter.doFilter(req, res, chain);

    verify(webSession).logout();
    verify(res).sendRedirect("/login");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  public void notSignedIn_noop() throws Exception {
    when(webSession.isSignedIn()).thenReturn(false);

    filter.doFilter(req, res, chain);

    verifyNoInteractions(refresher);
    verify(chain).doFilter(req, res);
  }

  @Test
  public void notIdentified_noop() throws Exception {
    when(currentUser.isIdentifiedUser()).thenReturn(false);

    filter.doFilter(req, res, chain);

    verifyNoInteractions(refresher);
    verify(chain).doFilter(req, res);
  }

  @Test
  public void gitAccessPath_noop() throws Exception {
    // Git-over-HTTP authenticates per request and uses the plugin's own validation cache; the
    // browser refresh filter must never act on it.
    when(currentUser.getAccessPath()).thenReturn(AccessPath.GIT);

    filter.doFilter(req, res, chain);

    verifyNoInteractions(refresher);
    verify(chain).doFilter(req, res);
  }
}
