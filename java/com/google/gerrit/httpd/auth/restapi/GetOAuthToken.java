// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.restapi;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.auth.oauth.OAuthTokenCache;
import com.google.gerrit.auth.oauth.OAuthTokenRefresher;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthRevokedException;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;

@Singleton
public class GetOAuthToken implements RestReadView<AccountResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String BEARER_TYPE = "bearer";

  private final Provider<CurrentUser> self;
  private final OAuthTokenCache tokenCache;
  private final OAuthTokenRefresher refresher;
  private final Provider<String> canonicalWebUrlProvider;

  @Inject
  GetOAuthToken(
      Provider<CurrentUser> self,
      OAuthTokenCache tokenCache,
      OAuthTokenRefresher refresher,
      @CanonicalWebUrl Provider<String> urlProvider) {
    this.self = self;
    this.tokenCache = tokenCache;
    this.refresher = refresher;
    this.canonicalWebUrlProvider = urlProvider;
  }

  @Override
  public Response<OAuthTokenInfo> apply(AccountResource rsrc)
      throws AuthException, ResourceNotFoundException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      throw new AuthException("not allowed to get access token");
    }
    Account.Id id = rsrc.getUser().getAccountId();
    try {
      // Refresh on read: renew an expired token in place (RFC 6749 section 6) before returning it.
      refresher.refreshIfExpired(id);
    } catch (OAuthRevokedException e) {
      logger.atFine().withCause(e).log("OAuth grant revoked for account %s", id);
      throw new ResourceNotFoundException();
    }
    // Read without evicting (getEvenIfExpired) so a token that could not be refreshed keeps its
    // refresh_token; treat a still-expired token as absent.
    OAuthToken accessToken = tokenCache.getEvenIfExpired(id);
    if (accessToken == null || accessToken.isExpired()) {
      throw new ResourceNotFoundException();
    }
    OAuthTokenInfo accessTokenInfo = new OAuthTokenInfo();
    accessTokenInfo.username = rsrc.getUser().getUserName().orElse(null);
    accessTokenInfo.resourceHost = getHostName(canonicalWebUrlProvider.get());
    accessTokenInfo.accessToken = accessToken.getToken();
    accessTokenInfo.providerId = accessToken.getProviderId();
    accessTokenInfo.expiresAt = Long.toString(accessToken.getExpiresAt());
    accessTokenInfo.type = BEARER_TYPE;
    return Response.ok(accessTokenInfo);
  }

  @Nullable
  private static String getHostName(String canonicalWebUrl) {
    if (canonicalWebUrl == null) {
      logger.atSevere().log(
          "No canonicalWebUrl defined in gerrit.config, OAuth may not work properly");
      return null;
    }

    try {
      return new URI(canonicalWebUrl).getHost();
    } catch (URISyntaxException e) {
      logger.atSevere().withCause(e).log("Invalid canonicalWebUrl '%s'", canonicalWebUrl);
      return null;
    }
  }

  public static class OAuthTokenInfo {
    public String username;
    public String resourceHost;
    public String accessToken;
    public String providerId;
    public String expiresAt;
    public String type;
  }
}
