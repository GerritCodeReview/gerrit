// Copyright (C) 2021 Open Infrastructure Foundation
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

package com.google.gerrit.auth.openid;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_EXTERNAL;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_HTTP;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_HTTPS;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_XRI;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public final class OpenIdRealmTest {
  @Inject private OpenIdRealm openidRealm;
  @Inject private ExternalIdFactory extIdFactory;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
  }

  private ExternalId id(String scheme, String id) {
    return extIdFactory.create(scheme, id, Account.id(1000));
  }

  private boolean accountBelongsToRealm(ExternalId... ids) {
    return openidRealm.accountBelongsToRealm(Arrays.asList(ids));
  }

  private boolean accountBelongsToRealm(String scheme, String id) {
    return accountBelongsToRealm(id(scheme, id));
  }

  @Test
  public void accountBelongsToRealm() throws Exception {
    assertThat(accountBelongsToRealm(SCHEME_HTTP, "example.org/test")).isTrue();
    assertThat(accountBelongsToRealm(SCHEME_HTTPS, "example.org/test")).isTrue();
    assertThat(accountBelongsToRealm(SCHEME_XRI, "example.org/test")).isTrue();
    assertThat(
            accountBelongsToRealm(id(SCHEME_USERNAME, "test"), id(SCHEME_HTTP, "example.org/test")))
        .isTrue();
    assertThat(
            accountBelongsToRealm(
                id(SCHEME_USERNAME, "test"), id(SCHEME_HTTPS, "example.org/test")))
        .isTrue();
    assertThat(
            accountBelongsToRealm(id(SCHEME_USERNAME, "test"), id(SCHEME_XRI, "example.org/test")))
        .isTrue();
    assertThat(
            accountBelongsToRealm(id(SCHEME_HTTP, "example.org/test"), id(SCHEME_USERNAME, "test")))
        .isTrue();
    assertThat(
            accountBelongsToRealm(
                id(SCHEME_HTTPS, "example.org/test"), id(SCHEME_USERNAME, "test")))
        .isTrue();
    assertThat(
            accountBelongsToRealm(id(SCHEME_XRI, "test"), id(SCHEME_USERNAME, "example.org/test")))
        .isTrue();

    assertThat(accountBelongsToRealm(SCHEME_EXTERNAL, "test")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_GERRIT, "test")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "test")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "foo@bar.com")).isFalse();

    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "gerrit")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "xxgerritxx")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "gerrit.foo@bar.com")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "bar.gerrit@bar.com")).isFalse();
  }
}
