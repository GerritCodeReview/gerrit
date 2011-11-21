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
import com.google.gerrit.server.account.VisibleGroups;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ListGroupsCommand extends BaseCommand {

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

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        ListGroupsCommand.this.display();
      }
    });
  }

  private void display() throws Failure {
    final PrintWriter stdout = toPrintWriter(out);
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
      for (final GroupDetail groupDetail : groupList.getGroups()) {
        stdout.print(groupDetail.group.getName() + "\n");
      }
    } catch (OrmException e) {
      throw die(e);
    } catch (NoSuchGroupException e) {
      throw die(e);
    } finally {
      stdout.flush();
    }
  }
}
