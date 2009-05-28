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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.PushAllProjectsOp;
import com.google.gerrit.git.PushQueue;
import com.google.gerrit.git.WorkQueue;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Force a project to replicate, again. */
class AdminReplicate extends AbstractCommand {
  @Option(name = "--all", usage = "push all known projects")
  private boolean all;

  @Option(name = "--url", metaVar = "PATTERN", usage = "pattern to match URL on")
  private String urlMatch;

  @Argument(index = 0, multiValued = true, metaVar = "PROJECT", usage = "project name")
  private List<String> projectNames;

  @Override
  protected void run() throws Failure {
    assertIsAdministrator();

    if (all && projectNames.size() > 0) {
      throw new Failure(1, "error: cannot combine --all and PROJECT");
    }

    if (!PushQueue.isReplicationEnabled()) {
      throw new Failure(1, "error: replication not enabled");
    }

    if (all) {
      WorkQueue.schedule(new PushAllProjectsOp(urlMatch), 0, TimeUnit.SECONDS);

    } else {
      for (final String name : projectNames) {
        final Project.NameKey key = new Project.NameKey(name);
        if (Common.getProjectCache().get(key) != null) {
          PushQueue.scheduleFullSync(key, urlMatch);
        } else {
          throw new Failure(1, "error: '" + name + "': not a Gerrit project");
        }
      }
    }
  }
}
