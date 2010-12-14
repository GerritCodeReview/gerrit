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
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.reviewdb.Project.Status;
import com.google.gerrit.server.project.PerformDeleteProject;
import com.google.gerrit.server.project.PerformDeleteProjectImpl;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@AdminCommand
public class AdminPruneProjects extends BaseCommand {
  @Option(name = "--force-prune-right-now", required = true, usage = "force prune projects")
  private boolean forcePrune;

  @Inject
  private PerformDeleteProjectImpl.Factory performDeleteProject;

  @Inject
  private ReviewDb db;

  @Override
  public void start(Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        if (forcePrune) {
          deletePruneProjects();
        }
      }
    });
  }

  private void deletePruneProjects() throws OrmException {
    final List<NameKey> projectsToDelete = new ArrayList<NameKey>();

    for (Project p : db.projects().byStatus(Status.PRUNE.getCode())) {
      projectsToDelete.add(p.getNameKey());
    }

    final PerformDeleteProject perfDeleteProject =
        performDeleteProject.create(projectsToDelete);

    perfDeleteProject.deleteProjects();
  }
}
