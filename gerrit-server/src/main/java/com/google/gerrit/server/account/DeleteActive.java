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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.DeleteActive.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;

@RequiresCapability(GlobalCapability.MODIFY_ACCOUNT)
@Singleton
public class DeleteActive implements RestModifyView<AccountResource, Input> {
  public static class Input {}

  private final Provider<ReviewDb> dbProvider;
  private final AccountCache byIdCache;
  private final Provider<IdentifiedUser> self;

  @Inject
  DeleteActive(
      Provider<ReviewDb> dbProvider, AccountCache byIdCache, Provider<IdentifiedUser> self) {
    this.dbProvider = dbProvider;
    this.byIdCache = byIdCache;
    this.self = self;
  }

  @Override
  public Response<?> apply(AccountResource rsrc, Input input)
      throws RestApiException, OrmException, IOException {
    Account a = dbProvider.get().accounts().get(rsrc.getUser().getAccountId());
    if (a == null) {
      throw new ResourceNotFoundException("account not found");
    }
    if (!a.isActive()) {
      throw new ResourceConflictException("account not active");
    }
    if (self.get() == rsrc.getUser()) {
      throw new ResourceConflictException("cannot deactivate own account");
    }
    a.setActive(false);
    dbProvider.get().accounts().update(Collections.singleton(a));
    byIdCache.evict(a.getId());
    return Response.none();
  }
}
