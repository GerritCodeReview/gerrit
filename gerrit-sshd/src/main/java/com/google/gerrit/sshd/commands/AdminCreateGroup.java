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

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.HashSet;
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

  private final Set<Account.Id> initialMembers = new HashSet<Account.Id>();

  @Option(name = "--member", aliases = {"-m"}, metaVar = "USERNAME", usage = "initial set of users to become members of the group")
  void addMember(final Account.Id id) {
    initialMembers.add(id);
  }

  @Inject
  private PerformCreateGroup.Factory performCreateGroupFactory;

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
    final PerformCreateGroup performCreateGroup =
        performCreateGroupFactory.create();
    try {
      performCreateGroup.createGroup(groupName, groupDescription, ownerGroupId, initialMembers.toArray(new Account.Id[initialMembers.size()]));
    } catch (NameAlreadyUsedException e) {
      throw die(e);
    }
  }
}