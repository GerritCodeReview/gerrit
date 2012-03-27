// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupList;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.VisibleGroups;
import com.google.gerrit.server.ioutil.ColumnFormatter;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

public class ListGroupsCommand extends SshCommand {
  @Inject
  private GroupCache groupCache;

  @Inject
  private VisibleGroups.Factory visibleGroupsFactory;

  @Inject
  private IdentifiedUser.GenericFactory userFactory;

  @Option(name = "--project", aliases = {"-p"},
      usage = "projects for which the groups should be listed")
  private final List<ProjectControl> projects = new ArrayList<ProjectControl>();

  @Option(name = "--visible-to-all", usage = "to list only groups that are visible to all registered users")
  private boolean visibleToAll;

  @Option(name = "--type", usage = "type of group")
  private AccountGroup.Type groupType;

  @Option(name = "--user", aliases = {"-u"},
      usage = "user for which the groups should be listed")
  private Account.Id user;

  @Option(name = "--verbose", aliases = {"-v"},
      usage = "verbose output format with tab-separated columns for the " +
          "group name, UUID, description, type, owner group name, " +
          "owner group UUID, and whether the group is visible to all")
  private boolean verboseOutput;

  @Override
  protected void run() throws Failure {
    try {
      if (user != null && !projects.isEmpty()) {
        throw new UnloggedFailure(1, "fatal: --user and --project options are not compatible.");
      }

      final VisibleGroups visibleGroups = visibleGroupsFactory.create();
      visibleGroups.setOnlyVisibleToAll(visibleToAll);
      visibleGroups.setGroupType(groupType);
      final GroupList groupList;
      if (!projects.isEmpty()) {
        groupList = visibleGroups.get(projects);
      } else if (user != null) {
        groupList = visibleGroups.get(userFactory.create(user));
      } else {
        groupList = visibleGroups.get();
      }

      final ColumnFormatter formatter = new ColumnFormatter(stdout, '\t');
      for (final GroupDetail groupDetail : groupList.getGroups()) {
        final AccountGroup g = groupDetail.group;
        formatter.addColumn(g.getName());
        if (verboseOutput) {
          formatter.addColumn(KeyUtil.decode(g.getGroupUUID().toString()));
          formatter.addColumn(
              g.getDescription() != null ? g.getDescription() : "");
          formatter.addColumn(g.getType().toString());
          final AccountGroup owningGroup =
              groupCache.get(g.getOwnerGroupUUID());
          formatter.addColumn(
              owningGroup != null ? owningGroup.getName() : "n/a");
          formatter.addColumn(KeyUtil.decode(g.getOwnerGroupUUID().toString()));
          formatter.addColumn(Boolean.toString(g.isVisibleToAll()));
        }
        formatter.nextLine();
      }
      formatter.finish();
    } catch (OrmException e) {
      throw die(e);
    } catch (NoSuchGroupException e) {
      throw die(e);
    }
  }
}
