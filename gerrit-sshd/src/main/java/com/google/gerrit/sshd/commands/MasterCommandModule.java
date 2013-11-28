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


/** Register the commands a Gerrit server in master mode supports. */
public class MasterCommandModule extends CommandModule {
  @Override
  protected void configure() {
    final CommandName gerrit = Commands.named("gerrit");
    final CommandName testSubmit = Commands.named(gerrit, "test-submit");

    command(gerrit, CreateAccountCommand.class);
    command(gerrit, CreateGroupCommand.class);
    command(gerrit, RenameGroupCommand.class);
    command(gerrit, CreateProjectCommand.class);
    command(gerrit, AdminQueryShell.class);
    command(gerrit, SetReviewersCommand.class);
    command(gerrit, Receive.class);
    command(gerrit, AdminSetParent.class);
    command(gerrit, ReviewCommand.class);
    command(gerrit, SetAccountCommand.class);
    command(gerrit, SetMembersCommand.class);
    command(gerrit, SetProjectCommand.class);

    command(gerrit, "test-submit").toProvider(new DispatchCommandProvider(testSubmit));
    command(testSubmit, TestSubmitRuleCommand.class);
    command(testSubmit, TestSubmitTypeCommand.class);
  }
}
