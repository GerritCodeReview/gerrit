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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AuthTokenVerifierTest {
  private static final Account.Id ACCOUNT_ID = Account.id(1);
  private AuthTokenVerifier tokenVerifier;
  @Mock private DirectAuthTokenAccessor tokenAccessor;

  @Before
  public void setUp() throws Exception {
    tokenVerifier = new AuthTokenVerifier(tokenAccessor);
    ImmutableList<AuthToken> tokens =
        ImmutableList.of(
            AuthToken.createWithPlainToken("id1", "hashedToken"),
            AuthToken.createWithPlainToken("id2", "another_Token"));
    doReturn(tokens).when(tokenAccessor).getTokens(ACCOUNT_ID);
  }

  @Test
  public void checkToken() {
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "hashedToken")).isTrue();
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "another_Token")).isTrue();
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "invalid")).isFalse();
    assertThat(tokenVerifier.checkToken(ACCOUNT_ID, "another_token")).isFalse();
  }
}
