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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import java.time.Instant;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AuthTokenCacheTest {
  private static final Account.Id ACCOUNT_ID = Account.id(1);
  private static final String PWD = "secret";
  private static final Account ACCOUNT =
      Account.builder(ACCOUNT_ID, Instant.EPOCH)
          .setFullName("foo bar")
          .setDisplayName("foo")
          .setActive(false)
          .setMetaId("dead..beef")
          .setUniqueTag("dead..beef..tag")
          .setStatus("OOO")
          .setPreferredEmail("foo@bar.tld")
          .build();

  private AuthTokenCacheImpl.Loader cacheLoader;

  @Mock private AccountCache accountCache;
  @Mock private VersionedAuthTokens.Factory authTokenFactory;
  @Mock private VersionedAuthTokens versionedAuthTokens;

  @Before
  public void setUp() throws Exception {
    doReturn(versionedAuthTokens).when(versionedAuthTokens).load();
    doReturn(versionedAuthTokens).when(authTokenFactory).create(ACCOUNT_ID);
    cacheLoader = new AuthTokenCacheImpl.Loader(accountCache, authTokenFactory);
  }

  @Test
  public void loadTokenFromAccount() throws Exception {
    doReturn(Optional.of(AccountState.forAccount(ACCOUNT))).when(accountCache).get(ACCOUNT_ID);
    doReturn(ImmutableList.of(AuthToken.createWithPlainToken("testtoken", PWD)))
        .when(versionedAuthTokens)
        .getTokens();
    ImmutableList<AuthToken> tokens = cacheLoader.load(ACCOUNT_ID);
    assertThat(HashedPassword.decode(tokens.get(0).hashedToken()).checkPassword(PWD)).isTrue();
  }
}
