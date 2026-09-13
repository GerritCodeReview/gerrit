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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.auth.oauth.OAuthTokenRevoker.Result;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.auth.oauth.OAuthTokenRevokedListener;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OAuthTokenRevokerTest {
  private static final String PLUGIN = "oauth-plugin";
  private static final String EXPORT = "google-oauth";
  private static final String PROVIDER_ID = PLUGIN + ":" + EXPORT;
  private static final Account.Id ACCOUNT = Account.id(1);

  @Mock private OAuthTokenCache tokenCache;
  @Mock private DynamicMap<OAuthServiceProvider> providers;
  @Mock private OAuthServiceProvider provider;
  @Mock private PluginSetContext<OAuthTokenRevokedListener> revokedListeners;

  private OAuthTokenRevoker revoker;

  @Before
  public void setUp() {
    revoker = new OAuthTokenRevoker(tokenCache, providers, revokedListeners);
    lenient().when(providers.get(PLUGIN, EXPORT)).thenReturn(provider);
    lenient().when(provider.supportsRevoke()).thenReturn(true);
  }

  private static OAuthToken token(String providerId) {
    return new OAuthToken("at", "bearer", "{}", 0, providerId);
  }

  @Test
  public void revoke_providerSupportsRevoke_revokedAndEvicted() throws Exception {
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(token(PROVIDER_ID));

    Result result = revoker.revoke(ACCOUNT);

    assertThat(result).isEqualTo(Result.REVOKED);
    verify(provider).revoke(any());
    verify(tokenCache).remove(ACCOUNT);
    verify(revokedListeners).runEach(any());
  }

  @Test
  public void revoke_noToken_isNoOp() {
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(null);

    Result result = revoker.revoke(ACCOUNT);

    assertThat(result).isEqualTo(Result.NO_TOKEN);
    verify(tokenCache, never()).remove(any());
    verify(revokedListeners, never()).runEach(any());
  }

  @Test
  public void revoke_providerDoesNotSupportRevoke_evictedOnly() {
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(token(PROVIDER_ID));
    when(provider.supportsRevoke()).thenReturn(false);

    Result result = revoker.revoke(ACCOUNT);

    assertThat(result).isEqualTo(Result.EVICTED_ONLY);
    verify(tokenCache).remove(ACCOUNT);
    verify(revokedListeners).runEach(any());
  }

  @Test
  public void revoke_idpFailure_evictedOnly_stillEvictsAndFires() throws Exception {
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(token(PROVIDER_ID));
    doThrow(new IOException("idp down")).when(provider).revoke(any());

    Result result = revoker.revoke(ACCOUNT);

    assertThat(result).isEqualTo(Result.EVICTED_ONLY);
    verify(tokenCache).remove(ACCOUNT);
    verify(revokedListeners).runEach(any());
  }

  @Test
  public void revoke_nullProviderId_evictedOnly() {
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(token(null));

    Result result = revoker.revoke(ACCOUNT);

    assertThat(result).isEqualTo(Result.EVICTED_ONLY);
    verify(tokenCache).remove(ACCOUNT);
  }

  @Test
  public void revoke_supportsRevokeThrows_stillEvictsAndFires() throws Exception {
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(token(PROVIDER_ID));
    when(provider.supportsRevoke()).thenThrow(new RuntimeException("provider broken"));

    Result result = revoker.revoke(ACCOUNT);

    assertThat(result).isEqualTo(Result.EVICTED_ONLY);
    verify(tokenCache).remove(ACCOUNT); // a broken provider must not block local eviction
    verify(revokedListeners).runEach(any());
  }

  @Test
  public void revoke_undecryptableEntry_stillEvictsAndFires() throws Exception {
    when(tokenCache.getEvenIfExpired(ACCOUNT))
        .thenThrow(new IllegalStateException("wrong key or tampered"));

    Result result = revoker.revoke(ACCOUNT);

    assertThat(result).isEqualTo(Result.EVICTED_ONLY);
    verify(tokenCache).remove(ACCOUNT); // purge locally even though it could not be decrypted
    verify(revokedListeners).runEach(any());
    verify(provider, never()).revoke(any()); // cannot revoke upstream without the token
  }

  @Test
  public void revokeAll_undecryptableEntry_doesNotAbort_stillPurges() {
    Account.Id other = Account.id(2);
    when(tokenCache.accountsWithCachedToken()).thenReturn(ImmutableSet.of(ACCOUNT, other));
    when(tokenCache.getEvenIfExpired(ACCOUNT))
        .thenThrow(new IllegalStateException("wrong key or tampered"));
    when(tokenCache.getEvenIfExpired(other)).thenReturn(token(PROVIDER_ID));

    int processed = revoker.revokeAll();

    assertThat(processed).isEqualTo(2);
    verify(tokenCache).remove(ACCOUNT); // undecryptable entry still evicted, loop not aborted
    verify(tokenCache).remove(other);
    verify(tokenCache).removeAll(); // post-loop purge still runs
    verify(revokedListeners, times(2)).runEach(any()); // both global flushes still fire
  }

  @Test
  public void revokeAll_bracketsGlobalFlush_evictsEach_andPurges() {
    when(tokenCache.accountsWithCachedToken()).thenReturn(ImmutableSet.of(ACCOUNT));
    when(tokenCache.getEvenIfExpired(ACCOUNT)).thenReturn(token(PROVIDER_ID));

    int processed = revoker.revokeAll();

    assertThat(processed).isEqualTo(1);
    verify(tokenCache).remove(ACCOUNT); // per-account evict
    verify(tokenCache).removeAll(); // disk-only stragglers
    // onAllTokensRevoked fired before AND after the loop; the per-account callback is suppressed.
    verify(revokedListeners, times(2)).runEach(any());
  }
}
