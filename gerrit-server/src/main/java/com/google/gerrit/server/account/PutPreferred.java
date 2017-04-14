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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.PutPreferred.Input;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class PutPreferred implements RestModifyView<AccountResource.Email, Input> {
  static class Input {}

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;
  private final PermissionBackend permissionBackend;
  private final AccountCache byIdCache;

  @Inject
  PutPreferred(
      Provider<CurrentUser> self,
      Provider<ReviewDb> dbProvider,
      PermissionBackend permissionBackend,
      AccountCache byIdCache) {
    this.self = self;
    this.dbProvider = dbProvider;
    this.permissionBackend = permissionBackend;
    this.byIdCache = byIdCache;
  }

  @Override
  public Response<String> apply(AccountResource.Email rsrc, Input input)
      throws AuthException, ResourceNotFoundException, OrmException, IOException,
          PermissionBackendException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.user(self).check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return apply(rsrc.getUser(), rsrc.getEmail());
  }

  public Response<String> apply(IdentifiedUser user, String email)
      throws ResourceNotFoundException, OrmException, IOException {
    AtomicBoolean alreadyPreferred = new AtomicBoolean(false);
    Account a =
        dbProvider
            .get()
            .accounts()
            .atomicUpdate(
                user.getAccountId(),
                new AtomicUpdate<Account>() {
                  @Override
                  public Account update(Account a) {
                    if (email.equals(a.getPreferredEmail())) {
                      alreadyPreferred.set(true);
                    } else {
                      a.setPreferredEmail(email);
                    }
                    return a;
                  }
                });
    if (a == null) {
      throw new ResourceNotFoundException("account not found");
    }
    byIdCache.evict(a.getId());
    return alreadyPreferred.get() ? Response.ok("") : Response.created("");
  }
}
