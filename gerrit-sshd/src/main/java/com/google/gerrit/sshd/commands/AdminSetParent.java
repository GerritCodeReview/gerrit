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
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AdminCommand
final class AdminSetParent extends BaseCommand {
  @Option(name = "--parent", aliases = {"-p"}, metaVar = "NAME", usage = "new parent project")
  private ProjectControl newParent;

  @Argument(index = 0, required = true, multiValued = true, metaVar = "NAME", usage = "projects to modify")
  private List<ProjectControl> children = new ArrayList<ProjectControl>();

  @Inject
  private GitRepositoryManager mgr;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ProjectCache projectCache;

  @Inject
  @GerritPersonIdent
  private PersonIdent serverIdent;

  @Inject
  @WildProjectName
  private Project.NameKey wildProject;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        updateParents();
      }
    });
  }

  private void updateParents() throws Failure {
    final StringBuilder err = new StringBuilder();
    final Set<Project.NameKey> grandParents = new HashSet<Project.NameKey>();
    Project.NameKey newParentKey;

    grandParents.add(wildProject);

    if (newParent != null) {
      newParentKey = newParent.getProject().getNameKey();

      // Catalog all grandparents of the "parent", we want to
      // catch a cycle in the parent pointers before it occurs.
      //
      Project.NameKey gp = newParent.getProject().getParent();
      while (gp != null && grandParents.add(gp)) {
        final ProjectState s = projectCache.get(gp);
        if (s != null) {
          gp = s.getProject().getParent();
        } else {
          break;
        }
      }
    } else {
      // If no parent was selected, set to NULL to use the default.
      //
      newParentKey = null;
    }

    final PersonIdent userIdent = currentUser.newCommitterIdent( //
        serverIdent.getWhen(), //
        serverIdent.getTimeZone());
    for (final ProjectControl pc : children) {
      final Project.NameKey key = pc.getProject().getNameKey();
      final String name = pc.getProject().getName();

      if (wildProject.equals(key)) {
        // Don't allow the wild card project to have a parent.
        //
        err.append("error: Cannot set parent of '" + name + "'\n");
        continue;
      }

      if (grandParents.contains(key)) {
        // Try to avoid creating a cycle in the parent pointers.
        //
        err.append("error: Cycle exists between '" + name + "' and '"
            + (newParentKey != null ? newParentKey.get() : wildProject.get())
            + "'\n");
        continue;
      }

      try {
        Repository git = mgr.openRepository(key);
        try {
          ProjectConfig config = new ProjectConfig();
          config.load(git);

          config.getProject().setParentName(newParentKey.get());
          CommitBuilder commit = new CommitBuilder();
          commit.setAuthor(userIdent);
          commit.setCommitter(serverIdent);
          commit.setMessage("Inherit access from " + newParentKey.get() + "\n");
          if (!config.commit(commit, git)) {
            err.append("error: Could not update project " + name + "\n");
          }
        } finally {
          git.close();
        }
      } catch (RepositoryNotFoundException notFound) {
        err.append("error: Project " + name + " not found\n");
      } catch (IOException e) {
        throw new Failure(1, "Cannot update project " + name, e);
      } catch (ConfigInvalidException e) {
        throw new Failure(1, "Cannot update project " + name, e);
      }
    }

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw new UnloggedFailure(1, err.toString());
    }
  }
}
