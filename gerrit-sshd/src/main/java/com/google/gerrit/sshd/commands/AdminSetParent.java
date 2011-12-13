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

import com.google.gerrit.common.data.UpdateParentsResult;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.UpdateParents;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AdminCommand
final class AdminSetParent extends BaseCommand {
  @Option(name = "--parent", aliases = {"-p"}, metaVar = "NAME", usage = "new parent project")
  private ProjectControl newParent;

  @Option(name = "--children-of", metaVar = "NAME",
      usage = "parent project for which the child projects should be reparented")
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
  private UpdateParents.Factory updateParentsFactory;

  @Inject
  private AllProjectsName allProjectsName;

  private PrintWriter stdout;
  private Project.NameKey newParentKey = null;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        stdout = toPrintWriter(out);
        try {
          parseCommandLine();
          updateParents();
        } finally {
          stdout.flush();
        }
      }
    });
  }

  private void updateParents() throws Failure {
    if (oldParent == null && children.isEmpty()) {
      throw new UnloggedFailure(1, "fatal: child projects have to be specified as " +
                                   "arguments or the --children-of option has to be set");
    }
    if (oldParent == null && !excludedChildren.isEmpty()) {
      throw new UnloggedFailure(1, "fatal: --exclude can only be used together " +
                                   "with --children-of");
    }

    final List<Project> childProjects = new ArrayList<Project>();
    for (final ProjectControl pc : children) {
      childProjects.add(pc.getProject());
    }
    if (oldParent != null) {
      childProjects.addAll(getChildrenForReparenting(oldParent));
    }

    final UpdateParentsResult result =
        updateParentsFactory.create(childProjects, newParent.getProject())
            .updateParents();
    for (final UpdateParentsResult.Error error : result.getErrors()) {
      String message;
      switch (error.getType()) {
        case UPDATE_NOT_PERMITTED:
          message = "not permitted to update the parent project of {0}";
          break;
        case PROJECT_NOT_FOUND:
          message = "project {0} not found";
          break;
        case PARENT_CANNOT_BE_SET:
          message = "parent project for {0} cannot be set";
          break;
        case CYCLE_EXISTS:
          message = "cycle exists between {0} and {1}";
          break;
        case PROJECT_UPDATE_FAILED:
        default:
          message = "updating project {0} failed";
      }
      try {
        err.write(("error: "
            + MessageFormat.format(message, error.getProjectName().get(),
              error.getParentProjectName().get()) + "\n").getBytes(ENC));
      } catch (IOException e) {
      }
    }
  }

  /**
   * Returns the children of the specified parent project that should be
   * reparented. The returned list of child projects does not contain projects
   * that were specified to be excluded from reparenting.
   */
  private List<Project> getChildrenForReparenting(final ProjectControl parent) {
    final List<Project> childProjects = new ArrayList<Project>();
    final List<Project.NameKey> excluded =
      new ArrayList<Project.NameKey>(excludedChildren.size());
    for (final ProjectControl excludedChild : excludedChildren) {
      excluded.add(excludedChild.getProject().getNameKey());
    }
    final List<Project.NameKey> automaticallyExcluded =
      new ArrayList<Project.NameKey>(excludedChildren.size());
    if (newParentKey != null) {
      automaticallyExcluded.addAll(getAllParents(newParentKey));
    }
    for (final Project child : getChildren(parent.getProject().getNameKey())) {
      final Project.NameKey childName = child.getNameKey();
      if (!excluded.contains(childName)) {
        if (!automaticallyExcluded.contains(childName)) {
          childProjects.add(child);
        } else {
          stdout.println("Automatically excluded '" + childName + "' " +
                         "from reparenting because it is in the parent " +
                         "line of the new parent '" + newParentKey + "'.");
        }
      }
    }
    return childProjects;
  }

  private Set<Project.NameKey> getAllParents(final Project.NameKey projectName) {
    final Set<Project.NameKey> parents = new HashSet<Project.NameKey>();
    Project.NameKey p = projectName;
    while (p != null && parents.add(p)) {
      final ProjectState e = projectCache.get(p);
      if (e == null) {
        // If we can't get it from the cache, pretend it's not present.
        break;
      }
      p = getParentName(e.getProject());
    }
    return parents;
  }

  private List<Project> getChildren(final Project.NameKey parentName) {
    final List<Project> childProjects = new ArrayList<Project>();
    for (final Project.NameKey projectName : projectCache.all()) {
      final ProjectState e = projectCache.get(projectName);
      if (e == null) {
        // If we can't get it from the cache, pretend it's not present.
        continue;
      }

      if (parentName.equals(getParentName(e.getProject()))) {
        childProjects.add(e.getProject());
      }
    }
    return childProjects;
  }

  /**
   * Returns the project parent name.
   *
   * @return Project parent name, <code>null</code> for the 'All-Projects' root
   *         project
   */
  private Project.NameKey getParentName(final Project project) {
    if (project.getParent() != null) {
      return project.getParent();
    }

    if (project.getNameKey().equals(allProjectsName)) {
      return null;
    }

    return allProjectsName;
  }
}
