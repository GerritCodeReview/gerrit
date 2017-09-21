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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import java.util.Optional;
import java.util.Set;

public class LdapModule extends CacheModule {
  static final String USERNAME_CACHE = "ldap_usernames";
  static final String GROUP_CACHE = "ldap_groups";
  static final String GROUP_EXIST_CACHE = "ldap_group_existence";
  static final String PARENT_GROUPS_CACHE = "ldap_groups_byinclude";

  @Override
  protected void configure() {
    cache(GROUP_CACHE, String.class, new TypeLiteral<Set<AccountGroup.UUID>>() {})
        .expireAfterWrite(1, HOURS)
        .loader(LdapRealm.MemberLoader.class);

    cache(USERNAME_CACHE, String.class, new TypeLiteral<Optional<Account.Id>>() {})
        .loader(LdapRealm.UserLoader.class);

    cache(GROUP_EXIST_CACHE, String.class, new TypeLiteral<Boolean>() {})
        .expireAfterWrite(1, HOURS)
        .loader(LdapRealm.ExistenceLoader.class);

    cache(PARENT_GROUPS_CACHE, String.class, new TypeLiteral<ImmutableSet<String>>() {})
        .expireAfterWrite(1, HOURS);

    bind(Helper.class);
    bind(Realm.class).to(LdapRealm.class).in(Scopes.SINGLETON);

    DynamicSet.bind(binder(), GroupBackend.class).to(LdapGroupBackend.class);
  }
}
