// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.changedetail.MoveChange;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class MoveChangeCommand extends BaseCommand {
  @Option(name = "--message", aliases = "-m", metaVar = "MESSAGE",
      usage = "message published as a comment on the moved change")
  private String changeComment;

  @Argument(index = 0, required = true, metaVar = "CHANGE-NUM")
  private String changeNum;

  @Argument(index = 1, required = true, metaVar = "NEW-BRANCH")
  private String branch;

  @Inject
  private final MoveChange.Factory moveChangeFactory = null;

  @Override
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        Change.Id changeId = Change.Id.parse(changeNum);
        try {
          moveChangeFactory.create(changeId, branch, changeComment).call();
        } catch (Exception e) {
          throw new UnloggedFailure(1, "Could not move " + changeNum + " to "
              + branch + ": " + e.getMessage());
        }
      }
    });
  }
}
