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

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.*;
import com.google.gerrit.server.config.*;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.FakeAccountCache;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.util.Providers;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.google.inject.Scopes.SINGLETON;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(ConfigSuite.class)
public class IdentifiedUserTest{

  private static final Logger log =
    LoggerFactory.getLogger(IdentifiedUserTest.class);

  @ConfigSuite.Parameter
  public Config config;

  @ConfigSuite.Config
  @GerritServerConfig
  public static Config noteDbEnabled() {
    return NotesMigration.allEnabledConfig();
  }

  private Injector injector;
  private Account.Id ownerId;
  private IdentifiedUser identifiedUser;

  @Inject private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Before
  public void setUp() throws Exception {
    final FakeAccountCache accountCache = new FakeAccountCache();
    final Realm mockRealm = new FakeRealm() {
      HashSet<String> emails = new HashSet<String>(Arrays.asList(
        "",
        "!#$%&'+-/=.?^`{|}~@[IPv6:0123:4567:89AB:CDEF:0123:4567:89AB:CDEF]",
        "FirstName.LastName@Corporation.com"
      ));

      @Override
      public boolean hasEmailAddress(IdentifiedUser who, String email) {
        return emails.contains(email);
      }

      @Override
      public Set<String> getEmailAddresses(IdentifiedUser who) {
        return emails;
      }
    };

    AbstractModule mod = new AbstractModule() {
      @Override
      protected void configure() {
        bind(Boolean.class).annotatedWith(DisableReverseDnsLookup.class)
          .toInstance(Boolean.FALSE);
        bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(config);
        bind(String.class).annotatedWith(AnonymousCowardName.class)
          .toProvider(AnonymousCowardNameProvider.class);
        bind(String.class).annotatedWith(CanonicalWebUrl.class)
          .toInstance("http://localhost:8080/");
        bind(AccountCache.class).toInstance(accountCache);
        bind(GroupBackend.class).to(SystemGroupBackend.class).in(SINGLETON);
        bind(CapabilityControl.Factory.class)
          .toProvider(Providers.<CapabilityControl.Factory>of(null));
        bind(Realm.class).toInstance(mockRealm);

      }
    };

    injector = Guice.createInjector(mod);
    injector.injectMembers(this);

    Account co = new Account(new Account.Id(1), TimeUtil.nowTs());
    ownerId = co.getId();

    identifiedUser = identifiedUserFactory.create(ownerId);

    /* Trigger identifiedUser to load the email addresses from mockRealm */
    identifiedUser.getEmailAddresses();
  }

  @Test
  public void testEmailsExistence() {
    assertTrue(identifiedUser.hasEmailAddress(""));
    assertTrue(identifiedUser.hasEmailAddress("firstname.lastname@corporation.com"));
    assertTrue(identifiedUser.hasEmailAddress("FirstName.LastName@Corporation.com"));
    assertTrue(identifiedUser.hasEmailAddress("FIRSTNAME.LASTNAME@CORPORATION.COM"));
    assertTrue(identifiedUser.hasEmailAddress("FirstName.LastName@Corporation.com"));

    assertTrue(identifiedUser.hasEmailAddress("!#$%&'+-/=.?^`{|}~@[IPv6:0123:4567:89AB:CDEF:0123:4567:89AB:CDEF]"));
    assertTrue(identifiedUser.hasEmailAddress("!#$%&'+-/=.?^`{|}~@[ipv6:0123:4567:89ab:cdef:0123:4567:89ab:cdef]"));

    assertFalse(identifiedUser.hasEmailAddress("non-exist@email.com"));

    /* assert again to test cached email address by IdentifiedUser.invalidEmails */
    assertFalse(identifiedUser.hasEmailAddress("non-exist@email.com"));
  }
}
