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

import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.CommandName;
import com.google.gerrit.sshd.Commands;
import com.google.gerrit.sshd.DispatchCommandProvider;
import com.google.gerrit.sshd.SuExec;


/** Register the commands a Gerrit server supports. */
public class DefaultCommandModule extends CommandModule {
  public DefaultCommandModule(boolean slave) {
    slaveMode = slave;
  }

  @Override
  protected void configure() {
    final CommandName git = Commands.named("git");
    final CommandName gerrit = Commands.named("gerrit");
    final CommandName logging = Commands.named(gerrit, "logging");
    final CommandName plugin = Commands.named(gerrit, "plugin");
    final CommandName testSubmit = Commands.named(gerrit, "test-submit");

    command(gerrit).toProvider(new DispatchCommandProvider(gerrit));
    command(gerrit, AproposCommand.class);
    command(gerrit, BanCommitCommand.class);
    command(gerrit, CloseConnection.class);
    command(gerrit, FlushCaches.class);
    command(gerrit, ListProjectsCommand.class);
    command(gerrit, ListMembersCommand.class);
    command(gerrit, ListGroupsCommand.class);
    command(gerrit, LsUserRefs.class);
    command(gerrit, Query.class);
    command(gerrit, ShowCaches.class);
    command(gerrit, ShowConnections.class);
    command(gerrit, ShowQueue.class);
    command(gerrit, StreamEvents.class);
    command(gerrit, VersionCommand.class);
    command(gerrit, GarbageCollectionCommand.class);
    command(gerrit, "plugin").toProvider(new DispatchCommandProvider(plugin));

    command(plugin, PluginLsCommand.class);
    command(plugin, PluginEnableCommand.class);
    command(plugin, PluginInstallCommand.class);
    command(plugin, PluginReloadCommand.class);
    command(plugin, PluginRemoveCommand.class);
    alias(plugin, "add", PluginInstallCommand.class);
    alias(plugin, "rm", PluginRemoveCommand.class);

    command(git).toProvider(new DispatchCommandProvider(git));

    command("ps").to(ShowQueue.class);
    command("kill").to(KillCommand.class);
    command("scp").to(ScpCommand.class);

    // Honor the legacy hyphenated forms as aliases for the non-hyphenated forms
    command("git-upload-pack").to(Commands.key(git, "upload-pack"));
    command(git, "upload-pack").to(Upload.class);
    command("git-upload-archive").to(Commands.key(git, "upload-archive"));
    command(git, "upload-archive").to(UploadArchive.class);
    command("suexec").to(SuExec.class);
    listener().to(ShowCaches.StartupListener.class);

    // The following commands can only be run on a server in Master mode
    command(gerrit, CreateAccountCommand.class);
    command(gerrit, CreateGroupCommand.class);
    command(gerrit, CreateProjectCommand.class);
    command(gerrit, SetHeadCommand.class);
    command(gerrit, AdminQueryShell.class);
    if (slaveMode) {
      command("git-receive-pack").to(NotSupportedInSlaveModeFailureCommand.class);
      command("gerrit-receive-pack").to(NotSupportedInSlaveModeFailureCommand.class);
      command(git, "receive-pack").to(NotSupportedInSlaveModeFailureCommand.class);
      command(gerrit, "test-submit").to(NotSupportedInSlaveModeFailureCommand.class);
    } else {
      command("git-receive-pack").to(Commands.key(git, "receive-pack"));
      command("gerrit-receive-pack").to(Commands.key(git, "receive-pack"));
      command(git, "receive-pack").to(Commands.key(gerrit, "receive-pack"));
      command(gerrit, "test-submit").toProvider(
          new DispatchCommandProvider(testSubmit));
    }
    command(gerrit, Receive.class);

    command(gerrit, RenameGroupCommand.class);
    command(gerrit, ReviewCommand.class);
    command(gerrit, SetProjectCommand.class);
    command(gerrit, SetReviewersCommand.class);

    command(gerrit, SetMembersCommand.class);
    command(gerrit, CreateBranchCommand.class);
    command(gerrit, SetAccountCommand.class);
    command(gerrit, AdminSetParent.class);

    command(gerrit, CreateAccountCommand.class);
    command(testSubmit, TestSubmitRuleCommand.class);
    command(testSubmit, TestSubmitTypeCommand.class);

    command(logging).toProvider(new DispatchCommandProvider(logging));
    command(logging, SetLoggingLevelCommand.class);
    command(logging, ListLoggingLevelCommand.class);
    alias(logging, "ls", ListLoggingLevelCommand.class);
    alias(logging, "set", SetLoggingLevelCommand.class);
  }
}
