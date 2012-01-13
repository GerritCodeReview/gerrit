// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.auth.internal;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;

public class InternalModule extends CacheModule {

  static final String USERNAME_CACHE = "internal_usernames";

  @Override
  protected void configure() {
    final TypeLiteral<Cache<String, Account.Id>> usernames =
        new TypeLiteral<Cache<String, Account.Id>>() {};
    core(usernames, USERNAME_CACHE) //
        .populateWith(InternalRealm.UserLoader.class);

    bind(Realm.class).to(InternalRealm.class).in(Scopes.SINGLETON);
  }

}
