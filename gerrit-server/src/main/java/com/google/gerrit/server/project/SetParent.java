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

package com.google.gerrit.server.project;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.SetParent.Input;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

import java.io.IOException;

class SetParent implements RestModifyView<ProjectResource, Input> {
  static class Input {
    @DefaultInput
    String parent;
    String commitMessage;
  }

  private final ProjectCache cache;
  private final MetaDataUpdate.Server updateFactory;
  private final AllProjectsName allProjects;

  @Inject
  SetParent(ProjectCache cache,
      MetaDataUpdate.Server updateFactory,
      AllProjectsName allProjects) {
    this.cache = cache;
    this.updateFactory = updateFactory;
    this.allProjects = allProjects;
  }

  @Override
  public String apply(final ProjectResource rsrc, Input input) throws AuthException,
      BadRequestException, ResourceConflictException,
      ResourceNotFoundException, UnprocessableEntityException, IOException {
    ProjectControl ctl = rsrc.getControl();
    IdentifiedUser user = (IdentifiedUser) ctl.getCurrentUser();
    if (!user.getCapabilities().canAdministrateServer()) {
      throw new AuthException("not administrator");
    }

    if (rsrc.getNameKey().equals(allProjects)) {
      throw new ResourceConflictException("cannot set parent of "
          + allProjects.get());
    }

    input.parent = Strings.emptyToNull(input.parent);
    if (input.parent != null) {
      ProjectState parent = cache.get(new Project.NameKey(input.parent));
      if (parent == null) {
        throw new UnprocessableEntityException("parent project " + input.parent
            + " not found");
      }

      if (Iterables.tryFind(parent.tree(), new Predicate<ProjectState>() {
        @Override
        public boolean apply(ProjectState input) {
          return input.getProject().getNameKey().equals(rsrc.getNameKey());
        }
      }).isPresent()) {
        throw new ResourceConflictException("cycle exists between "
            + rsrc.getName() + " and " + parent.getProject().getName());
      }
    }

    try {
      MetaDataUpdate md = updateFactory.create(rsrc.getNameKey());
      try {
        ProjectConfig config = ProjectConfig.read(md);
        Project project = config.getProject();
        project.setParentName(input.parent);

        String msg = Strings.emptyToNull(input.commitMessage);
        if (msg == null) {
          msg = String.format(
              "Changed parent to %s.\n",
              Objects.firstNonNull(project.getParentName(), allProjects.get()));
        } else if (!msg.endsWith("\n")) {
          msg += "\n";
        }
        md.setAuthor(user);
        md.setMessage(msg);
        config.commit(md);
        cache.evict(ctl.getProject());

        Project.NameKey parentName = project.getParent(allProjects);
        return parentName != null ? parentName.get() : "";
      } finally {
        md.close();
      }
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(rsrc.getName());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(String.format(
          "invalid project.config: %s", e.getMessage()));
    }
  }
}
