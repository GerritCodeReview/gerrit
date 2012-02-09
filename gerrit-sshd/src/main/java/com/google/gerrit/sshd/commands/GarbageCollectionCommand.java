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

import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** Runs the Git garbage collection. */
@CommandMetaData(name = "gc", descr = "Run git garbage collection")
public class GarbageCollectionCommand extends BaseCommand {

  @Option(name = "--all", usage = "runs the Git garbage collection for all projects")
  private boolean all;

  @Argument(index = 0, required = false, multiValued = true, metaVar = "NAME",
      usage = "projects for which the Git garbage collection should be run")
  private List<ProjectControl> projects = new ArrayList<ProjectControl>();

  @Inject
  private ProjectCache projectCache;

  @Inject GarbageCollection.Factory garbageCollectionFactory;

  private PrintWriter stdout;

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        stdout = toPrintWriter(out);
        try {
          parseCommandLine();
          verifyCommandLine();
          runGC();
        } finally {
          stdout.flush();
        }
      }
    });
  }

  private void verifyCommandLine() throws UnloggedFailure {
    if (!all && projects.isEmpty()) {
      throw new UnloggedFailure(1,
          "needs projects as command arguments or --all option");
    }
    if (all && !projects.isEmpty()) {
      throw new UnloggedFailure(1,
          "either specify projects as command arguments or use --all option");
    }
  }

  private void runGC() {
    final List<Project.NameKey> projectNames = new ArrayList<Project.NameKey>();
    if (all) {
      for (final Project.NameKey projectName : projectCache.all()) {
        projectNames.add(projectName);
      }
    } else {
      for (final ProjectControl pc : projects) {
        projectNames.add(pc.getProject().getNameKey());
      }
    }

    final GarbageCollectionResult result =
        garbageCollectionFactory.create().run(projectNames, stdout);
    if (result.hasErrors()) {
      for (final GarbageCollectionResult.Error e : result.getErrors()) {
        final String msg;
        switch (e.getType()) {
          case GC_NOT_PERMITTED:
            msg = "fatal: " + e.getWho()
                + " does not have \"Run Garbage Collection\" capability.";
            break;
          case REPOSITORY_NOT_FOUND:
            msg = "error: project \"" + e.getProjectName() + "\" not found";
            break;
          case GC_NOT_SUPPORTED:
            msg = "error: garbage collection not supported for project \""
                + e.getProjectName() + "\"";
            break;
          case GC_FAILED:
            msg = "error: garbage collection for project \"" + e.getProjectName()
                + "\" failed";
            break;
          default:
            msg = "error: garbage collection for project \"" + e.getProjectName()
                + "\" failed: " + e.getType();
        }
        stdout.print(msg + "\n");
      }
    }
  }
}
