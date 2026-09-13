// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.auth.oauth;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.io.IOException;

/* Contract that OAuth provider must implement */
@ExtensionPoint
public interface OAuthServiceProvider {

  /**
   * Returns the URL where you should redirect your users to authenticate your application.
   *
   * @return the OAuth service URL to redirect your users for authentication
   */
  default String getAuthorizationUrl() {
    return getAuthorizationInfo().getAuthorizationUrl();
  }

  /**
   * Returns the URL where you should redirect your users to authenticate your application.
   *
   * @return the OAuth service URL to redirect your users for authentication and verifier string
   *     generated during initial authorization phase
   */
  default OAuthAuthorizationInfo getAuthorizationInfo() {
    return new OAuthAuthorizationInfo(getAuthorizationUrl(), null);
  }

  /**
   * Retrieve the access token
   *
   * @param verifier verifier code
   * @return access token
   */
  default OAuthToken getAccessToken(OAuthVerifier verifier) {
    return getAccessToken(verifier, null);
  }

  /**
   * Retrieve the access token
   *
   * @param verifier verifier code
   * @param codeVerifier verifier string generated during initial authorization phase
   * @return access token
   */
  default OAuthToken getAccessToken(OAuthVerifier verifier, @Nullable String codeVerifier) {
    return getAccessToken(verifier);
  }

  /**
   * After establishing of secure communication channel, this method supposed to access the
   * protected resource and retrieve the user name.
   *
   * @return OAuth user information
   */
  OAuthUserInfo getUserInfo(OAuthToken token) throws IOException;

  /**
   * Whether this provider can refresh an expired access token (OAuth 2.0 refresh grant, RFC 6749
   * section 6). Defaults to {@code false}; a provider that requests and accepts refresh tokens
   * overrides this together with {@link #refresh}.
   *
   * @return whether {@link #refresh} is supported for this provider
   */
  default boolean supportsRefresh() {
    return false;
  }

  /**
   * Exchanges the refresh token carried in {@code expiredToken.getRaw()} for a fresh access token.
   *
   * @param expiredToken the previously issued token whose access token has expired
   * @return a new token, with {@code expiresAt} populated
   * @throws OAuthRevokedException when the IdP reports {@code invalid_grant} (the grant is gone;
   *     revoke the session); an {@link IOException} subtype
   * @throws IOException on a transient IdP/network failure (handle by policy, do not revoke)
   */
  default OAuthToken refresh(OAuthToken expiredToken) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Whether this provider can revoke a token at the identity provider (OAuth 2.0 token revocation,
   * RFC 7009). Defaults to {@code false}; a provider whose IdP exposes a revocation endpoint
   * overrides this together with {@link #revoke}.
   *
   * @return whether {@link #revoke} is supported for this provider
   */
  default boolean supportsRevoke() {
    return false;
  }

  /**
   * Revokes {@code token} at the identity provider so it can no longer be used there (RFC 7009):
   * POST the token to the provider's revocation endpoint (for Google, {@code
   * https://oauth2.googleapis.com/revoke}). An already-invalid token is a no-op. Unlike {@link
   * #refresh}, this does not throw {@link OAuthRevokedException}.
   *
   * @param token the token to revoke at the IdP
   * @throws IOException on a transient IdP/network failure
   */
  default void revoke(OAuthToken token) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the OAuth version of the service.
   *
   * @return oauth version as string
   */
  String getVersion();

  /**
   * Returns the name of this service. This name is presented to the user to choose between multiple
   * service providers
   *
   * @return name of the service
   */
  String getName();
}
