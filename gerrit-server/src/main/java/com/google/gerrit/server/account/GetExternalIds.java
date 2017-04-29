// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.AccountExternalIdInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Singleton
public class GetExternalIds implements RestReadView<AccountResource> {
  private final PermissionBackend permissionBackend;
  private final ExternalIds externalIds;
  private final Provider<CurrentUser> self;
  private final AuthConfig authConfig;

  @Inject
  GetExternalIds(
      PermissionBackend permissionBackend,
      ExternalIds externalIds,
      Provider<CurrentUser> self,
      AuthConfig authConfig) {
    this.permissionBackend = permissionBackend;
    this.externalIds = externalIds;
    this.self = self;
    this.authConfig = authConfig;
  }

  @Override
  public List<AccountExternalIdInfo> apply(AccountResource resource)
      throws RestApiException, IOException, OrmException {
    if (self.get() != resource.getUser()
        && !permissionBackend.user(self).testOrFalse(GlobalPermission.ACCESS_DATABASE)) {
      throw new AuthException("not allowed to get external IDs");
    }

    Collection<ExternalId> ids = externalIds.byAccount(resource.getUser().getAccountId());
    if (ids.isEmpty()) {
      return ImmutableList.of();
    }
    List<AccountExternalIdInfo> result = Lists.newArrayListWithCapacity(ids.size());
    for (ExternalId id : ids) {
      AccountExternalIdInfo info = new AccountExternalIdInfo();
      info.identity = id.key().get();
      info.emailAddress = id.email();
      info.trusted = toBoolean(authConfig.isIdentityTrustable(Collections.singleton(id)));
      // The identity can be deleted only if its not the one used to
      // establish this web session, and if only if an identity was
      // actually used to establish this web session.
      if (!id.isScheme(SCHEME_USERNAME)) {
        ExternalId.Key last = resource.getUser().getLastLoginExternalIdKey();
        info.canDelete = toBoolean(last == null || !last.get().equals(info.identity));
      }
      result.add(info);
    }
    return result;
  }

  private static Boolean toBoolean(boolean v) {
    return v ? v : null;
  }
}
