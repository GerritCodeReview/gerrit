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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.PushAllProjectsOp;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Force a project to replicate, again. */
final class Replicate extends BaseCommand {
  @Option(name = "--all", usage = "push all known projects")
  private boolean all;

  @Option(name = "--url", metaVar = "PATTERN", usage = "pattern to match URL on")
  private String urlMatch;

  @Argument(index = 0, multiValued = true, metaVar = "PROJECT", usage = "project name")
  private List<String> projectNames = new ArrayList<String>(2);

  @Inject
  IdentifiedUser currentUser;

  @Inject
  private PushAllProjectsOp.Factory pushAllOpFactory;

  @Inject
  private ReplicationQueue replication;

  @Inject
  private ProjectCache projectCache;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        if (!currentUser.getCapabilities().canStartReplication()) {
          String msg = String.format(
            "fatal: %s does not have \"Start Replication\" capability.",
            currentUser.getUserName());
          throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
        }

        parseCommandLine();
        Replicate.this.schedule();
      }
    });
  }

  private void schedule() throws Failure {
    if (all && projectNames.size() > 0) {
      throw new Failure(1, "error: cannot combine --all and PROJECT");
    }

    if (!replication.isEnabled()) {
      throw new Failure(1, "error: replication not enabled");
    }

    if (all) {
      pushAllOpFactory.create(urlMatch).start(0, TimeUnit.SECONDS);

    } else {
      for (final String name : projectNames) {
        final Project.NameKey key = new Project.NameKey(name);
        if (projectCache.get(key) != null) {
          replication.scheduleFullSync(key, urlMatch);
        } else {
          throw new Failure(1, "error: '" + name + "': not a Gerrit project");
        }
      }
    }
  }
}
