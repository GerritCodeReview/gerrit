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
import com.google.gerrit.server.project.CreateProjectHierarchy;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectNode;
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

  @Option(name = "--old-parent", metaVar = "NAME", usage = "old parent project")
  private ProjectControl oldParent;

  @Option(name = "--exclude", metaVar = "NAME",
      usage = "child project of old parent project which should not be reparented")
  private List<ProjectControl> excludedChildren = new ArrayList<ProjectControl>();

  @Argument(index = 0, required = false, multiValued = true, metaVar = "NAME",
      usage = "projects to modify")
  private List<ProjectControl> children = new ArrayList<ProjectControl>();

  @Inject
  private ProjectCache projectCache;

  @Inject
  private MetaDataUpdate.User metaDataUpdateFactory;

  @Inject
  private AllProjectsName allProjectsName;

  @Inject
  private CreateProjectHierarchy.Factory createProjectHierarchyFactory;

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
    if (oldParent == null && children.isEmpty()) {
      throw new UnloggedFailure(1, "fatal: child projects have to be specified as arguments or the --old-parent option has to be set");
    }
    if (oldParent == null && !excludedChildren.isEmpty()) {
      throw new UnloggedFailure(1, "fatal: --exclude can only be used together with --old-parent");
    }

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

    final List<Project> childProjects = new ArrayList<Project>();
    for (final ProjectControl pc : children) {
      childProjects.add(pc.getProject());
    }
    if (oldParent != null) {
      final List<Project.NameKey> excluded =
          new ArrayList<Project.NameKey>(excludedChildren.size() + 1);
      if (newParentKey != null) {
        excluded.add(newParentKey);
      }
      for (final ProjectControl excludedChild : excludedChildren) {
        excluded.add(excludedChild.getProject().getNameKey());
      }
      final ProjectNode oldParentProject =
          createProjectHierarchyFactory.create().getProjectHierarchy(
              oldParent.getProject().getNameKey());
      for (final ProjectNode childProject : oldParentProject.getChildren()) {
        if (!excluded.contains(childProject.getProject().getNameKey())) {
          childProjects.add(childProject.getProject());
        }
      }
    }

    for (final Project project : childProjects) {
      final String name = project.getName();
      final Project.NameKey nameKey = project.getNameKey();

      if (allProjectsName.equals(nameKey)) {
        // Don't allow the wild card project to have a parent.
        //
        err.append("error: Cannot set parent of '" + name + "'\n");
        continue;
      }

      if (grandParents.contains(nameKey) || nameKey.equals(newParentKey)) {
        // Try to avoid creating a cycle in the parent pointers.
        //
        err.append("error: Cycle exists between '" + name + "' and '"
            + (newParentKey != null ? newParentKey.get() : allProjectsName.get())
            + "'\n");
        continue;
      }

      try {
        MetaDataUpdate md = metaDataUpdateFactory.create(nameKey);
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

      projectCache.evict(project);
    }

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw new UnloggedFailure(1, err.toString());
    }
  }
}
