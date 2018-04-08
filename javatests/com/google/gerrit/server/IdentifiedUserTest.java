// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server;

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.FakeRealm;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.DisableReverseDnsLookup;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.FakeAccountCache;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ConfigSuite.class)
public class IdentifiedUserTest {
  @ConfigSuite.Parameter public Config config;

  private IdentifiedUser identifiedUser;

  @Inject private IdentifiedUser.GenericFactory identifiedUserFactory;

  private static final String[] TEST_CASES = {
    "",
    "FirstName.LastName@Corporation.com",
    "!#$%&'+-/=.?^`{|}~@[IPv6:0123:4567:89AB:CDEF:0123:4567:89AB:CDEF]",
  };

  @Before
  public void setUp() throws Exception {
    final FakeAccountCache accountCache = new FakeAccountCache();
    final Realm mockRealm =
        new FakeRealm() {
          HashSet<String> emails = new HashSet<>(Arrays.asList(TEST_CASES));

          @Override
          public boolean hasEmailAddress(IdentifiedUser who, String email) {
            return emails.contains(email);
          }

          @Override
          public Set<String> getEmailAddresses(IdentifiedUser who) {
            return emails;
          }
        };

    AbstractModule mod =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Boolean.class)
                .annotatedWith(DisableReverseDnsLookup.class)
                .toInstance(Boolean.FALSE);
            bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(config);
            bind(String.class)
                .annotatedWith(AnonymousCowardName.class)
                .toProvider(AnonymousCowardNameProvider.class);
            bind(String.class)
                .annotatedWith(CanonicalWebUrl.class)
                .toInstance("http://localhost:8080/");
            bind(AccountCache.class).toInstance(accountCache);
            bind(GroupBackend.class).to(SystemGroupBackend.class).in(SINGLETON);
            bind(Realm.class).toInstance(mockRealm);
          }
        };

    Injector injector = Guice.createInjector(mod);
    injector.injectMembers(this);

    Account account = new Account(new Account.Id(1), TimeUtil.nowTs());
    Account.Id ownerId = account.getId();

    identifiedUser = identifiedUserFactory.create(ownerId);

    /* Trigger identifiedUser to load the email addresses from mockRealm */
    identifiedUser.getEmailAddresses();
  }

  @Test
  public void emailsExistence() {
    assertThat(identifiedUser.hasEmailAddress(TEST_CASES[0])).isTrue();
    assertThat(identifiedUser.hasEmailAddress(TEST_CASES[1].toLowerCase())).isTrue();
    assertThat(identifiedUser.hasEmailAddress(TEST_CASES[1])).isTrue();
    assertThat(identifiedUser.hasEmailAddress(TEST_CASES[1].toUpperCase())).isTrue();
    /* assert again to test cached email address by IdentifiedUser.validEmails */
    assertThat(identifiedUser.hasEmailAddress(TEST_CASES[1])).isTrue();

    assertThat(identifiedUser.hasEmailAddress(TEST_CASES[2])).isTrue();
    assertThat(identifiedUser.hasEmailAddress(TEST_CASES[2].toLowerCase())).isTrue();

    assertThat(identifiedUser.hasEmailAddress("non-exist@email.com")).isFalse();
    /* assert again to test cached email address by IdentifiedUser.invalidEmails */
    assertThat(identifiedUser.hasEmailAddress("non-exist@email.com")).isFalse();
  }
}
