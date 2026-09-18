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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.CanonicalWebUrl;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.auth.openid.OpenIdProviderPattern;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Provider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.Message;

@RunWith(MockitoJUnitRunner.Silent.class)
public class OpenIdServiceLinkIdentityTest {
  @Mock private DynamicItem<WebSession> webSession;
  @Mock private Provider<IdentifiedUser> identifiedUser;
  @Mock private CanonicalWebUrl urlProvider;
  @Mock private AuthConfig authConfig;
  @Mock private AccountManager accountManager;
  @Mock private ConsumerManager consumerManager;
  @Mock private ExternalIdKeyFactory externalIdKeyFactory;
  @Mock private AuthRequest.Factory authRequestFactory;
  @Mock private OpenIdProviderPattern allowedProvider;
  @Mock private DiscoveryInformation discovered;
  @Mock private VerificationResult verified;
  @Mock private Identifier verifiedId;
  @Mock private IdentifiedUser user;
  @Mock private AuthRequest areq;
  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse rsp;

  private OpenIdLinkState linkState;
  private OpenIdServiceImpl openIdService;

  @Before
  public void setUp() throws Exception {
    // The current session's link-state. The same instance is wired into the service below
    // (() -> linkState), so linkState.get() in the happy-path tests mints the exact token the guard
    // accepts. A separate "new OpenIdLinkState()" stands in for another session's non-matching
    // token.
    linkState = new OpenIdLinkState();

    when(allowedProvider.matches(anyString())).thenReturn(true);
    when(authConfig.getAllowedOpenIDs()).thenReturn(List.of(allowedProvider));
    when(authConfig.getOpenIdDomains()).thenReturn(List.of());

    // A fresh, valid provider assertion: discovery, association and verification all succeed.
    when(discovered.hasClaimedIdentifier()).thenReturn(false);
    when(consumerManager.discover(anyString())).thenReturn(List.of(discovered));
    when(consumerManager.associate(any())).thenReturn(discovered);
    when(verified.getVerifiedId()).thenReturn(verifiedId);
    when(consumerManager.verify(any(), any(), any())).thenReturn(verified);
    when(urlProvider.get(any(HttpServletRequest.class))).thenReturn("http://gerrit.example/");

    when(verified.getAuthResponse()).thenReturn(mock(Message.class));
    when(authRequestFactory.create(any())).thenReturn(areq);
    when(identifiedUser.get()).thenReturn(user);
    when(user.getAccountId()).thenReturn(Account.id(1));
    when(accountManager.link(any(Account.Id.class), any(AuthRequest.class)))
        .thenReturn(mock(AuthResult.class));
    when(webSession.get()).thenReturn(mock(WebSession.class));

    openIdService =
        new OpenIdServiceImpl(
            webSession,
            identifiedUser,
            urlProvider,
            new Config(),
            authConfig,
            accountManager,
            consumerManager,
            externalIdKeyFactory,
            authRequestFactory,
            () -> linkState);

    when(req.getParameter("gerrit.mode")).thenReturn("LINK_IDENTIY");
    when(req.getParameter("openid.identity")).thenReturn("http://provider.example/id");
    when(req.getParameterMap()).thenReturn(Map.of());
  }

  /**
   * A verified LINK_IDENTIY callback carrying no session-minted link-state token is refused
   * (redirect to SignInFailure) before {@link AccountManager#link} runs.
   */
  @Test
  public void linkWithoutSessionTokenIsRejectedAndDoesNotLink() throws Exception {
    openIdService.doAuth(req, rsp);

    ArgumentCaptor<String> redirect = ArgumentCaptor.forClass(String.class);
    verify(rsp).sendRedirect(redirect.capture());
    assertThat(redirect.getValue()).contains("SignInFailure,LINK_IDENTIY");
    verify(accountManager, never()).link(any(Account.Id.class), any(AuthRequest.class));
  }

  /**
   * A verified LINK_IDENTIY callback carrying another session's link-state token is refused before
   * {@link AccountManager#link} runs.
   */
  @Test
  public void linkWithAnotherSessionsTokenIsRejectedAndDoesNotLink() throws Exception {
    when(req.getParameter("gerrit.link_state")).thenReturn(new OpenIdLinkState().get());

    openIdService.doAuth(req, rsp);

    ArgumentCaptor<String> redirect = ArgumentCaptor.forClass(String.class);
    verify(rsp).sendRedirect(redirect.capture());
    assertThat(redirect.getValue()).contains("SignInFailure,LINK_IDENTIY");
    verify(accountManager, never()).link(any(Account.Id.class), any(AuthRequest.class));
  }

  /**
   * A verified LINK_IDENTIY callback carrying this session's minted link-state token passes the
   * guard and links the identity onto the current account.
   */
  @Test
  public void linkWithSessionTokenProceedsToLink() throws Exception {
    when(req.getParameter("gerrit.link_state")).thenReturn(linkState.get());

    openIdService.doAuth(req, rsp);

    verify(accountManager).link(Account.id(1), areq);
  }

  @Test
  public void claimedIdentityWithoutSessionTokenIsRejectedBeforeReconciliation() throws Exception {
    when(req.getParameter("gerrit.claimed")).thenReturn("http://claimed.example/id");

    openIdService.doAuth(req, rsp);

    verify(accountManager, never()).lookup(anyString());
    verify(accountManager, never()).link(any(Account.Id.class), any(AuthRequest.class));
  }

