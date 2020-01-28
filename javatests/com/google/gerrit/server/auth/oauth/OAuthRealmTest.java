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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_EXTERNAL;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

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

  private ExternalId id(String scheme, String id) {
    return ExternalId.create(scheme, id, new Account.Id(1000));
  }

  private boolean accountBelongsToRealm(ExternalId... ids) {
    return oauthRealm.accountBelongsToRealm(Arrays.asList(ids));
  }

  private boolean accountBelongsToRealm(String scheme, String id) {
    return accountBelongsToRealm(id(scheme, id));
  }

  @Test
  public void accountBelongsToRealm() throws Exception {
    assertThat(accountBelongsToRealm(SCHEME_EXTERNAL, "test")).isTrue();
    assertThat(accountBelongsToRealm(id(SCHEME_USERNAME, "test"), id(SCHEME_EXTERNAL, "test")))
        .isTrue();
    assertThat(accountBelongsToRealm(id(SCHEME_EXTERNAL, "test"), id(SCHEME_USERNAME, "test")))
        .isTrue();

    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "test")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "foo@bar.com")).isFalse();

    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "external")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "xxexternalxx")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "external.foo@bar.com")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "bar.external@bar.com")).isFalse();
  }
}
