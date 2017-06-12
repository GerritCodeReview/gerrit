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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class PutDescription implements RestModifyView<ProjectResource, DescriptionInput> {
  private final ProjectCache cache;
  private final MetaDataUpdate.Server updateFactory;
  private final GitRepositoryManager gitMgr;

  @Inject
  PutDescription(
      ProjectCache cache, MetaDataUpdate.Server updateFactory, GitRepositoryManager gitMgr) {
    this.cache = cache;
    this.updateFactory = updateFactory;
    this.gitMgr = gitMgr;
  }

  @Override
  public Response<String> apply(ProjectResource resource, DescriptionInput input)
      throws AuthException, ResourceConflictException, ResourceNotFoundException, IOException {
    if (input == null) {
      input = new DescriptionInput(); // Delete would set description to null.
    }

    ProjectControl ctl = resource.getControl();
    IdentifiedUser user = ctl.getUser().asIdentifiedUser();
    if (!ctl.isOwner()) {
      throw new AuthException("not project owner");
    }

    try (MetaDataUpdate md = updateFactory.create(resource.getNameKey())) {
      ProjectConfig config = ProjectConfig.read(md);
      Project project = config.getProject();
      project.setDescription(Strings.emptyToNull(input.description));

      String msg =
          MoreObjects.firstNonNull(
              Strings.emptyToNull(input.commitMessage), "Updated description.\n");
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      md.setAuthor(user);
      md.setMessage(msg);
      config.commit(md);
      cache.evict(ctl.getProject());
      gitMgr.setProjectDescription(resource.getNameKey(), project.getDescription());

      return Strings.isNullOrEmpty(project.getDescription())
          ? Response.<String>none()
          : Response.ok(project.getDescription());
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(resource.getName());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(
          String.format("invalid project.config: %s", e.getMessage()));
    }
  }
}
