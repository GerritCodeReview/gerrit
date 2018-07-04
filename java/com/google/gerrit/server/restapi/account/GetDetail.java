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

package com.google.gerrit.server.restapi.account;

import com.google.common.collect.Sets;
import com.google.gerrit.extensions.common.AccountDetailInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.InternalAccountDirectory;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Singleton
public class GetDetail implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final InternalAccountDirectory directory;

  @Inject
  public GetDetail(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      InternalAccountDirectory directory) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.directory = directory;
  }

  @Override
  public AccountDetailInfo apply(AccountResource rsrc)
      throws OrmException, PermissionBackendException {
    Account a = rsrc.getUser().getAccount();
    AccountDetailInfo info = new AccountDetailInfo(a.getId().get());
    info.registeredOn = a.getRegisteredOn();
    info.inactive = !a.isActive() ? true : null;
    Set<FillOptions> fillOptions;
    if (self.get().hasSameAccountId(rsrc.getUser())) {
      fillOptions = EnumSet.allOf(FillOptions.class);
    } else {
      try {
        permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
        fillOptions = EnumSet.allOf(FillOptions.class);
      } catch (AuthException e) {
        fillOptions =
            Sets.difference(
                EnumSet.allOf(FillOptions.class), EnumSet.of(FillOptions.SECONDARY_EMAILS));
      }
    }
    directory.fillAccountInfo(Collections.singleton(info), fillOptions);
    return info;
  }
}
