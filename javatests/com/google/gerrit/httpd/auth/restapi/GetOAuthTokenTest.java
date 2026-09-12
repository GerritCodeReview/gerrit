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

package com.google.gerrit.httpd.auth.restapi;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.auth.oauth.OAuthTokenCache;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.httpd.auth.restapi.GetOAuthToken.OAuthTokenInfo;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.util.Providers;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GetOAuthTokenTest {
  private static final Account.Id ACCOUNT = Account.id(1);

  @Mock private CurrentUser self;
  @Mock private IdentifiedUser user;
  @Mock private AccountResource rsrc;
  @Mock private OAuthTokenCache tokenCache;
  @Mock private AuthConfig authConfig;

  @Before
  public void setUp() {
    when(rsrc.getUser()).thenReturn(user);
    when(user.getAccountId()).thenReturn(ACCOUNT);
    when(self.hasSameAccountId(user)).thenReturn(true);
  }

  private GetOAuthToken command() {
    return new GetOAuthToken(
        Providers.of(self), tokenCache, Providers.of("https://gerrit.example.org/"), authConfig);
  }

  private static OAuthToken token(long expiresAt) {
    return new OAuthToken("at", "bearer", "{}", expiresAt, "p:x");
  }

  @Test
  public void refreshFilterEnabled_expiredToken_notFound_withoutEvicting() {
    when(authConfig.isOAuthTokenRefreshFilterEnabled()).thenReturn(true);
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(token(System.currentTimeMillis() - 1000));

    assertThrows(ResourceNotFoundException.class, () -> command().apply(rsrc));
    // The refresh_token in raw must survive for the filter to retry: no evicting get().
    verify(tokenCache, never()).getOrEvictIfExpired(ACCOUNT);
  }

  @Test
  public void refreshFilterEnabled_validToken_returnedWithoutEvicting() throws Exception {
    when(authConfig.isOAuthTokenRefreshFilterEnabled()).thenReturn(true);
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(token(Long.MAX_VALUE));
    when(user.getUserName()).thenReturn(Optional.of("jdoe"));

    Response<OAuthTokenInfo> res = command().apply(rsrc);

    assertThat(res.value().accessToken).isEqualTo("at");
    verify(tokenCache, never()).getOrEvictIfExpired(ACCOUNT);
  }

  @Test
  public void refreshFilterDisabled_usesEvictingGet() throws Exception {
    when(authConfig.isOAuthTokenRefreshFilterEnabled()).thenReturn(false);
    when(tokenCache.getOrEvictIfExpired(ACCOUNT)).thenReturn(token(Long.MAX_VALUE));
    when(user.getUserName()).thenReturn(Optional.of("jdoe"));

    Response<OAuthTokenInfo> unused = command().apply(rsrc);

    verify(tokenCache).getOrEvictIfExpired(ACCOUNT);
  }
}
