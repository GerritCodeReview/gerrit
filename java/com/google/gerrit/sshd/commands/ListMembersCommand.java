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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupDetailFactory.Factory;
import com.google.gerrit.server.group.ListMembers;
import com.google.gerrit.server.ioutil.ColumnFormatter;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.PrintWriter;
import java.util.List;
import org.kohsuke.args4j.Argument;

/** Implements a command that allows the user to see the members of a group. */
@CommandMetaData(
  name = "ls-members",
  description = "List the members of a given group",
  runsAt = MASTER_OR_SLAVE
)
public class ListMembersCommand extends SshCommand {
  @Inject ListMembersCommandImpl impl;

  @Override
  public void run() throws Exception {
    impl.display(stdout);
  }

  @Override
  protected void parseCommandLine() throws UnloggedFailure {
    parseCommandLine(impl);
  }

  private static class ListMembersCommandImpl extends ListMembers {
    @Argument(required = true, usage = "the name of the group", metaVar = "GROUPNAME")
    private String name;

    private final GroupCache groupCache;

    @Inject
    protected ListMembersCommandImpl(
        GroupCache groupCache,
        Factory groupDetailFactory,
        AccountLoader.Factory accountLoaderFactory) {
      super(groupCache, groupDetailFactory, accountLoaderFactory);
      this.groupCache = groupCache;
    }

    void display(PrintWriter writer) throws OrmException {
      AccountGroup group = groupCache.get(new AccountGroup.NameKey(name));
      String errorText = "Group not found or not visible\n";

      if (group == null) {
        writer.write(errorText);
        writer.flush();
        return;
      }

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

        formatter.addColumn(Integer.toString(member._accountId));
        formatter.addColumn(MoreObjects.firstNonNull(member.username, "n/a"));
        formatter.addColumn(MoreObjects.firstNonNull(Strings.emptyToNull(member.name), "n/a"));
        formatter.addColumn(MoreObjects.firstNonNull(member.email, "n/a"));
        formatter.nextLine();
      }

      formatter.finish();
    }
  }
}
