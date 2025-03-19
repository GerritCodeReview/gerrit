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
import com.google.gerrit.extensions.auth.AuthTokenInput;
import com.google.gerrit.server.account.AuthTokenAccessor;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class TokenIT extends AbstractDaemonTest {
  @Inject AuthTokenAccessor tokenAccessor;

  private AuthTokenInput authTokenInput;

  @Before
  public void setup() throws Exception {
    addUserTokens();
    addAdminTokens();

    String id = "testToken";
    authTokenInput = new AuthTokenInput();
    authTokenInput.id = id;
  }

  @Test
  public void assertGenerateOwnTokenSucceeds() throws Exception {
    RestResponse resp =
        userRestSession.put(
            String.format("/accounts/self/tokens/%s", authTokenInput.id), authTokenInput);
    resp.assertCreated();

    JsonObject createdToken = JsonParser.parseReader(resp.getReader()).getAsJsonObject();
    assertThat(createdToken.get("id").getAsString()).isEqualTo(authTokenInput.id);
    assertThat(createdToken.get("token").getAsString()).isNotNull();

    assertThat(tokenAccessor.getToken(user.id(), authTokenInput.id)).isPresent();
  }

  @Test
  public void assertCreateTokenForOtherUserFailsForNonAdmins() throws Exception {
    userRestSession
        .put(
            String.format("/accounts/%d/tokens/%s", admin.id().get(), authTokenInput.id),
            authTokenInput)
        .assertForbidden();
  }

  @Test
  public void assertCreateTokenForOtherUserSucceedsForAdmins() throws Exception {
    adminRestSession
        .put(
            String.format("/accounts/%d/tokens/%s", user.id().get(), authTokenInput.id),
            authTokenInput)
        .assertCreated();
  }

  @Test
  public void assertSetSpecificTokenFailsForNonAdmins() throws Exception {
    authTokenInput.token = "secret";
    userRestSession
        .put(
            String.format("/accounts/%d/tokens/%s", user.id().get(), authTokenInput.id),
            authTokenInput)
        .assertForbidden();
  }

  @Test
  public void assertSetSpecificTokenSucceedsForAdmins() throws Exception {
    authTokenInput.token = "secret";
    RestResponse resp =
        adminRestSession.put(
            String.format("/accounts/%d/tokens/%s", user.id().get(), authTokenInput.id),
            authTokenInput);

    resp.assertCreated();

    JsonObject createdToken = JsonParser.parseReader(resp.getReader()).getAsJsonObject();
    assertThat(createdToken.get("id").getAsString()).isEqualTo(authTokenInput.id);
    assertThat(createdToken.get("token").getAsString()).isEqualTo(authTokenInput.token);

    assertThat(tokenAccessor.getToken(user.id(), authTokenInput.id)).isPresent();
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
    assertThat(tokenAccessor.getToken(user.id(), "userToken1")).isEmpty();
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

  @Test
  public void assertCreateTokensWithLifetimeSucceeds() throws Exception {
    for (String lifetime : List.of("5min", "1h", "1d", "1mon", "3y")) {
      authTokenInput.lifetime = lifetime;
      authTokenInput.id = String.format("testToken_%s", lifetime);
      RestResponse resp =
          userRestSession.put(
              String.format("/accounts/self/tokens/%s", authTokenInput.id), authTokenInput);
      resp.assertCreated();

      JsonObject createdToken = JsonParser.parseReader(resp.getReader()).getAsJsonObject();
      assertThat(createdToken.get("id").getAsString()).isEqualTo(authTokenInput.id);
      assertThat(createdToken.get("token").getAsString()).isNotNull();
      assertThat(
              TimeUnit.NANOSECONDS.toMinutes(
                  Math.abs(
                      Timestamp.valueOf(createdToken.get("expiration").getAsString())
                          .toInstant()
                          .compareTo(
                              Instant.now()
                                  .plusSeconds(
                                      ConfigUtil.getTimeUnit(lifetime, 0, TimeUnit.SECONDS))))))
          .isLessThan(1L);

      assertThat(tokenAccessor.getToken(user.id(), authTokenInput.id)).isPresent();
    }
  }

  @Test
  public void assertInvalidLifetimeFormatReturnsBadRequest() throws Exception {
    authTokenInput.lifetime = "1invalid";
    RestResponse resp =
        userRestSession.put(
            String.format("/accounts/self/tokens/%s", authTokenInput.id), authTokenInput);
    resp.assertBadRequest();
  }

  private void addUserTokens() throws Exception {
    @SuppressWarnings("unused")
    var unused =
        tokenAccessor.addPlainToken(user.id(), "userToken1", "http-pass", Optional.empty());
  }

  private void addAdminTokens() throws Exception {
    @SuppressWarnings("unused")
    var unused =
        tokenAccessor.addPlainToken(admin.id(), "adminToken1", "http-pass", Optional.empty());
  }
}
