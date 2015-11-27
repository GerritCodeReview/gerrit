// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.apache.commons.validator.routines.EmailValidator;

import java.util.Collections;

public class InitAdminUser implements InitStep {
  private final ConsoleUI ui;
  private final InitFlags flags;
  private SchemaFactory<ReviewDb> dbFactory;

  @Inject
  InitAdminUser(
      InitFlags flags,
      ConsoleUI ui) {
    this.flags = flags;
    this.ui = ui;
  }

  @Override
  public void run() {
  }

  @Inject(optional = true)
  void set(SchemaFactory<ReviewDb> dbFactory) {
    this.dbFactory = dbFactory;
  }

  @Override
  public void postRun() throws Exception {
    AuthType authType =
        flags.cfg.getEnum(AuthType.values(), "auth", null, "type", null);
    if (authType != AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT) {
      return;
    }

    ReviewDb db = dbFactory.open();
    try {
      if (db.accounts().anyAccounts().toList().isEmpty()) {
        ui.header("Gerrit Administrator");
        if (ui.yesno(true, "Create administrator user")) {
          Account.Id id = new Account.Id(db.nextAccountId());
          String username = ui.readString("admin", "username");
          String name = ui.readString("Administrator", "name");
          String email = readEmail();
          String httpPassword = ui.readString("secret", "HTTP password");

          AccountExternalId extUser =
              new AccountExternalId(id, new AccountExternalId.Key(
                  AccountExternalId.SCHEME_USERNAME, username));
          if (!Strings.isNullOrEmpty(httpPassword)) {
            extUser.setPassword(httpPassword);
          }
          db.accountExternalIds().insert(Collections.singleton(extUser));

          if (email != null) {
            AccountExternalId extMailto =
                new AccountExternalId(id, new AccountExternalId.Key(
                    AccountExternalId.SCHEME_MAILTO, email));
            extMailto.setEmailAddress(email);
            db.accountExternalIds().insert(Collections.singleton(extMailto));
          }

          Account a = new Account(id, TimeUtil.nowTs());
          a.setFullName(name);
          a.setPreferredEmail(email);
          db.accounts().insert(Collections.singleton(a));

          AccountGroupName adminGroup = db.accountGroupNames().get(
              new AccountGroup.NameKey("Administrators"));
          AccountGroupMember m =
              new AccountGroupMember(new AccountGroupMember.Key(id,
                  adminGroup.getId()));
          db.accountGroupMembers().insert(Collections.singleton(m));
        }
      }
    } finally {
      db.close();
    }
  }

  private String readEmail() {
    String email = ui.readString("admin@example.com", "email");
    if (email != null && !EmailValidator.getInstance().isValid(email)) {
      ui.message("error: invalid email address\n");
      return readEmail();
    }
    return email;
  }
}
