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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
  name = "set-project-parent",
  description = "Change the project permissions are inherited from"
)
final class AdminSetParent extends SshCommand {
  private static final Logger log = LoggerFactory.getLogger(AdminSetParent.class);

  @Option(
    name = "--parent",
    aliases = {"-p"},
    metaVar = "NAME",
    usage = "new parent project"
  )
  private ProjectControl newParent;

  @Option(
    name = "--children-of",
    metaVar = "NAME",
    usage = "parent project for which the child projects should be reparented"
  )
  private ProjectControl oldParent;

  @Option(
    name = "--exclude",
    metaVar = "NAME",
    usage = "child project of old parent project which should not be reparented"
  )
  private List<ProjectControl> excludedChildren = new ArrayList<>();

  @Argument(
    index = 0,
    required = false,
    multiValued = true,
    metaVar = "NAME",
    usage = "projects to modify"
  )
  private List<ProjectControl> children = new ArrayList<>();

  @Inject private ProjectCache projectCache;

  @Inject private MetaDataUpdate.User metaDataUpdateFactory;

  @Inject private AllProjectsName allProjectsName;

  @Inject private ListChildProjects listChildProjects;

  private Project.NameKey newParentKey;

  @Override
  protected void run() throws Failure {
    if (oldParent == null && children.isEmpty()) {
      throw die(
          "child projects have to be specified as "
              + "arguments or the --children-of option has to be set");
    }
    if (oldParent == null && !excludedChildren.isEmpty()) {
      throw die("--exclude can only be used together with --children-of");
    }

    final StringBuilder err = new StringBuilder();
    final Set<Project.NameKey> grandParents = new HashSet<>();

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
    }

    final List<Project.NameKey> childProjects = new ArrayList<>();
    for (ProjectControl pc : children) {
      childProjects.add(pc.getProject().getNameKey());
    }
    if (oldParent != null) {
      try {
        childProjects.addAll(getChildrenForReparenting(oldParent));
      } catch (PermissionBackendException e) {
        throw new Failure(1, "permissions unavailable", e);
      }
    }

    for (Project.NameKey nameKey : childProjects) {
      final String name = nameKey.get();

      if (allProjectsName.equals(nameKey)) {
        // Don't allow the wild card project to have a parent.
        //
        err.append("error: Cannot set parent of '").append(name).append("'\n");
        continue;
      }

      if (grandParents.contains(nameKey) || nameKey.equals(newParentKey)) {
        // Try to avoid creating a cycle in the parent pointers.
        //
        err.append("error: Cycle exists between '")
            .append(name)
            .append("' and '")
            .append(newParentKey != null ? newParentKey.get() : allProjectsName.get())
            .append("'\n");
        continue;
      }

      try (MetaDataUpdate md = metaDataUpdateFactory.create(nameKey)) {
        ProjectConfig config = ProjectConfig.read(md);
        config.getProject().setParentName(newParentKey);
        md.setMessage(
            "Inherit access from "
                + (newParentKey != null ? newParentKey.get() : allProjectsName.get())
                + "\n");
        config.commit(md);
      } catch (RepositoryNotFoundException notFound) {
        err.append("error: Project ").append(name).append(" not found\n");
      } catch (IOException | ConfigInvalidException e) {
        final String msg = "Cannot update project " + name;
        log.error(msg, e);
        err.append("error: ").append(msg).append("\n");
      }

      projectCache.evict(nameKey);
    }

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw die(err.toString());
    }
  }

  /**
   * Returns the children of the specified parent project that should be reparented. The returned
   * list of child projects does not contain projects that were specified to be excluded from
   * reparenting.
   */
  private List<Project.NameKey> getChildrenForReparenting(ProjectControl parent)
      throws PermissionBackendException {
    final List<Project.NameKey> childProjects = new ArrayList<>();
    final List<Project.NameKey> excluded = new ArrayList<>(excludedChildren.size());
    for (ProjectControl excludedChild : excludedChildren) {
      excluded.add(excludedChild.getProject().getNameKey());
    }
    final List<Project.NameKey> automaticallyExcluded = new ArrayList<>(excludedChildren.size());
    if (newParentKey != null) {
      automaticallyExcluded.addAll(getAllParents(newParentKey));
    }
    for (ProjectInfo child : listChildProjects.apply(new ProjectResource(parent))) {
      final Project.NameKey childName = new Project.NameKey(child.name);
      if (!excluded.contains(childName)) {
        if (!automaticallyExcluded.contains(childName)) {
          childProjects.add(childName);
        } else {
          stdout.println(
              "Automatically excluded '"
                  + childName
                  + "' "
                  + "from reparenting because it is in the parent "
                  + "line of the new parent '"
                  + newParentKey
                  + "'.");
        }
      }
    }
    return childProjects;
  }

  private Set<Project.NameKey> getAllParents(Project.NameKey projectName) {
    ProjectState ps = projectCache.get(projectName);
    if (ps == null) {
      return Collections.emptySet();
    }
    return ps.parents().transform(s -> s.getProject().getNameKey()).toSet();
  }
}
