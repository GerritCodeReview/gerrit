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
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static org.mockito.Mockito.doReturn;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AuthTokenCacheTest {
  private static final Account.Id ACCOUNT_ID = Account.id(1);

  private AuthTokenCacheImpl.Loader cacheLoader;

  @Mock private AccountCache accountCache;

  @Mock private VersionedAuthTokens.Accessor tokenAccessor;

  @Before
  public void setUp() throws Exception {
    Account account =
        Account.builder(ACCOUNT_ID, Instant.EPOCH)
            .setFullName("foo bar")
            .setDisplayName("foo")
            .setActive(false)
            .setMetaId("dead..beef")
            .setUniqueTag("dead..beef..tag")
            .setStatus("OOO")
            .setPreferredEmail("foo@bar.tld")
            .build();
    doReturn(
            AccountState.forAccount(
                account,
                List.of(
                    ExternalId.create(
                        ExternalId.Key.create(SCHEME_USERNAME, "foo", false),
                        ACCOUNT_ID,
                        null,
                        HashedPassword.fromPassword("secret").encode(),
                        null))))
        .when(accountCache)
        .getEvenIfMissing(ACCOUNT_ID);
    cacheLoader = new AuthTokenCacheImpl.Loader(accountCache, tokenAccessor);
  }

  @Test
  public void loadTokenFromExternalId() throws Exception {
    doReturn(List.of()).when(tokenAccessor).getTokens(ACCOUNT_ID);
    Iterable<AuthTokenCacheEntry> tokens = cacheLoader.load(ACCOUNT_ID);
    assertThat(tokens.iterator().next().checkToken("secret")).isTrue();
  }

  @Test
  public void loadTokenFromUserRef() throws Exception {
    List<AuthToken> tokensInCache =
        List.of(
            AuthToken.createWithPlainToken("id1", "password"),
            AuthToken.createWithPlainToken("id2", "1234abcd"));
    doReturn(tokensInCache).when(tokenAccessor).getTokens(ACCOUNT_ID);
    ImmutableList<AuthTokenCacheEntry> tokens = ImmutableList.copyOf(cacheLoader.load(ACCOUNT_ID));

    assertToken(tokens, "secret", false);
    assertToken(tokens, "password", true);
    assertToken(tokens, "1234abcd", true);
  }

  private void assertToken(
      ImmutableList<AuthTokenCacheEntry> tokens, String providedToken, boolean expected) {
    boolean isTokenValid = false;
    for (AuthTokenCacheEntry token : tokens) {
      if (token.checkToken(providedToken)) {
        isTokenValid = true;
        break;
      }
    }
    assertThat(isTokenValid).isEqualTo(expected);
  }
}
