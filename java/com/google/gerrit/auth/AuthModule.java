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

package com.google.gerrit.auth;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.auth.ldap.LdapModule;
import com.google.gerrit.auth.oauth.OAuthRealm;
import com.google.gerrit.auth.oauth.OAuthTokenAesGcmEncrypter;
import com.google.gerrit.auth.oauth.OAuthTokenCache;
import com.google.gerrit.auth.openid.OpenIdRealm;
import com.google.gerrit.extensions.auth.oauth.OAuthTokenEncrypter;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.InternalAuthBackend;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.AbstractModule;
import com.google.inject.ProvisionException;
import java.util.Base64;

public class AuthModule extends AbstractModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AuthType loginType;
  private final String oauthTokenEncryptionKey;

  public AuthModule(AuthConfig authConfig) {
    loginType = authConfig.getAuthType();
    oauthTokenEncryptionKey = authConfig.getOAuthTokenEncryptionKey();
  }

  @Override
  protected void configure() {
    install(OAuthTokenCache.module());
    bindOAuthTokenEncrypter();

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

      case OPENID:
      case OPENID_SSO:
        bind(Realm.class).to(OpenIdRealm.class);
        DynamicSet.bind(binder(), AuthBackend.class).to(InternalAuthBackend.class);
        break;

      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case HTTP:
      default:
        bind(Realm.class).to(DefaultRealm.class);
        DynamicSet.bind(binder(), AuthBackend.class).to(InternalAuthBackend.class);
        break;
    }
  }

  /**
   * Binds {@link OAuthTokenEncrypter} to encrypt stored OAuth tokens when {@code
   * auth.tokenEncryptionKey} holds a base64 AES key. Absent (and OAUTH auth), tokens are stored in
   * cleartext and a warning is logged.
   */
  private void bindOAuthTokenEncrypter() {
    if (oauthTokenEncryptionKey == null || oauthTokenEncryptionKey.isBlank()) {
      if (loginType == AuthType.OAUTH) {
        logger.atWarning().log(
            "auth.tokenEncryptionKey is not set; OAuth tokens are persisted in cleartext in the"
                + " oauth_tokens cache. Set a base64 AES key to encrypt the stored tokens.");
      }
      return;
    }
    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(oauthTokenEncryptionKey.trim());
    } catch (IllegalArgumentException e) {
      throw new ProvisionException("auth.tokenEncryptionKey is not valid base64", e);
    }
    if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
      throw new ProvisionException(
          "auth.tokenEncryptionKey must be a base64-encoded 128/192/256-bit AES key (16, 24 or 32"
              + " bytes)");
    }
    DynamicItem.bind(binder(), OAuthTokenEncrypter.class)
        .toInstance(new OAuthTokenAesGcmEncrypter(keyBytes));
  }
}
