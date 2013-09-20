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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "delete-group", description = "Delete specific group")
public final class DeleteGroupCommand extends SshCommand {
  @Argument(index = 0, required = true, metaVar = "NAME", usage = "group to delete")
  private String groupName;

  @Option(name = "--yes-really-delete", usage = "confirmation to delete the group")
  private boolean yesReallyDelete;

  @Option(name = "--force", aliases = {"-f"}, usage = "delete the group even if it is used")
  private boolean force = false;

  private final ReviewDb db;
  private final CurrentUser currentUser;
  private final GroupCache groupCache;
  private final ProjectCache projectCache;
  private final GroupControl.Factory groupControlFactory;

  @Inject
  protected DeleteGroupCommand(ReviewDb db,
      CurrentUser currentUser,
      ProjectCache projectCache,
      GroupControl.Factory groupControlFactory,
      GroupCache groupCache) {
    this.db = db;
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
  }

  @Override
  public void run() throws UnloggedFailure, SQLException, OrmException {
    final AccountGroup.NameKey groupNameKey =
        new AccountGroup.NameKey(groupName);
    final AccountGroup group = groupCache.get(groupNameKey);
    if (group == null) {
      throw die("Group not found: " + groupName);
    }

    List<String> aclUsedProjects = Lists.newArrayList();
    for (final Project.NameKey projectName : projectCache.all()) {
      final ProjectState e = projectCache.get(projectName);
      if (e == null) {
        continue;
      }

      final ProjectControl pctl = e.controlFor(currentUser);
      if (!groupControlFactory.controlFor(group).isVisible()) {
        throw die(String.format("Group %s is not visible",
            group.getName()));
      }
      if (pctl.getLocalGroups().contains(
          GroupReference.forGroup(groupCache.get(group.getGroupUUID())))) {
        aclUsedProjects.add(projectName.get());
      }
    }

    if (!yesReallyDelete) {
      StringBuilder msgBuilder = new StringBuilder();
      msgBuilder.append("Really delete group: ");
      msgBuilder.append(groupName);
      msgBuilder.append("?\n");
      msgBuilder.append("This is an operation which permanently deletes");
      msgBuilder.append("data. This cannot be undone!\n");
      msgBuilder.append("If you are sure you wish to delete this group, ");
      msgBuilder.append("re-run\n");
      msgBuilder.append("with the --yes-really-delete flag.\n");
      throw new UnloggedFailure(msgBuilder.toString());
    }

    if (!aclUsedProjects.isEmpty()) {
      // TODO(davido): implement auto ACL deletion for each project
      // if --force is passed
      String msg = String.format(
          "Cannot delete group %s,"
          + " because it is used in this/these project(s): %s.\n"
          + "Delete the ACL from the WebUI and try again.\n"
          + "(Sadly enouph, automagic ACL deletion isn't implemented yet!)",
          group.getName(), Joiner.on(",").join(aclUsedProjects));
      throw new UnloggedFailure(msg);
    }

    delete(group);
    groupCache.evict(group);
  }

  private void delete(AccountGroup group) throws SQLException, OrmException {
    Connection conn = ((JdbcSchema) db).getConnection();
    conn.setAutoCommit(false);
    try {
      atomicDelete(group);
      conn.commit();
    } finally {
      conn.setAutoCommit(true);
    }
  }

  // TODO(davido):
  // handle ACCOUNT_GROUP_INCLUDES_BY_UUID && ACCOUNT_GROUP_INCLUDES_BY_UUID_AUDIT
  private void atomicDelete(AccountGroup group) throws OrmException {
    db.accountGroupMembers().delete(db.accountGroupMembers()
        .byGroup(group.getId()));
    AccountGroupName accountGroupName =
        db.accountGroupNames().get(new AccountGroup.NameKey(group.getName()));
    db.accountGroupNames().delete(Collections.singleton(accountGroupName));
    db.accountGroups().delete(db.accountGroups().byUUID(group.getGroupUUID()));
    db.accountGroupMembersAudit().delete(db.accountGroupMembersAudit()
        .byGroupId(group.getId()));
  }
}
