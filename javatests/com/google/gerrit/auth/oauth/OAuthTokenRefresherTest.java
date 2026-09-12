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

package com.google.gerrit.auth.oauth;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthRevokedException;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthTokenEncrypter;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OAuthTokenRefresherTest {
  private static final String PLUGIN = "gerrit-oauth-provider";
  private static final String EXPORT = "google-oauth";
  private static final String PROVIDER_ID = PLUGIN + ":" + EXPORT;
  private static final Account.Id ACCOUNT = Account.id(1);

  @Mock private OAuthTokenCache tokenCache;
  @Mock private DynamicMap<OAuthServiceProvider> providers;
  @Mock private OAuthServiceProvider provider;

  private OAuthTokenRefresher refresher;

  @Before
  public void setUp() {
    refresher = newRefresher(new Config());
    lenient().when(providers.get(PLUGIN, EXPORT)).thenReturn(provider);
    lenient().when(provider.supportsRefresh()).thenReturn(true);
  }

  @After
  public void tearDown() {
    TimeUtil.resetCurrentMillisSupplier();
  }

  private static OAuthToken token(long expiresAt, String providerId) {
    return new OAuthToken("at", "bearer", "{}", expiresAt, providerId);
  }

  private static OAuthToken expired() {
    return token(System.currentTimeMillis() - 1000, PROVIDER_ID);
  }

  /** Marks a cached, expired token that the hot-path peek will report as expired. */
  private void cachedExpired() {
    when(tokenCache.hasExpiredToken(ACCOUNT)).thenReturn(true);
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(expired());
  }

  @Test
  public void expiredRefreshableToken_isReplaced() throws Exception {
    OAuthToken refreshed = token(System.currentTimeMillis() + 3_600_000, PROVIDER_ID);
    cachedExpired();
    when(provider.refresh(any())).thenReturn(refreshed);

    refresher.refreshIfExpired(ACCOUNT);

    verify(tokenCache).put(ACCOUNT, refreshed);
  }

  @Test
  public void notExpiredOrAbsent_doesNothing_withoutDecrypting() throws Exception {
    when(tokenCache.hasExpiredToken(ACCOUNT)).thenReturn(false);

    refresher.refreshIfExpired(ACCOUNT);

    // Hot path must not decrypt or refresh when nothing is expired.
    verify(tokenCache, never()).getEvenIfExpired(any());
    verify(provider, never()).refresh(any());
    verify(tokenCache, never()).put(any(), any());
  }

  @Test
  public void invalidGrant_removesToken_andThrows() throws Exception {
    cachedExpired();
    when(provider.refresh(any())).thenThrow(new OAuthRevokedException("grant gone"));

    assertThrows(OAuthRevokedException.class, () -> refresher.refreshIfExpired(ACCOUNT));
    verify(tokenCache).remove(ACCOUNT);
    verify(tokenCache, never()).put(any(), any());
  }

  @Test
  public void transientIOException_noCacheMutation() throws Exception {
    cachedExpired();
    when(provider.refresh(any())).thenThrow(new IOException("timeout"));

    refresher.refreshIfExpired(ACCOUNT);

    verify(tokenCache, never()).put(any(), any());
    verify(tokenCache, never()).remove(any());
  }

  @Test
  public void transientFailure_backsOff_skipsRetryUntilInterval() throws Exception {
    cachedExpired();
    when(provider.refresh(any())).thenThrow(new IOException("idp down"));

    refresher.refreshIfExpired(ACCOUNT);
    refresher.refreshIfExpired(ACCOUNT);

    // First call attempts refresh and fails; the second is backed off within the interval, so
    // refresh runs only once across both reads.
    verify(provider, times(1)).refresh(any());
  }

  @Test
  public void refreshInterval_withoutUnit_isParsedAsMinutes() {
    Config config = new Config();
    config.setString("auth", null, "oauthTokenRefreshInterval", "2");

    assertThat(OAuthTokenRefresher.getRefreshIntervalMillis(config))
        .isEqualTo(TimeUnit.MINUTES.toMillis(2));
  }

  @Test
  public void transientFailure_usesExponentialBackoffUntilConfiguredInterval() throws Exception {
    AtomicLong nowMs = new AtomicLong(1_000);
    TimeUtil.setCurrentMillisSupplier(nowMs::get);
    Config config = new Config();
    config.setString("auth", null, "oauthTokenRefreshInterval", "2 seconds");
    refresher = newRefresher(config);
    cachedExpired();
    when(provider.refresh(any())).thenThrow(new IOException("idp down"));

    refresher.refreshIfExpired(ACCOUNT); // Fails; backs off for 1 second.
    refresher.refreshIfExpired(ACCOUNT);
    nowMs.addAndGet(1_000);

    refresher.refreshIfExpired(ACCOUNT); // Fails; backs off for 2 seconds.
    nowMs.addAndGet(1_000);
    refresher.refreshIfExpired(ACCOUNT);
    nowMs.addAndGet(1_000);

    refresher.refreshIfExpired(ACCOUNT);

    verify(provider, times(3)).refresh(any());
  }

  @Test
  public void unexpectedRuntimeException_noCacheMutation() throws Exception {
    cachedExpired();
    when(provider.refresh(any())).thenThrow(new RuntimeException("bug"));

    refresher.refreshIfExpired(ACCOUNT);

    verify(tokenCache, never()).put(any(), any());
  }

  @Test
  public void nullProviderId_noRefresh() throws Exception {
    when(tokenCache.hasExpiredToken(ACCOUNT)).thenReturn(true);
    when(tokenCache.getEvenIfExpired(ACCOUNT))
        .thenReturn(token(System.currentTimeMillis() - 1000, null));

    refresher.refreshIfExpired(ACCOUNT);

    verify(provider, never()).refresh(any());
  }

  @Test
  public void providerDoesNotSupportRefresh_noRefresh() throws Exception {
    cachedExpired();
    when(provider.supportsRefresh()).thenReturn(false);

    refresher.refreshIfExpired(ACCOUNT);

    verify(provider, never()).refresh(any());
    verify(tokenCache, never()).put(any(), any());
  }

  private OAuthTokenRefresher newRefresher(Config config) {
    return new OAuthTokenRefresher(
        tokenCache, providers, DynamicItem.itemOf(OAuthTokenEncrypter.class, null), config);
  }
}
