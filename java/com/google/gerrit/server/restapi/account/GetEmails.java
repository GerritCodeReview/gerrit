// Copyright (C) 2013 The Android Open Source Project
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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;

@Singleton
public class GetEmails implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;

  @Inject
  GetEmails(Provider<CurrentUser> self, PermissionBackend permissionBackend) {
    this.self = self;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public List<EmailInfo> apply(AccountResource rsrc)
      throws AuthException, PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return rsrc.getUser()
        .getEmailAddresses()
        .stream()
        .filter(Objects::nonNull)
        .map(e -> toEmailInfo(rsrc, e))
        .sorted(comparing((EmailInfo e) -> e.email))
        .collect(toList());
  }

  private static EmailInfo toEmailInfo(AccountResource rsrc, String email) {
    EmailInfo e = new EmailInfo();
    e.email = email;
    e.preferred(rsrc.getUser().getAccount().getPreferredEmail());
    return e;
  }
}
