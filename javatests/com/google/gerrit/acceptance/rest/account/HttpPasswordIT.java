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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.HttpPasswordInput;
import com.google.gerrit.server.account.VersionedAuthorizationTokens;
import com.google.gerrit.server.restapi.account.PutHttpPassword;
import com.google.inject.Inject;
import org.junit.Test;

public class HttpPasswordIT extends AbstractDaemonTest {
  @Inject VersionedAuthorizationTokens.Accessor tokenAccessor;

  @Test
  public void assertGenerateOwnPasswordSucceeds() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.generate = true;
    RestResponse resp = userRestSession.put("/accounts/self/password.http", passwordInput);
    resp.assertCreated();

    String returnedToken =
        Iterables.get(Splitter.onPattern(System.lineSeparator()).split(resp.getEntityContent()), 1);
    assertThat(returnedToken.substring(1, returnedToken.length() - 1)).isNotEmpty();

    assertThat(tokenAccessor.getToken(user.id(), PutHttpPassword.LEGACY_ID)).isNotNull();
  }

  @Test
  public void assertCreatePasswordForOtherUserFailsForNonAdmins() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.generate = true;
    userRestSession
        .put(String.format("/accounts/%d/password.http", admin.id().get()), passwordInput)
        .assertForbidden();
  }

  @Test
  public void assertCreatePasswordForOtherUserSucceedsForAdmins() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.generate = true;
    adminRestSession
        .put(String.format("/accounts/%d/password.http", user.id().get()), passwordInput)
        .assertCreated();
  }

  @Test
  public void assertSetSpecificTokenFailsForNonAdmins() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.httpPassword = "secret";
    userRestSession
        .put(String.format("/accounts/%d/password.http", user.id().get()), passwordInput)
        .assertForbidden();
  }

  @Test
  public void assertSetSpecificTokenSucceedsForAdmins() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.httpPassword = "secret";
    RestResponse resp =
        adminRestSession.put(
            String.format("/accounts/%d/password.http", user.id().get()), passwordInput);

    resp.assertCreated();

    String returnedToken =
        Iterables.get(Splitter.onPattern(System.lineSeparator()).split(resp.getEntityContent()), 1);
    assertThat(returnedToken.substring(1, returnedToken.length() - 1))
        .isEqualTo(passwordInput.httpPassword);

    assertThat(tokenAccessor.getToken(user.id(), PutHttpPassword.LEGACY_ID)).isNotNull();
  }

  @Test
  public void assertDeletePasswordSucceeds() throws Exception {
    userRestSession
        .delete(String.format("/accounts/%d/password.http", user.id().get()))
        .assertNoContent();
    assertThat(tokenAccessor.getToken(user.id(), PutHttpPassword.LEGACY_ID)).isNull();
  }

  @Test
  public void assertDeleteTokenForOtherUserSucceedsForAdmins() throws Exception {
    adminRestSession
        .delete(String.format("/accounts/%d/password.http", user.id().get()))
        .assertNoContent();
    assertThat(tokenAccessor.getToken(user.id(), PutHttpPassword.LEGACY_ID)).isNull();
  }

  @Test
  public void assertDeleteTokenForOtherUserFailsForNonAdmins() throws Exception {
    userRestSession
        .delete(String.format("/accounts/%d/tokens/adminToken1", admin.id().get()))
        .assertForbidden();
  }
}
