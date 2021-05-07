// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.inject.Inject;
import org.junit.Test;

public class DeleteAccountIT extends AbstractDaemonTest {
  @Inject private ExternalIds externalIds;

  @Test
  public void deleteAccountRestApi() throws Exception {
    TestAccount testAccount = accountCreator.create("user1");
    assertThat(accountCache.getByUsername(testAccount.username()).isPresent()).isTrue();
    RestResponse r = adminRestSession.delete("/accounts/" + testAccount.username());
    r.assertNoContent();
    assertThat(accountCache.getByUsername(testAccount.username()).isEmpty()).isTrue();
    assertThat(accounts.get(testAccount.id()).get().account().isActive()).isFalse();
    assertThat(externalIds.byAccount(testAccount.id()).isEmpty()).isTrue();
  }

  @Test
  public void deleteAccountRestApi_notAllowedByNonAdmin() throws Exception {
    TestAccount testAccount = accountCreator.create("user2");
    assertThat(accountCache.getByUsername(testAccount.username()).isPresent()).isTrue();
    RestResponse r = userRestSession.delete("/accounts/" + testAccount.username());
    r.assertForbidden();
    assertThat(accountCache.getByUsername(testAccount.username()).isPresent()).isTrue();
    assertThat(accounts.get(testAccount.id()).get().account().isActive()).isTrue();
    assertThat(externalIds.byAccount(testAccount.id()).isEmpty()).isFalse();
  }

  @Test
  public void deleteAccountRestApi_notAllowedToDeleteOwnAccount() throws Exception {
    assertThat(accountCache.getByUsername(admin.username()).isPresent()).isTrue();
    RestResponse r = adminRestSession.delete("/accounts/self");
    r.assertConflict();
    RestResponse r2 = adminRestSession.delete("/accounts/admin");
    r2.assertConflict();
    assertThat(accountCache.getByUsername(admin.username()).isPresent()).isTrue();
    assertThat(accounts.get(admin.id()).get().account().isActive()).isTrue();
    assertThat(externalIds.byAccount(admin.id()).isEmpty()).isFalse();
  }
}
