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

import autovalue.shaded.com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.common.TokenInput;
import com.google.gerrit.server.account.AuthToken;
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

    assertThat(tokenAccessor.getToken(user.id(), tokenInput.id)).isPresent();
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

    assertThat(tokenAccessor.getToken(user.id(), tokenInput.id)).isPresent();
  }

  @Test
  public void assertListTokensSucceeds() throws Exception {
    RestResponse resp = userRestSession.get(String.format("/accounts/%d/tokens", user.id().get()));
    resp.assertOK();

    JsonArray json = JsonParser.parseReader(resp.getReader()).getAsJsonArray();
    assertThat(json.size()).isEqualTo(2);
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
      tokenInput.lifetime = lifetime;
      tokenInput.id = String.format("testToken_%s", lifetime);
      RestResponse resp =
          userRestSession.put(String.format("/accounts/self/tokens/%s", tokenInput.id), tokenInput);
      resp.assertCreated();

      JsonObject createdToken = JsonParser.parseReader(resp.getReader()).getAsJsonObject();
      assertThat(createdToken.get("id").getAsString()).isEqualTo(tokenInput.id);
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

      assertThat(tokenAccessor.getToken(user.id(), tokenInput.id)).isPresent();
    }
  }

  @Test
  public void assertInvalidLifetimeFormatReturnsBadRequest() throws Exception {
    tokenInput.lifetime = "1invalid";
    RestResponse resp =
        userRestSession.put(String.format("/accounts/self/tokens/%s", tokenInput.id), tokenInput);
    resp.assertBadRequest();
  }

  @Test
  @GerritConfig(name = "auth.maxAuthTokensPerAccount", value = "3")
  public void assertCreatingMoreTokensThanAllowedFails() throws Exception {
    RestResponse resp =
        userRestSession.put(String.format("/accounts/self/tokens/%s", tokenInput.id), tokenInput);
    resp.assertCreated();

    TokenInput tokenInput2 = new TokenInput();
    tokenInput2.id = "testToken2";
    resp =
        userRestSession.put(String.format("/accounts/self/tokens/%s", tokenInput2.id), tokenInput2);
    resp.assertBadRequest();

    resp = userRestSession.delete(String.format("/accounts/self/tokens/%s", tokenInput.id));
    resp.assertNoContent();

    resp =
        userRestSession.put(String.format("/accounts/self/tokens/%s", tokenInput2.id), tokenInput2);
    resp.assertCreated();
  }

  @CanIgnoreReturnValue
  private AuthToken addUserTokens() throws Exception {
    return tokenAccessor.addPlainToken(user.id(), "userToken1", "http-pass", Optional.empty());
  }

  @CanIgnoreReturnValue
  private AuthToken addAdminTokens() throws Exception {
    return tokenAccessor.addPlainToken(admin.id(), "adminToken1", "http-pass", Optional.empty());
  }
}