  @Test
  public void claimedIdentityWithAnotherSessionsTokenIsRejectedBeforeReconciliation()
      throws Exception {
    when(req.getParameter("gerrit.claimed")).thenReturn("http://claimed.example/id");
    when(req.getParameter("gerrit.link_state")).thenReturn(new OpenIdLinkState().get());

    openIdService.doAuth(req, rsp);

    verify(accountManager, never()).lookup(anyString());
    verify(accountManager, never()).link(any(Account.Id.class), any(AuthRequest.class));
  }

  /**
   * A verified LINK_IDENTIY callback carrying a claimed identifier reconciles it: the claimed
   * identity is linked onto the account that owns the provider-asserted identity, and the provider
   * identity is linked onto the current session's account.
   */
  @Test
  public void claimedIdentityWithSessionTokenReconcilesClaimedIdentity() throws Exception {
    // The provider-asserted identity and a different claimed identity, each with its own key.
    String claimedIdentifier = "http://claimed.example/id";
    ExternalId.Key actualKey = ExternalId.Key.create("http", "//provider.example/id", false);
    ExternalId.Key claimedKey = ExternalId.Key.create("http", "//claimed.example/id", false);
    AuthRequest claimedRequest = mock(AuthRequest.class);

    // Callback params: the claimed identifier plus this session's own link-state token, so the
    // guard passes.
    when(req.getParameter("gerrit.claimed")).thenReturn(claimedIdentifier);
    when(req.getParameter("gerrit.link_state")).thenReturn(linkState.get());

    // Each identifier parses to a distinct external-id key, and each key yields its own
    // AuthRequest.
    when(externalIdKeyFactory.parse("http://provider.example/id")).thenReturn(actualKey);
    when(externalIdKeyFactory.parse(claimedIdentifier)).thenReturn(claimedKey);
    when(authRequestFactory.create(actualKey)).thenReturn(areq);
    when(authRequestFactory.create(claimedKey)).thenReturn(claimedRequest);

    // The provider request carries the verified profile that reconciliation copies onto the claim.
    when(areq.getExternalIdKey()).thenReturn(actualKey);
    when(areq.getDisplayName()).thenReturn("OpenID User");
    when(areq.getEmailAddress()).thenReturn("openid-user@example.com");

    // The claimed identifier is not yet linked; the provider identity already belongs to account 2.
    when(accountManager.lookup(claimedIdentifier)).thenReturn(Optional.empty());
    when(accountManager.lookup(actualKey.get())).thenReturn(Optional.of(Account.id(2)));

    openIdService.doAuth(req, rsp);

    // The verified profile is copied onto the claimed request, then both identities are linked: the
    // claim onto account 2 (its provider owner), the provider identity onto the session account.
    verify(claimedRequest).setDisplayName("OpenID User");
    verify(claimedRequest).setEmailAddress("openid-user@example.com");
    verify(accountManager).link(Account.id(2), claimedRequest);
    verify(accountManager).link(Account.id(1), areq);
  }

  /**
   * A token-less SIGN_IN callback is unaffected by the (LINK_IDENTIY-gated) guard: it authenticates
   * normally and never links.
   */
  @Test
  public void signInWithoutLinkStateIsNotRejected() throws Exception {
    when(req.getParameter("gerrit.mode")).thenReturn("SIGN_IN");
    when(accountManager.authenticate(any())).thenReturn(mock(AuthResult.class));

    openIdService.doAuth(req, rsp);

    verify(accountManager).authenticate(any());
    verify(accountManager, never()).link(any(Account.Id.class), any(AuthRequest.class));
  }

  /** LINK_IDENTIY discovery mints a link-state token and reflects it into the signed return_to. */
  @Test
  public void discoverMintsLinkStateForLinkMode() throws Exception {
    ArgumentCaptor<String> returnTo = stubAuthenticateCapturingReturnTo();

    @SuppressWarnings("unused")
    var unused =
        openIdService.discover(
            req, "http://provider.example/id", SignInMode.LINK_IDENTIY, false, null);

    assertThat(returnTo.getValue()).contains("gerrit.link_state=");
  }

  /** SIGN_IN discovery reflects no link-state token. */
  @Test
  public void discoverDoesNotMintLinkStateForSignIn() throws Exception {
    ArgumentCaptor<String> returnTo = stubAuthenticateCapturingReturnTo();

    @SuppressWarnings("unused")
    var unused =
        openIdService.discover(req, "http://provider.example/id", SignInMode.SIGN_IN, false, null);

    assertThat(returnTo.getValue()).doesNotContain("gerrit.link_state");
  }

  private ArgumentCaptor<String> stubAuthenticateCapturingReturnTo() throws Exception {
    org.openid4java.message.AuthRequest aReq = mock(org.openid4java.message.AuthRequest.class);
    when(aReq.getIdentity()).thenReturn(org.openid4java.message.AuthRequest.SELECT_ID);
    when(aReq.getDestinationUrl(false)).thenReturn("http://provider.example/authorize");
    when(aReq.getParameterMap()).thenReturn(Map.of());
    ArgumentCaptor<String> returnTo = ArgumentCaptor.forClass(String.class);
    when(consumerManager.authenticate(any(DiscoveryInformation.class), returnTo.capture()))
        .thenReturn(aReq);
    return returnTo;
  }
}
