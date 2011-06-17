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

import static com.google.gerrit.pgm.init.InitUtil.dnOf;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.reviewdb.AuthType;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Initialize the {@code auth} configuration section. */
@Singleton
class InitAuth implements InitStep {
  private final ConsoleUI ui;
  private final Section auth;
  private final Section ldap;

  @Inject
  InitAuth(final ConsoleUI ui, final Section.Factory sections) {
    this.ui = ui;
    this.auth = sections.get("auth");
    this.ldap = sections.get("ldap");
  }

  public void run() {
    ui.header("User Authentication");

    final AuthType auth_type =
        auth.select("Authentication method", "type", AuthType.OPENID);

    switch (auth_type) {
      case HTTP:
      case HTTP_LDAP: {
        String hdr = auth.get("httpHeader");
        if (ui.yesno(hdr != null, "Get username from custom HTTP header")) {
          auth.string("Username HTTP header", "httpHeader", "SM_USER");
        } else if (hdr != null) {
          auth.unset("httpHeader");
        }
        auth.string("SSO logout URL", "logoutUrl", null);
        break;
      }
    }

    switch (auth_type) {
      case LDAP:
      case LDAP_BIND:
      case HTTP_LDAP: {
        String server =
            ldap.string("LDAP server", "server", "ldap://localhost");
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
    }

    if (auth.getSecure("registerEmailPrivateKey") == null) {
      auth.setSecure("registerEmailPrivateKey", SignedToken.generateRandomKey());
    }
  }
}
