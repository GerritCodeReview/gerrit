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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AuthTokenVerifierTest {
  private static final Account.Id ACCOUNT_ID = Account.id(1);
  private AuthTokenVerifier tokenVerifier;
  private LoadingCache<Account.Id, List<AuthToken>> cache;
  @Mock AuthTokenCache.Loader loader;

  @Before
  public void setUp() throws Exception {
    ImmutableList<AuthToken> tokens =
        ImmutableList.of(
            AuthToken.createWithPlainToken("id1", "hashedToken"),
            AuthToken.createWithPlainToken("id2", "another_Token"),
            AuthToken.createWithPlainToken(
                "id3", "tokenWithLifetime", Optional.of(Instant.now().plus(1, ChronoUnit.DAYS))),
            AuthToken.createWithPlainToken(
                "id4",
                "tokenWithExpiredLifetime",
                Optional.of(Instant.now().minus(1, ChronoUnit.DAYS))));
    doReturn(tokens).when(loader).load(ACCOUNT_ID);
    cache = CacheBuilder.newBuilder().build(loader);
    AuthTokenCache authTokenCache = new AuthTokenCache(cache);
    AuthTokenAccessor tokenAccessor = new CachingAuthTokenAccessor(authTokenCache, null);
    tokenVerifier = new AuthTokenVerifier(tokenAccessor);
  }

  @Test
  public void checkTokenWithoutLimitedLifetime() {
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "hashedToken")).isTrue();
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "another_Token")).isTrue();
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "invalid")).isFalse();
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "another_token")).isFalse();
  }

  @Test
  public void assertThatTokensSucceedAuthenticationWithinLifetime() {
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "tokenWithLifetime")).isTrue();
  }

  @Test
  public void assertThatExpiredTokensFailAuthentication() {
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "tokenWithExpiredLifetime")).isFalse();
  }
}
