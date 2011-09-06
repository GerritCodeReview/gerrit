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

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.server.account.PerformRenameGroup;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;

import java.io.IOException;

public class RenameGroupCommand extends BaseCommand {

  @Argument(index = 0, required = true, metaVar = "GROUP", usage = "name of the group to be renamed")
  private String groupName;

  @Argument(index = 1, required = true, metaVar = "NEWNAME", usage = "new name of the group")
  private String newGroupName;

  @Inject
  private PerformRenameGroup.Factory performRenameGroupFactory;

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        try {
          performRenameGroupFactory.create().renameGroup(groupName, newGroupName);
        } catch (OrmException e) {
          throw die(e);
        } catch (NameAlreadyUsedException e) {
          throw die(e);
        } catch (NoSuchGroupException e) {
          throw die(e);
        }
      }
    });
  }
}
