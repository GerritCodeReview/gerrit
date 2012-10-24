// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.realm.ldap;

import javax.inject.Inject;

import com.google.gerrit.pgm.init.Section;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.realm.RealmConfigurationInitializer;

public class LdapRealmConfigurationInitializer implements
    RealmConfigurationInitializer {
  private final ConsoleUI ui;
  private final Section ldap;

  @Inject
  LdapRealmConfigurationInitializer(ConsoleUI ui, Section.Factory sections) {
    this.ui = ui;
    this.ldap = sections.get("ldap", null);
  }

  @Override
  public void init() {
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
  }

  static String dnOf(String name) {
    if (name != null) {
      int p = name.indexOf("://");
      if (0 < p) {
        name = name.substring(p + 3);
      }

      p = name.indexOf(".");
      if (0 < p) {
        name = name.substring(p + 1);
        name = "DC=" + name.replaceAll("\\.", ",DC=");
      } else {
        name = null;
      }
    }
    return name;
  }

}
