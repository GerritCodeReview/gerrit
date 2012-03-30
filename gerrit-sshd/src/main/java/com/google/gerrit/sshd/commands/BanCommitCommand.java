// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.server.git.BanCommit;
import com.google.gerrit.server.git.BanCommitResult;
import com.google.gerrit.server.git.IncompleteUserInfoException;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class BanCommitCommand extends BaseCommand {

  @Option(name = "--reason", aliases = {"-r"}, metaVar = "REASON", usage = "reason for banning the commit")
  private String reason;

  @Argument(index = 0, required = true, metaVar = "PROJECT",
      usage = "name of the project for which the commit should be banned")
  private ProjectControl projectControl;

  @Argument(index = 1, required = true, multiValued = true, metaVar = "COMMIT",
      usage = "commit(s) that should be banned")
  private List<ObjectId> commitsToBan = new ArrayList<ObjectId>();

  @Inject
  private BanCommit.Factory banCommitFactory;

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        BanCommitCommand.this.display();
      }
    });
  }

  private void display() throws Failure {
    try {
      final BanCommitResult result =
          banCommitFactory.create().ban(projectControl, commitsToBan, reason);

      final PrintWriter stdout = toPrintWriter(out);
      try {
        final List<ObjectId> newlyBannedCommits =
            result.getNewlyBannedCommits();
        if (!newlyBannedCommits.isEmpty()) {
          stdout.print("The following commits were banned:\n");
          printCommits(stdout, newlyBannedCommits);
        }

        final List<ObjectId> alreadyBannedCommits =
            result.getAlreadyBannedCommits();
        if (!alreadyBannedCommits.isEmpty()) {
          stdout.print("The following commits were already banned:\n");
          printCommits(stdout, alreadyBannedCommits);
        }

        final List<ObjectId> ignoredIds = result.getIgnoredObjectIds();
        if (!ignoredIds.isEmpty()) {
          stdout.print("The following ids do not represent commits"
              + " and were ignored:\n");
          printCommits(stdout, ignoredIds);
        }
      } finally {
        stdout.flush();
      }
    } catch (PermissionDeniedException e) {
      throw die(e);
    } catch (IOException e) {
      throw die(e);
    } catch (IncompleteUserInfoException e) {
      throw die(e);
    } catch (MergeException e) {
      throw die(e);
    } catch (InterruptedException e) {
      throw die(e);
    }
  }

  private static void printCommits(final PrintWriter stdout,
      final List<ObjectId> commits) {
    boolean first = true;
    for (final ObjectId c : commits) {
      if (!first) {
        stdout.print(",\n");
      }
      stdout.print(c.getName());
      first = false;
    }
    stdout.print("\n\n");
  }
}
