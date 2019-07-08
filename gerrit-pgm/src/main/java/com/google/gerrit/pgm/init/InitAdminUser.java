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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.ExternalId;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.validator.routines.EmailValidator;

public class InitAdminUser implements InitStep {
  private final ConsoleUI ui;
  private final InitFlags flags;
  private final VersionedAuthorizedKeysOnInit.Factory authorizedKeysFactory;
  private final ExternalIdsOnInit externalIds;
  private SchemaFactory<ReviewDb> dbFactory;
  private AccountIndexCollection indexCollection;

  @Inject
  InitAdminUser(
      InitFlags flags,
      ConsoleUI ui,
      VersionedAuthorizedKeysOnInit.Factory authorizedKeysFactory,
      ExternalIdsOnInit externalIds) {
    this.flags = flags;
    this.ui = ui;
    this.authorizedKeysFactory = authorizedKeysFactory;
    this.externalIds = externalIds;
  }

  @Override
  public void run() {}

  @Inject(optional = true)
  void set(SchemaFactory<ReviewDb> dbFactory) {
    this.dbFactory = dbFactory;
  }

  @Inject(optional = true)
  void set(AccountIndexCollection indexCollection) {
    this.indexCollection = indexCollection;
  }

  @Override
  public void postRun() throws Exception {
    AuthType authType = flags.cfg.getEnum(AuthType.values(), "auth", null, "type", null);
    if (authType != AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT) {
      return;
    }

    try (ReviewDb db = dbFactory.open()) {
      if (db.accounts().anyAccounts().toList().isEmpty()) {
        ui.header("Gerrit Administrator");
        if (ui.yesno(true, "Create administrator user")) {
          Account.Id id = new Account.Id(db.nextAccountId());
          String username = ui.readString("admin", "username");
          String name = ui.readString("Administrator", "name");
          String httpPassword = ui.readString("secret", "HTTP password");
          AccountSshKey sshKey = readSshKey(id);
          String email = readEmail(sshKey);

          List<ExternalId> extIds = new ArrayList<>(2);
          extIds.add(ExternalId.createUsername(username, id, httpPassword));

          if (email != null) {
            extIds.add(ExternalId.createEmail(id, email));
          }
          externalIds.insert(db, extIds);

          Account a = new Account(id, TimeUtil.nowTs());
          a.setFullName(name);
          a.setPreferredEmail(email);
          db.accounts().insert(Collections.singleton(a));

          AccountGroupName adminGroupName =
              db.accountGroupNames().get(new AccountGroup.NameKey("Administrators"));
          AccountGroupMember m =
              new AccountGroupMember(new AccountGroupMember.Key(id, adminGroupName.getId()));
          db.accountGroupMembers().insert(Collections.singleton(m));

          if (sshKey != null) {
            VersionedAuthorizedKeysOnInit authorizedKeys = authorizedKeysFactory.create(id).load();
            authorizedKeys.addKey(sshKey.getSshPublicKey());
            authorizedKeys.save("Add SSH key for initial admin user\n");
          }

          AccountGroup adminGroup = db.accountGroups().get(adminGroupName.getId());
          AccountState as =
              new AccountState(
                  a, Collections.singleton(adminGroup.getGroupUUID()), extIds, new HashMap<>());
          for (AccountIndex accountIndex : indexCollection.getWriteIndexes()) {
            accountIndex.replace(as);
          }
        }
      }
    }
  }

  private String readEmail(AccountSshKey sshKey) {
    String defaultEmail = "admin@example.com";
    if (sshKey != null && sshKey.getComment() != null) {
      String c = sshKey.getComment().trim();
      if (EmailValidator.getInstance().isValid(c)) {
        defaultEmail = c;
      }
    }
    return readEmail(defaultEmail);
  }

  private String readEmail(String defaultEmail) {
    String email = ui.readString(defaultEmail, "email");
    if (email != null && !EmailValidator.getInstance().isValid(email)) {
      ui.message("error: invalid email address\n");
      return readEmail(defaultEmail);
    }
    return email;
  }

  private AccountSshKey readSshKey(Account.Id id) throws IOException {
    String defaultPublicSshKeyFile = "";
    Path defaultPublicSshKeyPath = Paths.get(System.getProperty("user.home"), ".ssh", "id_rsa.pub");
    if (Files.exists(defaultPublicSshKeyPath)) {
      defaultPublicSshKeyFile = defaultPublicSshKeyPath.toString();
    }
    String publicSshKeyFile = ui.readString(defaultPublicSshKeyFile, "public SSH key file");
    return !Strings.isNullOrEmpty(publicSshKeyFile) ? createSshKey(id, publicSshKeyFile) : null;
  }

  private AccountSshKey createSshKey(Account.Id id, String keyFile) throws IOException {
    Path p = Paths.get(keyFile);
    if (!Files.exists(p)) {
      throw new IOException(String.format("Cannot add public SSH key: %s is not a file", keyFile));
    }
    String content = new String(Files.readAllBytes(p), UTF_8);
    return new AccountSshKey(new AccountSshKey.Id(id, 1), content);
  }
}
