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
import com.google.gerrit.extensions.events.HeadUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.SetHead.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class SetHead implements RestModifyView<ProjectResource, Input> {
  private static final Logger log = LoggerFactory.getLogger(SetHead.class);

  static class Input {
    @DefaultInput
    String ref;
  }

  private final GitRepositoryManager repoManager;
  private final Provider<IdentifiedUser> identifiedUser;
  private final DynamicSet<HeadUpdatedListener> headUpdatedListener;

  @Inject
  SetHead(GitRepositoryManager repoManager,
      Provider<IdentifiedUser> identifiedUser,
      DynamicSet<HeadUpdatedListener> headUpdatedListener) {
    this.repoManager = repoManager;
    this.identifiedUser = identifiedUser;
    this.headUpdatedListener = headUpdatedListener;
  }

  @Override
  public String apply(final ProjectResource rsrc, Input input) throws AuthException,
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

      final String oldHead = repo.getRef(Constants.HEAD).getTarget().getName();
      final String newHead = ref;
      if (!oldHead.equals(newHead)) {
        final RefUpdate u = repo.updateRef(Constants.HEAD, true);
        u.setRefLogIdent(identifiedUser.get().newRefLogIdent());
        RefUpdate.Result res = u.link(newHead);
        switch(res) {
          case NO_CHANGE:
          case RENAMED:
          case FORCED:
          case NEW:
            break;
          default:
            throw new IOException("Setting HEAD failed with " + res);
        }

        HeadUpdatedListener.Event event = new HeadUpdatedListener.Event() {
          @Override
          public String getProjectName() {
            return rsrc.getNameKey().get();
          }

          @Override
          public String getOldHeadName() {
            return oldHead;
          }

          @Override
          public String getNewHeadName() {
            return newHead;
          }
        };
        for (HeadUpdatedListener l : headUpdatedListener) {
          try {
            l.onHeadUpdated(event);
          } catch (RuntimeException e) {
            log.warn("Failure in HeadUpdatedListener", e);
          }
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
