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

package com.google.gerrit.sshd.commands;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.AddIncludedGroups;
import com.google.gerrit.server.group.AddMembers;
import com.google.gerrit.server.group.DeleteIncludedGroups;
import com.google.gerrit.server.group.DeleteMembers;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@CommandMetaData(name = "set-members", descr = "Modifies members of specific group or number of groups")
public class SetMembersCommand extends SshCommand {

  @Option(name = "--add", aliases = {"-a"}, metaVar = "USER", usage = "users that should be added as group member")
  private List<Account.Id> accountsToAdd = new ArrayList<Account.Id>();

  @Option(name = "--remove", aliases = {"-r"}, metaVar = "USER", usage = "users that should be removed from the group")
  private List<Account.Id> accountsToRemove = new ArrayList<Account.Id>();

  @Option(name = "--include", aliases = {"-i"}, metaVar = "GROUP", usage = "group that should be included as group member")
  private List<AccountGroup.Id> groupsToInclude =
      new ArrayList<AccountGroup.Id>();

  @Option(name = "--remove-group", aliases = {"-g"}, metaVar = "GROUP", usage = "group that should be removed from the group")
  private List<AccountGroup.Id> groupsToRemove =
      new ArrayList<AccountGroup.Id>();

  @Argument(index = 0, required = true, multiValued = true, metaVar = "GROUP", usage = "groups to modify")
  private List<AccountGroup.Id> groups = new ArrayList<AccountGroup.Id>();

  @Inject
  private GroupCache groupCache;

  @Inject
  private GroupControl.Factory groupControlFactory;

  @Inject
  private AddMembers addMembers;

  @Inject
  private DeleteMembers deleteMembers;

  @Inject
  AddIncludedGroups addIncludedGroups;

  @Inject
  DeleteIncludedGroups deleteIncludedGroups;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    for (AccountGroup.Id groupId : groups) {
      GroupResource resource = toResource(groupId);
      if (!accountsToRemove.isEmpty()) {
        deleteMembers.apply(resource, fromMembers(accountsToRemove));
      }
      if (!groupsToRemove.isEmpty()) {
        deleteIncludedGroups.apply(resource, fromGroups(groupsToRemove));
      }
      if (!accountsToAdd.isEmpty()) {
        reportMembersAdded(resource.getName(),
            addMembers.apply(resource, fromMembers(accountsToAdd)));
      }
      if (!groupsToInclude.isEmpty()) {
        reportGroupsAdded(resource.getName(),
            addIncludedGroups.apply(resource, fromGroups(groupsToInclude)));
      }
    }
  }

  private void reportMembersAdded(String groupName,
      List<AccountInfo> accountInfoList) throws UnsupportedEncodingException,
      IOException {
    out.write(String.format(
        "Members added to group %s: %s\n",
        groupName,
        Joiner.on(",").join(
            Iterables.transform(accountInfoList,
                new Function<AccountInfo, String>() {
                  @Override
                  public String apply(AccountInfo accountInfo) {
                    return accountInfo.username;
                  }
                }))).getBytes(ENC));
  }

  private void reportGroupsAdded(String groupName, List<GroupInfo> groupInfoList)
      throws UnsupportedEncodingException, IOException {
    out.write(String.format(
        "Groups added to group %s: %s\n",
        groupName,
        Joiner.on(",").join(
            Iterables.transform(groupInfoList,
                new Function<GroupInfo, String>() {
                  @Override
                  public String apply(GroupInfo groupInfo) {
                    return groupInfo.name;
                  }
                }))).getBytes(ENC));
  }

  private AddIncludedGroups.Input fromGroups(List<AccountGroup.Id> accounts) {
    return AddIncludedGroups.Input.fromGroups(Lists.newArrayList(Iterables
        .transform(accounts, new Function<AccountGroup.Id, String>() {
          @Override
          public String apply(AccountGroup.Id id) {
            return id.toString();
          }
        })));
  }

  private AddMembers.Input fromMembers(List<Account.Id> accounts) {
    return AddMembers.Input.fromMembers(Lists.newArrayList(Iterables.transform(
        accounts, new Function<Account.Id, String>() {
          @Override
          public String apply(Account.Id id) {
            return id.toString();
          }
        })));
  }

  private GroupResource toResource(AccountGroup.Id groupId)
      throws AuthException, ResourceNotFoundException {
    AccountGroup group = groupCache.get(groupId);
    GroupControl ctl = groupControlFactory.controlFor(group);
    if (!ctl.isVisible()) {
      throw new ResourceNotFoundException(group.getId().toString());
    }
    return new GroupResource(ctl);
  }
}
