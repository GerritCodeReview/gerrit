// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.email.PreferredNotificationEmailProvider;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

/*
 * Tests the functionality of the extension point created to manage where emails are sent
 */
@TestPlugin(
    name = "preferred-notification-email-provider-it-plugin",
    sysModule =
        "com.google.gerrit.acceptance.server.mail.PreferredNotificationEmailProviderIT$TestModule")
public class PreferredNotificationEmailProviderIT extends LightweightPluginDaemonTest {

  private static final String SET_BY_PLUGIN = "setByThePlugin-";
  @Inject protected RequestScopeOperations requestScopeOperations;

  public static class TestModule extends AbstractModule {

    @Override
    protected void configure() {
      DynamicSet.bind(binder(), PreferredNotificationEmailProvider.class)
          .to(SimplePreferredNotificationEmailProvider.class);
    }
  }

  protected SimplePreferredNotificationEmailProvider provider;

  @Before
  public void setUp() {
    provider = plugin.getSysInjector().getInstance(SimplePreferredNotificationEmailProvider.class);
  }

  @Test
  public void validatePreferredNotificationEmailAddress() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.recommend());

    ImmutableList<FakeEmailSender.Message> messages = sender.getMessages();
    assertThat(messages.size()).isEqualTo(1);
    ImmutableList<Address> rcpts = messages.get(0).rcpt();
    assertThat(rcpts.size()).isEqualTo(2);
    Set<String> rcptEmails = rcpts.stream().map(a -> a.email()).collect(Collectors.toSet());
    assertThat(rcptEmails).contains(SET_BY_PLUGIN + admin.email());
    assertThat(rcptEmails).contains(SET_BY_PLUGIN + user.email());
  }

  @Singleton
  public static class SimplePreferredNotificationEmailProvider
      implements PreferredNotificationEmailProvider {
    @Override
    public String getPreferredNotificationEmail(Account account) {
      return SET_BY_PLUGIN + account.preferredEmail();
    }
  }
}
