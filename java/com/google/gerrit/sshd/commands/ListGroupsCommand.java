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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GetGroups;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GroupJson;
import com.google.gerrit.server.group.ListGroups;
import com.google.gerrit.server.ioutil.ColumnFormatter;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.PrintWriter;
import org.kohsuke.args4j.Option;

@CommandMetaData(
  name = "ls-groups",
  description = "List groups visible to the caller",
  runsAt = MASTER_OR_SLAVE
)
public class ListGroupsCommand extends SshCommand {
  @Inject private MyListGroups impl;

  @Override
  public void run() throws Exception {
    if (impl.getUser() != null && !impl.getProjects().isEmpty()) {
      throw die("--user and --project options are not compatible.");
    }
    impl.display(stdout);
  }

  @Override
  protected void parseCommandLine() throws UnloggedFailure {
    parseCommandLine(impl);
  }

  private static class MyListGroups extends ListGroups {
    @Option(
      name = "--verbose",
      aliases = {"-v"},
      usage =
          "verbose output format with tab-separated columns for the "
              + "group name, UUID, description, owner group name, "
              + "owner group UUID, and whether the group is visible to all"
    )
    private boolean verboseOutput;

    @Inject
    MyListGroups(
        final GroupCache groupCache,
        final GroupControl.Factory groupControlFactory,
        final GroupControl.GenericFactory genericGroupControlFactory,
        final Provider<IdentifiedUser> identifiedUser,
        final IdentifiedUser.GenericFactory userFactory,
        final GetGroups accountGetGroups,
        final GroupJson json,
        GroupBackend groupBackend) {
      super(
          groupCache,
          groupControlFactory,
          genericGroupControlFactory,
          identifiedUser,
          userFactory,
          accountGetGroups,
          json,
          groupBackend);
    }

    void display(PrintWriter out) throws OrmException, BadRequestException {
      final ColumnFormatter formatter = new ColumnFormatter(out, '\t');
      for (GroupInfo info : get()) {
        formatter.addColumn(MoreObjects.firstNonNull(info.name, "n/a"));
        if (verboseOutput) {
          AccountGroup o =
              info.ownerId != null
                  ? groupCache.get(new AccountGroup.UUID(Url.decode(info.ownerId)))
                  : null;

          formatter.addColumn(Url.decode(info.id));
          formatter.addColumn(Strings.nullToEmpty(info.description));
          formatter.addColumn(o != null ? o.getName() : "n/a");
          formatter.addColumn(o != null ? o.getGroupUUID().get() : "");
          formatter.addColumn(
              Boolean.toString(MoreObjects.firstNonNull(info.options.visibleToAll, Boolean.FALSE)));
        }
        formatter.nextLine();
      }
      formatter.finish();
    }
  }
}
