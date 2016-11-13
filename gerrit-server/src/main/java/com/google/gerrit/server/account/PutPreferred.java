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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;

@Singleton
public class PutPreferred implements RestModifyView<AccountResource.Email, Input> {
  static class Input {}

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;
  private final AccountCache byIdCache;

  @Inject
  PutPreferred(Provider<CurrentUser> self, Provider<ReviewDb> dbProvider, AccountCache byIdCache) {
    this.self = self;
    this.dbProvider = dbProvider;
    this.byIdCache = byIdCache;
  }

  @Override
  public Response<String> apply(AccountResource.Email rsrc, Input input)
      throws AuthException, ResourceNotFoundException, OrmException, IOException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("not allowed to set preferred email address");
    }
    return apply(rsrc.getUser(), rsrc.getEmail());
  }

  public Response<String> apply(IdentifiedUser user, String email)
      throws ResourceNotFoundException, OrmException, IOException {
    Account a = dbProvider.get().accounts().get(user.getAccountId());
    if (a == null) {
      throw new ResourceNotFoundException("account not found");
    }
    if (email.equals(a.getPreferredEmail())) {
      return Response.ok("");
    }
    a.setPreferredEmail(email);
    dbProvider.get().accounts().update(Collections.singleton(a));
    byIdCache.evict(a.getId());
    return Response.created("");
  }
}
