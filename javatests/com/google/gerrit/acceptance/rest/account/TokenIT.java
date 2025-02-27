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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.TokenInput;
import com.google.gerrit.server.account.VersionedAuthTokens;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class TokenIT extends AbstractDaemonTest {
  @Inject VersionedAuthTokens.Accessor tokenAccessor;

  private TokenInput tokenInput;

  @Before
  public void setup() throws Exception {
    addUserTokens();
    addAdminTokens();

    String id = "testToken";
    tokenInput = new TokenInput();
    tokenInput.id = id;
  }

  @Test
  public void assertGenerateOwnTokenSucceeds() throws Exception {
    RestResponse resp =
        userRestSession.put(String.format("/accounts/self/tokens/%s", tokenInput.id), tokenInput);
    resp.assertCreated();

    JsonObject createdToken = JsonParser.parseReader(resp.getReader()).getAsJsonObject();
    assertThat(createdToken.get("id").getAsString()).isEqualTo(tokenInput.id);
    assertThat(createdToken.get("token").getAsString()).isNotNull();

    assertThat(tokenAccessor.getToken(user.id(), tokenInput.id)).isNotNull();
  }

  @Test
  public void assertCreateTokenForOtherUserFailsForNonAdmins() throws Exception {
    userRestSession
        .put(String.format("/accounts/%d/tokens/%s", admin.id().get(), tokenInput.id), tokenInput)
        .assertForbidden();
  }

  @Test
  public void assertCreateTokenForOtherUserSucceedsForAdmins() throws Exception {
    adminRestSession
        .put(String.format("/accounts/%d/tokens/%s", user.id().get(), tokenInput.id), tokenInput)
        .assertCreated();
  }

  @Test
  public void assertSetSpecificTokenFailsForNonAdmins() throws Exception {
    tokenInput.token = "secret";
    userRestSession
        .put(String.format("/accounts/%d/tokens/%s", user.id().get(), tokenInput.id), tokenInput)
        .assertForbidden();
  }

  @Test
  public void assertSetSpecificTokenSucceedsForAdmins() throws Exception {
    tokenInput.token = "secret";
    RestResponse resp =
        adminRestSession.put(
            String.format("/accounts/%d/tokens/%s", user.id().get(), tokenInput.id), tokenInput);

    resp.assertCreated();

    JsonObject createdToken = JsonParser.parseReader(resp.getReader()).getAsJsonObject();
    assertThat(createdToken.get("id").getAsString()).isEqualTo(tokenInput.id);
    assertThat(createdToken.get("token").getAsString()).isEqualTo(tokenInput.token);

    assertThat(tokenAccessor.getToken(user.id(), tokenInput.id)).isNotNull();
  }

  @Test
  public void assertListTokensSucceeds() throws Exception {
    RestResponse resp = userRestSession.get(String.format("/accounts/%d/tokens", user.id().get()));
    resp.assertOK();

    JsonArray json = JsonParser.parseReader(resp.getReader()).getAsJsonArray();
    assertThat(json.size()).isEqualTo(1);
    assertThat(json.get(0).getAsJsonObject().get("id").getAsString()).isEqualTo("userToken1");
  }

  @Test
  public void assertListTokensForOtherUserSucceedsForAdmins() throws Exception {
    adminRestSession.get(String.format("/accounts/%d/tokens", user.id().get())).assertOK();
  }

  @Test
  public void assertListTokensForOtherUserFailsForNonAdmins() throws Exception {
    userRestSession.get(String.format("/accounts/%d/tokens", admin.id().get())).assertForbidden();
  }

  @Test
  public void assertDeleteTokenSucceeds() throws Exception {
    userRestSession
        .delete(String.format("/accounts/%d/tokens/userToken1", user.id().get()))
        .assertNoContent();
    assertThat(tokenAccessor.getToken(user.id(), "userToken1")).isNull();
  }

  @Test
  public void assertDeleteTokenForOtherUserSucceedsForAdmins() throws Exception {
    adminRestSession
        .delete(String.format("/accounts/%d/tokens/userToken1", user.id().get()))
        .assertNoContent();
  }

  @Test
  public void assertDeleteTokenForOtherUserFailsForNonAdmins() throws Exception {
    userRestSession
        .delete(String.format("/accounts/%d/tokens/adminToken1", admin.id().get()))
        .assertForbidden();
  }

  private void addUserTokens() throws Exception {
    tokenAccessor.addPlainToken(user.id(), "userToken1", "http-pass");
  }

  private void addAdminTokens() throws Exception {
    tokenAccessor.addPlainToken(admin.id(), "adminToken1", "http-pass");
  }
}
