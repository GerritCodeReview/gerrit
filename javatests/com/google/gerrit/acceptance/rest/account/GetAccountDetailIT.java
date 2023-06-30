// Copyright (C) 2015 The Android Open Source Project
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
import static com.google.gerrit.acceptance.rest.account.AccountAssert.assertAccountInfo;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.AccountDetailInfo;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.account.AccountTagProvider;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.util.List;
import org.junit.Test;

public class GetAccountDetailIT extends AbstractDaemonTest {
  @Inject private GroupOperations groupOperations;
  @Inject private AccountOperations accountOperations;

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        bind(AccountTagProvider.class)
            .annotatedWith(Exports.named("CustomAccountTagProvider"))
            .to(CustomAccountTagProvider.class);
      }
    };
  }

  @Test
  public void getDetail() throws Exception {
    RestResponse r = adminRestSession.get("/accounts/" + admin.id().get() + "/detail/");
    AccountDetailInfo info = newGson().fromJson(r.getReader(), AccountDetailInfo.class);
    assertAccountInfo(admin, info);
    Account account = getAccount(admin.id());
    assertThat(info.registeredOn.getTime()).isEqualTo(account.registeredOn().toEpochMilli());
  }

  @Test
  public void getDetailForServiceUser() throws Exception {
    Account.Id serviceUser = accountOperations.newAccount().create();
    groupOperations
        .group(
            groupCache
                .get(AccountGroup.nameKey(ServiceUserClassifier.SERVICE_USERS))
                .get()
                .getGroupUUID())
        .forUpdate()
        .addMember(serviceUser)
        .update();
    RestResponse r = adminRestSession.get("/accounts/" + serviceUser.get() + "/detail/");
    AccountDetailInfo info = newGson().fromJson(r.getReader(), AccountDetailInfo.class);
    assertThat(info.tags).containsExactly(AccountInfo.Tags.SERVICE_USER);
  }

  @Test
  public void getDetailForExtensionPointAccountTag() throws Exception {
    RestResponse r = userRestSession.get("/accounts/" + user.id().get() + "/detail/");
    AccountDetailInfo info = newGson().fromJson(r.getReader(), AccountDetailInfo.class);
    assertThat(info.tags).containsExactly("BASIC_USER");
  }

  @Test
  public void searchForSecondaryEmailRequiresModifyAccountPermission() throws Exception {
    Account.Id id =
        accountOperations
            .newAccount()
            .preferredEmail("preferred@eexample.com")
            .addSecondaryEmail("secondary@example.com")
            .create();

    RestResponse r = userRestSession.get("/accounts/secondary@example.com/detail/");
    r.assertStatus(404);

    // The admin has MODIFY_ACCOUNT permission and can see the user.
    r = adminRestSession.get("/accounts/secondary@example.com/detail/");
    r.assertStatus(200);
    AccountDetailInfo info = newGson().fromJson(r.getReader(), AccountDetailInfo.class);
    assertThat(info._accountId).isEqualTo(id.get());

    r = adminRestSession.get("/accounts/secondary@example.com/detail/");
    r.assertStatus(200);
    info = newGson().fromJson(r.getReader(), AccountDetailInfo.class);
    assertThat(info._accountId).isEqualTo(id.get());
  }

  private static class CustomAccountTagProvider implements AccountTagProvider {
    private PermissionBackend permissions;

    @Inject
    public CustomAccountTagProvider(PermissionBackend permissions) {
      this.permissions = permissions;
    }

    @Override
    public List<String> getTags(Account.Id id) {
      try {
        if (!permissions.currentUser().test(GlobalPermission.ADMINISTRATE_SERVER)) {
          return ImmutableList.of("BASIC_USER");
        }
      } catch (Exception e) {
        throw new IllegalStateException("can't check admin permissions", e);
      }
      return ImmutableList.of();
    }
  }
}
