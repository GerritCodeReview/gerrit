// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.auth.ldap;

import static java.util.concurrent.TimeUnit.HOURS;

import com.google.common.base.Optional;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;

import java.util.Set;

public class LdapModule extends CacheModule {
  static final String USERNAME_CACHE = "ldap_usernames";
  static final String GROUP_CACHE = "ldap_groups";

  @Override
  protected void configure() {
    cache(GROUP_CACHE,
        String.class,
        new TypeLiteral<Set<AccountGroup.UUID>>() {})
      .maxAge(1, HOURS)
      .populateWith(LdapRealm.MemberLoader.class);

    cache(USERNAME_CACHE,
        String.class,
        new TypeLiteral<Optional<Account.Id>>() {})
      .populateWith(LdapRealm.UserLoader.class);

    bind(Realm.class).to(LdapRealm.class).in(Scopes.SINGLETON);
    bind(Helper.class);
  }
}
