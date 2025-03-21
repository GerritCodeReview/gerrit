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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.auth.AuthTokenInfo;
import com.google.gerrit.extensions.auth.AuthTokenInput;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.Test;

@NoHttpd
public class ReduceMaxTokenLifetimeIT extends StandaloneSiteTest {
  private static final String USERNAME = "foo";
  private static final String TOKEN_ID = "id";

  @Test
  public void tokenWithNoLifetimeGetsLifetime() throws Exception {
    initSite();
    Account.Id accountId = createAccountWithToken(null);

    runGerrit("ReduceMaxTokenLifetime", "-d", sitePaths.site_path.toString(), "--lifetime", "1d");

    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      List<AuthTokenInfo> actual = gApi.accounts().id(accountId.get()).getTokens();
      assertThat(actual.get(0).id).isEqualTo(TOKEN_ID);
      assertThat(actual.get(0).expiration).isNotNull();
    }
  }

  @Test
  public void tokenWithTooLongLifetimeGetsAdaptedLifetime() throws Exception {
    initSite();
    Account.Id accountId = createAccountWithToken("2d");

    runGerrit("ReduceMaxTokenLifetime", "-d", sitePaths.site_path.toString(), "--lifetime", "1d");

    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      List<AuthTokenInfo> actual = gApi.accounts().id(accountId.get()).getTokens();
      assertThat(actual.get(0).id).isEqualTo(TOKEN_ID);
      assertThat(actual.get(0).expiration)
          .isLessThan(Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
    }
  }

  @Test
  public void tokenWithValidLifetimeDontGetAdapted() throws Exception {
    initSite();
    Account.Id accountId = createAccountWithToken("2h");

    runGerrit("ReduceMaxTokenLifetime", "-d", sitePaths.site_path.toString(), "--lifetime", "1d");

    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      List<AuthTokenInfo> actual = gApi.accounts().id(accountId.get()).getTokens();
      assertThat(actual.get(0).id).isEqualTo(TOKEN_ID);
      assertThat(actual.get(0).expiration)
          .isLessThan(Timestamp.from(Instant.now().plus(2, ChronoUnit.HOURS)));
    }
  }

  private void initSite() throws Exception {
    runGerrit("init", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
  }

  private Account.Id createAccountWithToken(@Nullable String tokenLifetime) throws Exception {
    Account.Id accountId;
    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      accountId = Account.id(gApi.accounts().create(USERNAME).detail()._accountId);

      AuthTokenInput input = new AuthTokenInput();
      input.id = TOKEN_ID;
      if (tokenLifetime != null) {
        input.lifetime = tokenLifetime;
      }
      gApi.accounts().id(accountId.get()).createToken(input);
    }
    return accountId;
  }
}
