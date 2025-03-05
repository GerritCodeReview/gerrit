// Copyright (C) 2018 The Android Open Source Project
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
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.auth.AuthTokenInput;
import com.google.gerrit.server.account.AuthTokenAccessor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

public class CreateAccountIT extends AbstractDaemonTest {
  @Inject AuthTokenAccessor tokenAccessor;

  @Test
  public void createAccountRestApi() throws Exception {
    AccountInput input = new AccountInput();
    input.username = "foo";
    assertThat(accountCache.getByUsername(input.username)).isEmpty();
    RestResponse r = adminRestSession.put("/accounts/" + input.username, input);
    r.assertCreated();
    assertThat(accountCache.getByUsername(input.username)).isPresent();
  }

  @Test
  public void createAccountWithTokenAsAdminSucceeds() throws Exception {
    AccountInput input = new AccountInput();
    input.username = "foo";
    AuthTokenInput token = new AuthTokenInput();
    token.id = "test";
    token.token = "secret";
    input.tokens = List.of(token);
    RestResponse r = adminRestSession.put("/accounts/" + input.username, input);
    r.assertCreated();

    JsonObject json = JsonParser.parseReader(r.getReader()).getAsJsonObject();
    assertThat(tokenAccessor.getToken(Account.id(json.get("_account_id").getAsInt()), token.id))
        .isNotNull();
  }

  @Test
  @GerritConfig(name = "auth.userNameToLowerCase", value = "false")
  public void createAccountRestApiUserNameToLowerCaseFalse() throws Exception {
    AccountInput input = new AccountInput();
    input.username = "JohnDoe";
    assertThat(accountCache.getByUsername(input.username)).isEmpty();
    RestResponse r = adminRestSession.put("/accounts/" + input.username, input);
    r.assertCreated();
    assertThat(accountCache.getByUsername(input.username)).isPresent();
  }

  @Test
  @GerritConfig(name = "auth.userNameToLowerCase", value = "true")
  public void createAccountRestApiUserNameToLowerCaseTrue() throws Exception {
    testUserNameToLowerCase("John1", "John1", "john1");
    assertThat(accountCache.getByUsername("John1")).isEmpty();

    testUserNameToLowerCase("john2", "John2", "john2");
    assertThat(accountCache.getByUsername("John2")).isEmpty();

    testUserNameToLowerCase("John3", "john3", "john3");
    assertThat(accountCache.getByUsername("John3")).isEmpty();

    testUserNameToLowerCase("John4", "johN4", "john4");
    assertThat(accountCache.getByUsername("John4")).isEmpty();
    assertThat(accountCache.getByUsername("johN4")).isEmpty();

    testUserNameToLowerCase("john5", "john5", "john5");
  }

  private void testUserNameToLowerCase(String usernameUrl, String usernameInput, String usernameDb)
      throws Exception {
    AccountInput input = new AccountInput();
    input.username = usernameInput;
    assertThat(accountCache.getByUsername(usernameDb)).isEmpty();
    RestResponse r = adminRestSession.put("/accounts/" + usernameUrl, input);
    r.assertCreated();
    assertThat(accountCache.getByUsername(usernameDb)).isPresent();
  }
}
