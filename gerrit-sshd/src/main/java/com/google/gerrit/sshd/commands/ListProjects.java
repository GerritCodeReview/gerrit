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

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class ListProjects extends BaseCommand {
  @Inject
  private ReviewDb db;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  @WildProjectName
  private Project.NameKey wildProject;

  @Option(name = "--show-branch", aliases = {"-b"},
      usage = "displays the sha of each project in the specified branch")
  private String showBranch;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        ListProjects.this.display();
      }
    });
  }

  private final ObjectId getObjectIdForBranch(Project.NameKey projectName,
      String branch) {
    try {
      final Repository r = repoManager.openRepository(projectName.get());
      try {
        Ref ref = r.getRef(branch);
        return ref == null ? null : ref.getObjectId();
      } finally {
        r.close();
      }
    } catch (IOException ioe) {
      return null;
    }
  }

  private void display() throws Failure {
    final PrintWriter stdout = toPrintWriter(out);
    try {
      for (final Project p : db.projects().all()) {
        if (p.getNameKey().equals(wildProject)) {
          // This project "doesn't exist". At least not as a repository.
          //
          continue;
        }

        final ProjectState e = projectCache.get(p.getNameKey());
        if (e != null && e.controlFor(currentUser).isVisible()) {
          if (showBranch != null) {
            ObjectId id = getObjectIdForBranch(p.getNameKey(), showBranch);
            if (id != null) {
              stdout.print(id.name() + " ");
              stdout.print(p.getName());
              stdout.println();
          }
        }
      }
    } catch (OrmException e) {
      throw new Failure(1, "fatal: database error", e);
    } finally {
      stdout.flush();
    }
  }
}
