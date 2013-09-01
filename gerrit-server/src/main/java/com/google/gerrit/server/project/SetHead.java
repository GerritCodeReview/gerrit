// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.SetHead.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

public class SetHead implements RestModifyView<ProjectResource, Input> {
  static class Input {
    @DefaultInput
    String ref;
  }

  private final GitRepositoryManager repoManager;
  private final Provider<IdentifiedUser> identifiedUser;

  @Inject
  SetHead(GitRepositoryManager repoManager, Provider<IdentifiedUser> identifiedUser) {
    this.repoManager = repoManager;
    this.identifiedUser = identifiedUser;
  }

  @Override
  public String apply(ProjectResource rsrc, Input input) throws AuthException,
      ResourceNotFoundException, BadRequestException,
      UnprocessableEntityException, IOException {
    if (!rsrc.getControl().isOwner()) {
      throw new AuthException("restricted to project owner");
    }
    if (input == null || Strings.isNullOrEmpty(input.ref)) {
      throw new BadRequestException("ref required");
    }
    String ref = input.ref;
    if (!ref.startsWith(Constants.R_REFS)) {
      ref = Constants.R_HEADS + ref;
    }

    Repository repo = null;
    try {
      repo = repoManager.openRepository(rsrc.getNameKey());
      if (repo.getRef(ref) == null) {
        throw new UnprocessableEntityException(String.format(
            "Ref Not Found: %s", ref));
      }

      if (!repo.getRef(Constants.HEAD).getTarget().getName().equals(ref)) {
        final RefUpdate u = repo.updateRef(Constants.HEAD, true);
        u.setRefLogIdent(identifiedUser.get().newRefLogIdent());
        RefUpdate.Result res = u.link(ref);
        switch(res) {
          case NO_CHANGE:
          case RENAMED:
          case FORCED:
          case NEW:
            break;
          default:
            throw new IOException("Setting HEAD failed with " + res);
        }
      }
      return ref;
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(rsrc.getName());
    } finally {
      if (repo != null) {
        repo.close();
      }
    }
  }
}
