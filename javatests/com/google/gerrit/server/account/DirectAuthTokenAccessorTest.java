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
import com.google.common.collect.ImmutableSet;
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
public class DirectAuthTokenAccessorTest {
  private static final Account.Id ACCOUNT_ID = Account.id(1);
  private Account account;
  @Mock private VersionedAuthTokens versionedAuthTokens;
  @Mock private VersionedAuthTokens.Factory authTokenFactory;
  @Mock private AccountCache accountCache;
  private HttpPasswordFallbackAuthTokenAccessor tokenAccessor;
  private ImmutableList<AuthToken> tokens;

  @Before
  public void setUp() throws Exception {
    tokenAccessor =
        new HttpPasswordFallbackAuthTokenAccessor(
            accountCache, new DirectAuthTokenAccessor(null, authTokenFactory, null, null));
    tokens =
        ImmutableList.of(
            AuthToken.createWithPlainToken("id1", "hashedToken"),
            AuthToken.createWithPlainToken("id2", "another_Token"));
    doReturn(versionedAuthTokens).when(authTokenFactory).create(ACCOUNT_ID);
    doReturn(versionedAuthTokens).when(versionedAuthTokens).load();
    doReturn(tokens).when(versionedAuthTokens).getTokens();
    account =
        Account.builder(ACCOUNT_ID, Instant.EPOCH)
            .setFullName("foo bar")
            .setDisplayName("foo")
            .setActive(true)
            .setMetaId("dead..beef")
            .setUniqueTag("dead..beef..tag")
            .setStatus("OOO")
            .setPreferredEmail("foo@bar.tld")
            .build();
    ;
    doReturn(
            AccountState.forAccount(
                account,
                ImmutableSet.of(
                    ExternalId.create(
                        ExternalId.Key.create(ExternalId.SCHEME_USERNAME, "foo", false),
                        ACCOUNT_ID,
                        null,
                        "secret",
                        null))))
        .when(accountCache)
        .getEvenIfMissing(ACCOUNT_ID);
  }

  @Test
  public void getTokensReturnsAuthTokens() {
    assertThat(tokenAccessor.getTokens(ACCOUNT_ID)).containsExactlyElementsIn(tokens);
  }

  @Test
  public void getTokensReturnsHttpPasswordIfNoAuthTokenExists() throws Exception {
    doReturn(ImmutableList.of()).when(versionedAuthTokens).getTokens();
    List<AuthToken> result = tokenAccessor.getTokens(ACCOUNT_ID);
    assertThat(result).hasSize(1);
    assertThat(result.get(0))
        .isEqualTo(AuthToken.create(DirectAuthTokenAccessor.LEGACY_ID, "secret"));
  }

  @Test
  public void getTokensReturnsEmptyListIfNeitherTokensOrPasswordExists() throws Exception {
    doReturn(ImmutableList.of()).when(versionedAuthTokens).getTokens();
    doReturn(AccountState.forAccount(account)).when(accountCache).getEvenIfMissing(ACCOUNT_ID);
    List<AuthToken> result = tokenAccessor.getTokens(ACCOUNT_ID);
    assertThat(result).hasSize(0);
  }
}
