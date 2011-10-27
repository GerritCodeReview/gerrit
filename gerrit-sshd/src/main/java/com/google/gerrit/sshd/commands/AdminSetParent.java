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
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
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
  private ProjectCache projectCache;

  @Inject
  private MetaDataUpdate.User metaDataUpdateFactory;

  @Inject
  private AllProjectsName allProjectsName;

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

    grandParents.add(allProjectsName);

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

    for (final ProjectControl pc : children) {
      final Project.NameKey key = pc.getProject().getNameKey();
      final String name = pc.getProject().getName();

      if (allProjectsName.equals(key)) {
        // Don't allow the wild card project to have a parent.
        //
        err.append("error: Cannot set parent of '" + name + "'\n");
        continue;
      }

      if (grandParents.contains(key) || key.equals(newParentKey)) {
        // Try to avoid creating a cycle in the parent pointers.
        //
        err.append("error: Cycle exists between '" + name + "' and '"
            + (newParentKey != null ? newParentKey.get() : allProjectsName.get())
            + "'\n");
        continue;
      }

      try {
        MetaDataUpdate md = metaDataUpdateFactory.create(key);
        try {
          ProjectConfig config = ProjectConfig.read(md);
          config.getProject().setParentName(newParentKey);
          md.setMessage("Inherit access from "
              + (newParentKey != null ? newParentKey.get() : allProjectsName.get()) + "\n");
          if (!config.commit(md)) {
            err.append("error: Could not update project " + name + "\n");
          }
        } finally {
          md.close();
        }
      } catch (RepositoryNotFoundException notFound) {
        err.append("error: Project " + name + " not found\n");
      } catch (IOException e) {
        throw new Failure(1, "Cannot update project " + name, e);
      } catch (ConfigInvalidException e) {
        throw new Failure(1, "Cannot update project " + name, e);
      }

      projectCache.evict(pc.getProject());
    }

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw new UnloggedFailure(1, err.toString());
    }
  }
}
