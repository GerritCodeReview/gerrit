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

import static com.google.gerrit.server.i18n.I18n.getText;
import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.ioutil.ColumnFormatter;
import com.google.gerrit.server.restapi.group.ListGroups;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.util.cli.Options;
import com.google.inject.Inject;
import java.util.Optional;
import org.kohsuke.args4j.Option;

@CommandMetaData(
    name = "ls-groups",
    description = "List groups visible to the caller",
    runsAt = MASTER_OR_SLAVE)
public class ListGroupsCommand extends SshCommand {
  @Inject private GroupCache groupCache;

  @Inject @Options public ListGroups listGroups;

  @Option(
      name = "--verbose",
      aliases = {"-v"},
      usage =
          "verbose output format with tab-separated columns for the "
              + "group name, UUID, description, owner group name, "
              + "owner group UUID, and whether the group is visible to all")
  private boolean verboseOutput;

  @Override
  public void run() throws Exception {
    if (listGroups.getUser() != null && !listGroups.getProjects().isEmpty()) {
      throw die(getText("ssh.command.list.groups.user.and.project.options.are.not.compatible"));
    }

    ColumnFormatter formatter = new ColumnFormatter(stdout, '\t');
    for (GroupInfo info : listGroups.get()) {
      formatter.addColumn(MoreObjects.firstNonNull(info.name, "n/a"));
      if (verboseOutput) {
        Optional<InternalGroup> group =
            info.ownerId != null
                ? groupCache.get(AccountGroup.uuid(Url.decode(info.ownerId)))
                : Optional.empty();

        formatter.addColumn(Url.decode(info.id));
        formatter.addColumn(Strings.nullToEmpty(info.description));
        formatter.addColumn(group.map(InternalGroup::getName).orElse("n/a"));
        formatter.addColumn(group.map(g -> g.getGroupUUID().get()).orElse(""));
        formatter.addColumn(
            Boolean.toString(MoreObjects.firstNonNull(info.options.visibleToAll, Boolean.FALSE)));
      }
      formatter.nextLine();
    }
    formatter.finish();
  }
}
