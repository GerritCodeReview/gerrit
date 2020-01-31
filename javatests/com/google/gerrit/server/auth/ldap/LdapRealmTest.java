// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.auth.ldap.LdapModule.GROUP_CACHE;
import static com.google.gerrit.server.auth.ldap.LdapModule.GROUP_EXIST_CACHE;
import static com.google.gerrit.server.auth.ldap.LdapModule.PARENT_GROUPS_CACHE;
import static com.google.gerrit.server.auth.ldap.LdapModule.USERNAME_CACHE;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public final class LdapRealmTest {
  @Inject private LdapRealm ldapRealm = null;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector =
        Guice.createInjector(
            new InMemoryModule(),
            new CacheModule() {
              @Override
              protected void configure() {
                cache(GROUP_CACHE, String.class, new TypeLiteral<Set<AccountGroup.UUID>>() {})
                    .loader(LdapRealm.MemberLoader.class);
                cache(USERNAME_CACHE, String.class, new TypeLiteral<Optional<Account.Id>>() {})
                    .loader(LdapRealm.UserLoader.class);
                cache(GROUP_EXIST_CACHE, String.class, new TypeLiteral<Boolean>() {})
                    .loader(LdapRealm.ExistenceLoader.class);
                cache(
                    PARENT_GROUPS_CACHE, String.class, new TypeLiteral<ImmutableSet<String>>() {});
              }
            });
    injector.injectMembers(this);
  }

  private ExternalId id(String scheme, String id) {
    return ExternalId.create(scheme, id, Account.id(1000));
  }

  private boolean accountBelongsToRealm(ExternalId... ids) {
    return ldapRealm.accountBelongsToRealm(Arrays.asList(ids));
  }

  private boolean accountBelongsToRealm(String scheme, String id) {
    return accountBelongsToRealm(id(scheme, id));
  }

  @Test
  public void accountBelongsToRealm() throws Exception {
    assertThat(accountBelongsToRealm(SCHEME_GERRIT, "test")).isTrue();
    assertThat(accountBelongsToRealm(id(SCHEME_USERNAME, "test"), id(SCHEME_GERRIT, "test")))
        .isTrue();
    assertThat(accountBelongsToRealm(id(SCHEME_GERRIT, "test"), id(SCHEME_USERNAME, "test")))
        .isTrue();

    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "test")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "foo@bar.com")).isFalse();

    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "gerrit")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "xxgerritxx")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "gerrit.foo@bar.com")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "bar.gerrit@bar.com")).isFalse();
  }
}
