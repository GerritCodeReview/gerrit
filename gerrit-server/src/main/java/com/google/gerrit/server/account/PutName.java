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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.PutName.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;

@Singleton
public class PutName implements RestModifyView<AccountResource, Input> {
  public static class Input {
    @DefaultInput public String name;
  }

  private final Provider<CurrentUser> self;
  private final Realm realm;
  private final Provider<ReviewDb> dbProvider;
  private final AccountCache byIdCache;

  @Inject
  PutName(
      Provider<CurrentUser> self,
      Realm realm,
      Provider<ReviewDb> dbProvider,
      AccountCache byIdCache) {
    this.self = self;
    this.realm = realm;
    this.dbProvider = dbProvider;
    this.byIdCache = byIdCache;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, Input input)
      throws AuthException, MethodNotAllowedException, ResourceNotFoundException, OrmException,
          IOException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("not allowed to change name");
    }
    return apply(rsrc.getUser(), input);
  }

  public Response<String> apply(IdentifiedUser user, Input input)
      throws MethodNotAllowedException, ResourceNotFoundException, OrmException, IOException {
    if (input == null) {
      input = new Input();
    }

    if (!realm.allowsEdit(AccountFieldName.FULL_NAME)) {
      throw new MethodNotAllowedException("realm does not allow editing name");
    }

    Account a = dbProvider.get().accounts().get(user.getAccountId());
    if (a == null) {
      throw new ResourceNotFoundException("account not found");
    }
    a.setFullName(input.name);
    dbProvider.get().accounts().update(Collections.singleton(a));
    byIdCache.evict(a.getId());
    return Strings.isNullOrEmpty(a.getFullName())
        ? Response.<String>none()
        : Response.ok(a.getFullName());
  }
}
