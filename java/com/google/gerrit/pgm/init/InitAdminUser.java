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
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.pgm.init.api.AllUsersNameOnInitProvider;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.SequencesOnInit;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.validator.routines.EmailValidator;

public class InitAdminUser implements InitStep {
  private final InitFlags flags;
  private final ConsoleUI ui;
  private final AllUsersNameOnInitProvider allUsers;
  private final AccountsOnInit accounts;
  private final VersionedAuthorizedKeysOnInit.Factory authorizedKeysFactory;
  private final ExternalIdsOnInit externalIds;
  private final SequencesOnInit sequencesOnInit;
  private final GroupsOnInit groupsOnInit;
  private SchemaFactory<ReviewDb> dbFactory;
  private AccountIndexCollection accountIndexCollection;
  private GroupIndexCollection groupIndexCollection;

  @Inject
  InitAdminUser(
      InitFlags flags,
      ConsoleUI ui,
      AllUsersNameOnInitProvider allUsers,
      AccountsOnInit accounts,
      VersionedAuthorizedKeysOnInit.Factory authorizedKeysFactory,
      ExternalIdsOnInit externalIds,
      SequencesOnInit sequencesOnInit,
      GroupsOnInit groupsOnInit) {
    this.flags = flags;
    this.ui = ui;
    this.allUsers = allUsers;
    this.accounts = accounts;
    this.authorizedKeysFactory = authorizedKeysFactory;
    this.externalIds = externalIds;
    this.sequencesOnInit = sequencesOnInit;
    this.groupsOnInit = groupsOnInit;
  }

  @Override
  public void run() {}

  @Inject(optional = true)
  void set(SchemaFactory<ReviewDb> dbFactory) {
    this.dbFactory = dbFactory;
  }

  @Inject
  void set(AccountIndexCollection accountIndexCollection) {
    this.accountIndexCollection = accountIndexCollection;
  }

  @Inject
  void set(GroupIndexCollection groupIndexCollection) {
    this.groupIndexCollection = groupIndexCollection;
  }

  @Override
  public void postRun() throws Exception {
    AuthType authType = flags.cfg.getEnum(AuthType.values(), "auth", null, "type", null);
    if (authType != AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT) {
      return;
    }

    try (ReviewDb db = dbFactory.open()) {
      if (!accounts.hasAnyAccount()) {
        ui.header("Gerrit Administrator");
        if (ui.yesno(true, "Create administrator user")) {
          Account.Id id = new Account.Id(sequencesOnInit.nextAccountId(db));
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
          externalIds.insert("Add external IDs for initial admin user", extIds);

          Account a = new Account(id, TimeUtil.nowTs());
          a.setFullName(name);
          a.setPreferredEmail(email);
          accounts.insert(a);

          // Only two groups should exist at this point in time and hence iterating over all of them
          // is cheap.
          Optional<GroupReference> adminGroupReference =
              groupsOnInit
                  .getAllGroupReferences(db)
                  .filter(group -> group.getName().equals("Administrators"))
                  .findAny();
          if (!adminGroupReference.isPresent()) {
            throw new NoSuchGroupException("Administrators");
          }
          GroupReference adminGroup = adminGroupReference.get();
          groupsOnInit.addGroupMember(db, adminGroup.getUUID(), a);

          if (sshKey != null) {
            VersionedAuthorizedKeysOnInit authorizedKeys = authorizedKeysFactory.create(id).load();
            authorizedKeys.addKey(sshKey.sshPublicKey());
            authorizedKeys.save("Add SSH key for initial admin user\n");
          }

          AccountState as = AccountState.forAccount(new AllUsersName(allUsers.get()), a, extIds);
          for (AccountIndex accountIndex : accountIndexCollection.getWriteIndexes()) {
            accountIndex.replace(as);
          }

          InternalGroup adminInternalGroup = groupsOnInit.getExistingGroup(db, adminGroup);
          for (GroupIndex groupIndex : groupIndexCollection.getWriteIndexes()) {
            groupIndex.replace(adminInternalGroup);
          }
        }
      }
    }
  }

  private String readEmail(AccountSshKey sshKey) {
    String defaultEmail = "admin@example.com";
    if (sshKey != null && sshKey.comment() != null) {
      String c = sshKey.comment().trim();
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
    return AccountSshKey.create(id, 1, content);
  }
}
