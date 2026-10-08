// Copyright (C) 2026 The Android Open Source Project
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
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

@NoHttpd
@UseSsh
public class SetSshKeyExpiryIT extends StandaloneSiteTest {
  private static final String USERNAME = "foo";
  private static final String KEY =
      "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQCgug5VyMXQGnem2H1KVC4/HcRcD4zzBqS"
          + "uJBRWVonSSoz3RoAZ7bWXCVVGwchtXwUURD689wFYdiPecOrWOUgeeyRq754YWRhU+W28"
          + "vf8IZixgjCmiBhaL2gt3wff6pP+NXJpTSA4aeWE5DfNK5tZlxlSxqkKOS8JRSUeNQov5T"
          + "w== john.doe@example.com";

  @Test
  public void keyWithoutExpiryGetsExpiry() throws Exception {
    initSite();
    Account.Id accountId = createAccountWithKey(Optional.empty());

    Instant before = Instant.now();
    runGerrit("SetSshKeyExpiry", "-d", sitePaths.site_path.toString(), "--lifetime", "1d");

    Optional<Instant> expiry = getOnlyKey(accountId).expirationDate();
    assertThat(expiry).isPresent();
    assertThat(expiry.get()).isAtLeast(before.plus(Duration.ofDays(1)).minusSeconds(1));
    assertThat(expiry.get()).isAtMost(Instant.now().plus(Duration.ofDays(1)));
  }

  @Test
  public void keyWithExpiryIsNotChanged() throws Exception {
    initSite();
    Instant expiry = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Account.Id accountId = createAccountWithKey(Optional.of(expiry));

    runGerrit("SetSshKeyExpiry", "-d", sitePaths.site_path.toString(), "--lifetime", "1d");

    assertThat(getOnlyKey(accountId).expirationDate()).hasValue(expiry);
  }

  @Test
  public void invalidKeyIsNotChanged() throws Exception {
    initSite();
    Account.Id accountId = createAccountWithKey(Optional.empty());
    try (ServerContext ctx = startServer()) {
      ctx.getInjector()
          .getInstance(VersionedAuthorizedKeys.Accessor.class)
          .markKeyInvalid(accountId, 1);
    }

    runGerrit("SetSshKeyExpiry", "-d", sitePaths.site_path.toString(), "--lifetime", "1d");

    AccountSshKey key = getOnlyKey(accountId);
    assertThat(key.valid()).isFalse();
    assertThat(key.expirationDate()).isEmpty();
  }

  private void initSite() throws Exception {
    runGerrit("init", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
  }

  private Account.Id createAccountWithKey(Optional<Instant> expiry) throws Exception {
    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      Account.Id accountId = Account.id(gApi.accounts().create(USERNAME).detail()._accountId);
      ctx.getInjector()
          .getInstance(VersionedAuthorizedKeys.Accessor.class)
          .addKey(accountId, KEY, expiry);
      return accountId;
    }
  }

  private AccountSshKey getOnlyKey(Account.Id accountId) throws Exception {
    try (ServerContext ctx = startServer()) {
      List<AccountSshKey> keys =
          ctx.getInjector().getInstance(VersionedAuthorizedKeys.Accessor.class).getKeys(accountId);
      assertThat(keys).hasSize(1);
      return keys.get(0);
    }
  }
}
