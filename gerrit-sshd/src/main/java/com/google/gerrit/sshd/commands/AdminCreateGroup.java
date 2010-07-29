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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_USERNAME;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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

  @Option(name = "--member", aliases = {"-m"}, metaVar = "USERNAME", usage = "initial set of users to become members of the group")
  private List<String> initialMembers = new LinkedList<String>();

  @Option(name = "--name", required = true, aliases = {"-n"}, metaVar = "NAME", usage = "name of group to be created")
  @Argument(index = 0)
  private String groupName;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ReviewDb db;

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

  private void createGroup() throws OrmException {
    AccountGroup.Id groupId = new AccountGroup.Id(db.nextAccountGroupId());
    AccountGroup.NameKey nameKey = new AccountGroup.NameKey(groupName);
    AccountGroup group = new AccountGroup(nameKey, groupId);
    if (ownerGroupId != null) {
      group.setOwnerGroupId(ownerGroupId);
    }
    if (groupDescription != null) {
      group.setDescription(groupDescription);
    }
    db.accountGroups().insert(Collections.singleton(group));
    AccountGroupName groupName = new AccountGroupName(group);
    db.accountGroupNames().insert(Collections.singleton(groupName));

    List<AccountGroupMember> memberships = new ArrayList<AccountGroupMember>();
    List<AccountGroupMemberAudit> membershipsAudit = new ArrayList<AccountGroupMemberAudit>();
    for (String userName : initialMembers) {
      AccountExternalId.Key key = new AccountExternalId.Key(SCHEME_USERNAME, userName);
      Account.Id accountId = db.accountExternalIds().get(key).getAccountId();

      AccountGroupMember membership =
          new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId));
      memberships.add(membership);

      AccountGroupMemberAudit audit =
          new AccountGroupMemberAudit(membership, currentUser.getAccountId());
      membershipsAudit.add(audit);
    }
    db.accountGroupMembers().insert(memberships);
    db.accountGroupMembersAudit().insert(membershipsAudit);
  }
}