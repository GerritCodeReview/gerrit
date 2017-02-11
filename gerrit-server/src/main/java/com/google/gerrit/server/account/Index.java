//Copyright (C) 2016 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.Index.Input;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class Index implements RestModifyView<AccountResource, Input> {
  public static class Input {}

  private final AccountCache accountCache;
  private final Provider<CurrentUser> self;

  @Inject
  Index(AccountCache accountCache, Provider<CurrentUser> self) {
    this.accountCache = accountCache;
    this.self = self;
  }

  @Override
  public Response<?> apply(AccountResource rsrc, Input input) throws IOException, AuthException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("not allowed to index account");
    }

    // evicting the account from the cache, reindexes the account
    accountCache.evict(rsrc.getUser().getAccountId());
    return Response.none();
  }
}
