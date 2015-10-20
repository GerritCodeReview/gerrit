// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.InternalAuthBackend;
import com.google.gerrit.server.auth.ldap.LdapModule;
import com.google.gerrit.server.auth.oauth.OAuthRealm;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;

public class AuthModule extends AbstractModule {
  private final AuthType loginType;

  @Inject
  AuthModule(AuthConfig authConfig) {
    loginType = authConfig.getAuthType();
  }

  @Override
  protected void configure() {
    switch (loginType) {
      case HTTP_LDAP:
      case LDAP:
      case LDAP_BIND:
      case CLIENT_SSL_CERT_LDAP:
        install(new LdapModule());
        break;

      case OAUTH:
        bind(Realm.class).to(OAuthRealm.class);
        break;

      case CUSTOM_EXTENSION:
        break;

      default:
        bind(Realm.class).to(DefaultRealm.class);
        DynamicSet.bind(binder(), AuthBackend.class).to(InternalAuthBackend.class);
        break;
    }
  }
}
