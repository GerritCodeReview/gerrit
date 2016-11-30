// Copyright (C) 2016 The Android Open Source Project
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

import static org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE;

import static java.lang.String.format;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.projects.DeleteTagsInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.api.DeleteTagCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class DeleteTags
    implements RestModifyView<ProjectResource, DeleteTagsInput> {
  private static final Logger log = LoggerFactory.getLogger(DeleteTags.class);
  private final Provider<IdentifiedUser> identifiedUser;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated referenceUpdated;
  private final RefValidationHelper refDeletionValidator;

  @Inject
  DeleteTags(Provider<IdentifiedUser> identifiedUser,
      GitRepositoryManager repoManager,
      GitReferenceUpdated referenceUpdated,
      RefValidationHelper.Factory refHelperFactory) {
    this.identifiedUser = identifiedUser;
    this.repoManager = repoManager;
    this.referenceUpdated = referenceUpdated;
    this.refDeletionValidator = refHelperFactory.create(DELETE);
  }

  @Override
  public Response<?> apply(ProjectResource resource, DeleteTagsInput input)
      throws RestApiException, IOException {
    String tag = RefUtil.validateTagRef(input.tags);
    RefControl refControl = resource.getControl().controlForRef(tag);

    if (input == null) {
      input = new DeleteTagsInput();
    }
    if (input.tags == null) {
      input.tags = Lists.newArrayListWithCapacity(1);
    }

    try (Repository r = repoManager.openRepository(resource.getNameKey());
        Git git = new Git(r)) {
      DeleteTagCommand deleteTag = git.tagDelete().setTags(tag);
      for (String tags : input.tags) {
        if (!refControl.canDelete()) {
          throw new AuthException("Cannot delete tag");
        }
        RefUpdate update = r.updateRef(tags);
        update.setForceUpdate(true);
        IdentifiedUser user = identifiedUser.get();
        refDeletionValidator.validateRefOperation(
            resource.getName(), user, update);
        deleteTag.call();
      }
      for (ReceiveCommand command : deleteTag.getCommands()) {
        if (command.getResult() == Result.OK) {
          postDeletion(resource, command);
        }
      }
    } catch (GitAPIException e) {
      log.error("Cannot delete tag \"" + tag + "\"", e);
      throw new IOException(e);
    }
    return Response.none();
  }

  private void postDeletion(ProjectResource resource, ReceiveCommand cmd) {
    referenceUpdated.fire(resource.getNameKey(), cmd,
        identifiedUser.get().getAccount());
  }
}
