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

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.CommandName;
import com.google.gerrit.sshd.Commands;
import com.google.gerrit.sshd.DispatchCommandProvider;
import com.google.gerrit.sshd.SuExec;


/** Register the basic commands any Gerrit server should support. */
public class DefaultCommandModule extends CommandModule {
  @Override
  protected void configure() {
    final CommandName git = Commands.named("git");
    final CommandName gerrit = Commands.named("gerrit");
    final CommandName plugin = Commands.named(gerrit, "plugin");

    // The following commands can be ran on a server in either Master or Slave
    // mode. If a command should only be used on a server in one mode, but not
    // both, it should be bound in both MasterCommandModule and
    // SlaveCommandModule.

    command(gerrit).toProvider(new DispatchCommandProvider(gerrit));
    command(gerrit, "ban-commit").to(BanCommitCommand.class);
    command(gerrit, "flush-caches").to(FlushCaches.class);
    command(gerrit, "ls-projects").to(ListProjectsCommand.class);
    command(gerrit, "ls-groups").to(ListGroupsCommand.class);
    command(gerrit, "query").to(Query.class);
    command(gerrit, "show-caches").to(ShowCaches.class);
    command(gerrit, "show-connections").to(ShowConnections.class);
    command(gerrit, "show-queue").to(ShowQueue.class);
    command(gerrit, "stream-events").to(StreamEvents.class);
    command(gerrit, "version").to(VersionCommand.class);

    command(gerrit, "plugin").toProvider(new DispatchCommandProvider(plugin));
    command(plugin, "ls").to(PluginLsCommand.class);
    command(plugin, "enable").to(PluginEnableCommand.class);
    command(plugin, "install").to(PluginInstallCommand.class);
    command(plugin, "reload").to(PluginReloadCommand.class);
    command(plugin, "remove").to(PluginRemoveCommand.class);
    command(plugin, "add").to(Commands.key(plugin, "install"));
    command(plugin, "rm").to(Commands.key(plugin, "remove"));

    command(git).toProvider(new DispatchCommandProvider(git));
    command(git, "receive-pack").to(Commands.key(gerrit, "receive-pack"));
    command(git, "upload-pack").to(Upload.class);

    command("ps").to(ShowQueue.class);
    command("kill").to(KillCommand.class);
    command("scp").to(ScpCommand.class);

    // Honor the legacy hyphenated forms as aliases for the non-hyphenated forms
    //
    command("git-upload-pack").to(Commands.key(git, "upload-pack"));
    command("git-receive-pack").to(Commands.key(git, "receive-pack"));
    command("gerrit-receive-pack").to(Commands.key(git, "receive-pack"));

    command("suexec").to(SuExec.class);

    install(new LifecycleModule() {
      @Override
      protected void configure() {
        listener().to(ShowCaches.StartupListener.class);
      }
    });
  }
}
