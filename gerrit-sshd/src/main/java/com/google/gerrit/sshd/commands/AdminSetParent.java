// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ProjectAccess;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AdminCommand
final class AdminSetParent extends BaseCommand {
  @Option(name = "--parent", required = false, aliases = {"-p"}, usage = "name of the parent project")
  private ProjectControl projectControl;

  @Argument(index = 0, required = true, multiValued = true, usage = "projects to set the parent")
  private List<ProjectControl> children = new ArrayList<ProjectControl>();

  @Inject
  private ReviewDb db;

  @Inject
  private ProjectCache projectCache;

  @Inject
  @WildProjectName
  private Project.NameKey wildProject;

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        PrintWriter p = toPrintWriter(err);

        try {
          parseCommandLine();

          final ProjectAccess projectAccess = db.projects();
          Project.NameKey parentNameKey;
          final List<Project.NameKey> grandParents = new ArrayList<Project.NameKey>();

          if (projectControl != null) {
            parentNameKey = new Project.NameKey(projectControl.getProject().getName());
            Project.NameKey grandParent = projectControl.getProject().getParent();

            // stores all grandparents of the "parent", in order to
            // avoid an infinite loop on retrieving the project rights
            while (grandParent != null && !grandParent.equals(wildProject)) {
              grandParents.add(grandParent);
              grandParent = projectCache.get(grandParent).getProject().getParent();
            }
          } else {
            // if there is no parent, "All projects" is the default
            parentNameKey = new Project.NameKey(wildProject.get());
          }

          for (ProjectControl childPC : children) {
            Project child = childPC.getProject();
            final Project.NameKey childNameKey = new Project.NameKey(child.getName());

            // If the child project doesn't exist, just skip it
            if (!childNameKey.equals(wildProject)
                && !childNameKey.equals(parentNameKey) && (!grandParents.contains(childNameKey))) {
              child.setParent(parentNameKey);
              projectAccess.update(Collections.singleton(child));
            }
            else {
              p.print("It was not possible to set the parent project "
                  + parentNameKey.get() + " to the following project : " + child.getName() + "\n");
              p.flush();
            }
          }

          // invalidates all projects on cache
          projectCache.evictAll();

        } catch (Throwable e) {
          p.print("Error when trying to set a parent to a project: "
              + e.getMessage() + "\n");
          p.flush();
        }

      }
    });

  }
}