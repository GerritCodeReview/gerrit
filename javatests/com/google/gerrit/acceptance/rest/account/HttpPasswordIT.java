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
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.common.HttpPasswordInput;
import com.google.gerrit.server.account.AuthTokenAccessor;
import com.google.gerrit.server.account.PasswordMigrator;
import com.google.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;

public class HttpPasswordIT extends AbstractDaemonTest {
  @Inject AuthTokenAccessor tokenAccessor;

  TestAccount testUser;
  RestSession restSession;

  @Before
  public void setUp() throws Exception {
    testUser = accountCreator.create(RandomStringUtils.randomAlphabetic(10));
    restSession = server.createRestSession(testUser);
  }

  @Test
  public void generateOwnPasswordSucceeds() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.generate = true;
    RestResponse resp = restSession.put("/accounts/self/password.http", passwordInput);
    resp.assertCreated();

    String returnedToken =
        Iterables.get(Splitter.onPattern(System.lineSeparator()).split(resp.getEntityContent()), 1);
    assertThat(returnedToken.substring(1, returnedToken.length() - 1)).isNotEmpty();

    assertThat(tokenAccessor.getToken(testUser.id(), PasswordMigrator.DEFAULT_ID)).isPresent();
  }

  @Test
  public void createPasswordForOtherUserFailsForNonAdmins() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.generate = true;
    restSession
        .put(String.format("/accounts/%d/password.http", admin.id().get()), passwordInput)
        .assertForbidden();
  }

  @Test
  public void createPasswordForOtherUserSucceedsForAdmins() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.generate = true;
    adminRestSession
        .put(String.format("/accounts/%d/password.http", testUser.id().get()), passwordInput)
        .assertCreated();
  }

  @Test
  public void setSpecificTokenFailsForNonAdmins() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.httpPassword = "secret";
    restSession
        .put(String.format("/accounts/%d/password.http", testUser.id().get()), passwordInput)
        .assertForbidden();
  }

  @Test
  public void setSpecificTokenSucceedsForAdmins() throws Exception {
    HttpPasswordInput passwordInput = new HttpPasswordInput();
    passwordInput.httpPassword = "secret";
    RestResponse resp =
        adminRestSession.put(
            String.format("/accounts/%d/password.http", testUser.id().get()), passwordInput);

    resp.assertCreated();

    String returnedToken =
        Iterables.get(Splitter.onPattern(System.lineSeparator()).split(resp.getEntityContent()), 1);
    assertThat(returnedToken.substring(1, returnedToken.length() - 1))
        .isEqualTo(passwordInput.httpPassword);

    assertThat(tokenAccessor.getToken(testUser.id(), PasswordMigrator.DEFAULT_ID)).isPresent();
  }

  @Test
  public void deletePasswordSucceeds() throws Exception {
    restSession
        .delete(String.format("/accounts/%d/password.http", testUser.id().get()))
        .assertNoContent();
    assertThat(tokenAccessor.getToken(testUser.id(), PasswordMigrator.DEFAULT_ID)).isEmpty();
  }

  @Test
  public void deleteTokenForOtherUserSucceedsForAdmins() throws Exception {
    adminRestSession
        .delete(String.format("/accounts/%d/password.http", testUser.id().get()))
        .assertNoContent();
    assertThat(tokenAccessor.getToken(testUser.id(), PasswordMigrator.DEFAULT_ID)).isEmpty();
  }

  @Test
  public void deleteTokenForOtherUserFailsForNonAdmins() throws Exception {
    restSession
        .delete(String.format("/accounts/%d/tokens/adminToken1", admin.id().get()))
        .assertForbidden();
  }
}
