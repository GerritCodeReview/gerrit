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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupDetailFactory.Factory;
import com.google.gerrit.server.group.ListMembers;
import com.google.gerrit.server.ioutil.ColumnFormatter;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gwtorm.server.OrmException;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;

import java.io.PrintWriter;
import java.util.List;

import javax.inject.Inject;

/**
 * Implements a command that allows the user to see the members of a group.
 */
@CommandMetaData(name = "ls-members", description = "Lists the members of a given group",
  runsAt = MASTER_OR_SLAVE)
public class ListMembersCommand extends BaseCommand {
  @Inject
  ListMembersCommandImpl impl;

  @Override
  public void start(Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine(impl);
        final PrintWriter stdout = toPrintWriter(out);
        try {
          impl.display(stdout);
        } finally {
          stdout.flush();
        }
      }
    });
  }

  private static class ListMembersCommandImpl extends ListMembers {
    @Argument(required = true, usage = "the name of the group", metaVar = "GROUPNAME")
    private String name;

    private final GroupCache groupCache;

    @Inject
    protected ListMembersCommandImpl(GroupCache groupCache,
        Factory groupDetailFactory,
        AccountInfo.Loader.Factory accountLoaderFactory,
        AccountCache accountCache) {
      super(groupCache, groupDetailFactory, accountLoaderFactory);
      this.groupCache = groupCache;
    }

    void display(PrintWriter writer) throws UnloggedFailure, OrmException {
      AccountGroup group = groupCache.get(new AccountGroup.NameKey(name));
      String errorText = "Group not found or not visible\n";

      if (group == null) {
        writer.write(errorText);
        writer.flush();
        return;
      }

      try {
        List<AccountInfo> members = apply(group.getGroupUUID());
        ColumnFormatter formatter = new ColumnFormatter(writer, '\t');
        formatter.addColumn("id");
        formatter.addColumn("username");
        formatter.addColumn("full name");
        formatter.addColumn("email");
        formatter.nextLine();
        for (AccountInfo member : members) {
          if (member == null) {
            continue;
          }

          formatter.addColumn(member._id.toString());
          formatter.addColumn(Objects.firstNonNull(member.username, "n/a"));
          formatter.addColumn(Objects.firstNonNull(
              Strings.emptyToNull(member.name), "n/a"));
          formatter.addColumn(Objects.firstNonNull(member.email, "n/a"));
          formatter.nextLine();
        }

        formatter.finish();
      } catch (MethodNotAllowedException e) {
        writer.write(errorText);
        writer.flush();
      }
    }
  }
}
