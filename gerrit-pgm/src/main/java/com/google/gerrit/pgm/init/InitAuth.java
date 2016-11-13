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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.api.InitUtil.dnOf;

import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Initialize the {@code auth} configuration section. */
@Singleton
class InitAuth implements InitStep {
  private static final String RECEIVE = "receive";
  private static final String ENABLE_SIGNED_PUSH = "enableSignedPush";

  private final ConsoleUI ui;
  private final Section auth;
  private final Section ldap;
  private final Section receive;
  private final Libraries libraries;
  private final InitFlags flags;

  @Inject
  InitAuth(InitFlags flags, ConsoleUI ui, Libraries libraries, Section.Factory sections) {
    this.flags = flags;
    this.ui = ui;
    this.auth = sections.get("auth", null);
    this.ldap = sections.get("ldap", null);
    this.receive = sections.get(RECEIVE, null);
    this.libraries = libraries;
  }

  @Override
  public void run() {
    ui.header("User Authentication");

    initAuthType();
    if (auth.getSecure("registerEmailPrivateKey") == null) {
      auth.setSecure("registerEmailPrivateKey", SignedToken.generateRandomKey());
    }

    initSignedPush();
  }

  private void initAuthType() {
    AuthType authType =
        auth.select(
            "Authentication method",
            "type",
            flags.dev ? AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT : AuthType.OPENID);
    switch (authType) {
      case HTTP:
      case HTTP_LDAP:
        {
          String hdr = auth.get("httpHeader");
          if (ui.yesno(hdr != null, "Get username from custom HTTP header")) {
            auth.string("Username HTTP header", "httpHeader", "SM_USER");
          } else if (hdr != null) {
            auth.unset("httpHeader");
          }
          auth.string("SSO logout URL", "logoutUrl", null);
          break;
        }

      case CLIENT_SSL_CERT_LDAP:
      case CUSTOM_EXTENSION:
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case LDAP:
      case LDAP_BIND:
      case OAUTH:
      case OPENID:
      case OPENID_SSO:
        break;
    }

    switch (authType) {
      case LDAP:
      case LDAP_BIND:
      case HTTP_LDAP:
        {
          String server = ldap.string("LDAP server", "server", "ldap://localhost");
          if (server != null //
              && !server.startsWith("ldap://") //
              && !server.startsWith("ldaps://")) {
            if (ui.yesno(false, "Use SSL")) {
              server = "ldaps://" + server;
            } else {
              server = "ldap://" + server;
            }
            ldap.set("server", server);
          }

          ldap.string("LDAP username", "username", null);
          ldap.password("username", "password");

          String aBase = ldap.string("Account BaseDN", "accountBase", dnOf(server));
          ldap.string("Group BaseDN", "groupBase", aBase);
          break;
        }

      case CLIENT_SSL_CERT_LDAP:
      case CUSTOM_EXTENSION:
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case HTTP:
      case OAUTH:
      case OPENID:
      case OPENID_SSO:
        break;
    }
  }

  private void initSignedPush() {
    boolean def = flags.cfg.getBoolean(RECEIVE, ENABLE_SIGNED_PUSH, false);
    boolean enable = ui.yesno(def, "Enable signed push support");
    receive.set("enableSignedPush", Boolean.toString(enable));
    if (enable) {
      libraries.bouncyCastleProvider.downloadRequired();
      libraries.bouncyCastlePGP.downloadRequired();
    }
  }
}
