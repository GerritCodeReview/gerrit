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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.projects.ParentInput;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.ListChildProjects;
import com.google.gerrit.server.restapi.project.SetParent;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(
    name = "set-project-parent",
    description = "Change the project permissions are inherited from")
final class SetParentCommand extends SshCommand {
  @Option(
      name = "--parent",
      aliases = {"-p"},
      metaVar = "NAME",
      usage = "new parent project")
  private ProjectState newParent;

  @Option(
      name = "--children-of",
      metaVar = "NAME",
      usage = "parent project for which the child projects should be reparented")
  private ProjectState oldParent;

  @Option(
      name = "--exclude",
      metaVar = "NAME",
      usage = "child project of old parent project which should not be reparented")
  private List<ProjectState> excludedChildren = new ArrayList<>();

  @Argument(
      index = 0,
      required = false,
      multiValued = true,
      metaVar = "NAME",
      usage = "projects to modify")
  private List<ProjectState> children = new ArrayList<>();

  @Inject private ProjectCache projectCache;

  @Inject private ListChildProjects listChildProjects;

  @Inject private SetParent setParent;

  private Project.NameKey newParentKey;

  private static ParentInput parentInput(String parent) {
    ParentInput input = new ParentInput();
    input.parent = parent;
    return input;
  }

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

    if (newParent != null) {
      newParentKey = newParent.getProject().getNameKey();
    }

    final List<Project.NameKey> childProjects =
        children.stream().map(ProjectState::getNameKey).collect(toList());
    if (oldParent != null) {
      try {
        childProjects.addAll(getChildrenForReparenting(oldParent));
      } catch (PermissionBackendException e) {
        throw new Failure(1, "permissions unavailable", e);
      } catch (StorageException | RestApiException e) {
        throw new Failure(1, "failure in request", e);
      }
    }

    for (Project.NameKey nameKey : childProjects) {
      final String name = nameKey.get();
      ProjectState project = projectCache.get(nameKey);
      try {
        setParent.apply(new ProjectResource(project, user), parentInput(newParentKey.get()));
      } catch (AuthException e) {
        err.append("error: insuffient access rights to change parent of '")
            .append(name)
            .append("'\n");
      } catch (ResourceConflictException | ResourceNotFoundException | BadRequestException e) {
        err.append("error: ").append(e.getMessage()).append("'\n");
      } catch (UnprocessableEntityException | IOException e) {
        throw new Failure(1, "failure in request", e);
      } catch (PermissionBackendException e) {
        throw new Failure(1, "permissions unavailable", e);
      }
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
  private List<Project.NameKey> getChildrenForReparenting(ProjectState parent)
      throws PermissionBackendException, StorageException, RestApiException {
    final List<Project.NameKey> childProjects = new ArrayList<>();
    final List<Project.NameKey> excluded = new ArrayList<>(excludedChildren.size());
    for (ProjectState excludedChild : excludedChildren) {
      excluded.add(excludedChild.getProject().getNameKey());
    }
    final List<Project.NameKey> automaticallyExcluded = new ArrayList<>(excludedChildren.size());
    if (newParentKey != null) {
      automaticallyExcluded.addAll(getAllParents(newParentKey));
    }
    for (ProjectInfo child : listChildProjects.apply(new ProjectResource(parent, user))) {
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
    return ps.parents().transform(ProjectState::getNameKey).toSet();
  }
}
