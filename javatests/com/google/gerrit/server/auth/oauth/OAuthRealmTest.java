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

package com.google.gerrit.server.auth.oauth;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_EXTERNAL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public final class OAuthRealmTest {
  @Inject private OAuthRealm oauthRealm = null;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
  }

  @Test
  public void accountBelongsToRealm() throws Exception {
    Account.Id id = new Account.Id(1000);

    assertTrue(
        oauthRealm.accountBelongsToRealm(
            Arrays.asList(ExternalId.create(SCHEME_EXTERNAL, "test", id))));
    assertFalse(
        oauthRealm.accountBelongsToRealm(
            Arrays.asList(ExternalId.createUsername("test", id, null))));
    assertFalse(
        oauthRealm.accountBelongsToRealm(Arrays.asList(ExternalId.createEmail(id, "foo@bar.com"))));

    assertFalse(
        oauthRealm.accountBelongsToRealm(
            Arrays.asList(ExternalId.createUsername("external", id, null))));
    assertFalse(
        oauthRealm.accountBelongsToRealm(
            Arrays.asList(ExternalId.createUsername("xxexternalxx", id, null))));
    assertFalse(
        oauthRealm.accountBelongsToRealm(
            Arrays.asList(ExternalId.createEmail(id, "external.foo@bar.com"))));

    assertTrue(
        oauthRealm.accountBelongsToRealm(
            Arrays.asList(
                ExternalId.createUsername("test", id, null),
                ExternalId.create(SCHEME_EXTERNAL, "test", id))));
  }
}
