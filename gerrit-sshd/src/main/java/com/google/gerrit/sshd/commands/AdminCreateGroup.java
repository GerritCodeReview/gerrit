// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.lib.PersonIdent;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates a new group.
 * <p>
 * Optionally, puts an initial set of user in the newly created group.
 */
@AdminCommand
public class AdminCreateGroup extends BaseCommand {
  @Option(name = "--owner", aliases = {"-o"}, metaVar = "GROUP", usage = "owning group, if not specified the group will be self-owning")
  private AccountGroup.Id ownerGroupId;

  @Option(name = "--description", aliases = {"-d"}, metaVar = "DESC", usage = "description of group")
  private String groupDescription = "";

  @Argument(index = 0, required = true, metaVar = "GROUP", usage = "name of group to be created")
  private String groupName;

  @Inject
  private IdentifiedUser user;

  @Inject
  private ReviewDb db;

  @Inject
  @GerritPersonIdent
  private PersonIdent serverIdent;

  private final Set<Account.Id> initialMembers = new HashSet<Account.Id>();

  @Option(name = "--member", aliases = {"-m"}, metaVar = "USERNAME", usage = "initial set of users to become members of the group")
  void addMember(final Account.Id id) {
    initialMembers.add(id);
  }

  @Override
  public void start(Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        createGroup();
      }
    });
  }

  private void createGroup() throws OrmException, UnloggedFailure {
    AccountGroup.Id groupId = new AccountGroup.Id(db.nextAccountGroupId());
    AccountGroup.NameKey nameKey = new AccountGroup.NameKey(groupName);
    AccountGroup.UUID uuid = GroupUUID.make(groupName, //
        user.newCommitterIdent( //
            serverIdent.getWhen(), //
            serverIdent.getTimeZone()));
    AccountGroup group = new AccountGroup(nameKey, groupId, uuid);
    if (ownerGroupId != null) {
      group.setOwnerGroupId(ownerGroupId);
    }
    if (groupDescription != null) {
      group.setDescription(groupDescription);
    }
    AccountGroupName gn = new AccountGroupName(group);
    // first insert the group name to validate that the group name hasn't already been
    // used to create another group
    try {
      db.accountGroupNames().insert(Collections.singleton(gn));
    } catch (OrmDuplicateKeyException e) {
      throw die("group '" + groupName + "' already exists");
    }
    db.accountGroups().insert(Collections.singleton(group));

    List<AccountGroupMember> memberships = new ArrayList<AccountGroupMember>();
    List<AccountGroupMemberAudit> membershipsAudit = new ArrayList<AccountGroupMemberAudit>();
    for (Account.Id accountId : initialMembers) {
      AccountGroupMember membership =
          new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId));
      memberships.add(membership);

      AccountGroupMemberAudit audit =
          new AccountGroupMemberAudit(membership, user.getAccountId());
      membershipsAudit.add(audit);
    }
    db.accountGroupMembers().insert(memberships);
    db.accountGroupMembersAudit().insert(membershipsAudit);
  }
}