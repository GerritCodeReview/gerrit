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

import com.google.gerrit.extensions.api.projects.DeleteTagsInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
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
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    this.refDeletionValidator =
        refHelperFactory.create(ReceiveCommand.Type.DELETE);
  }

  @Override
  public Response<?> apply(ProjectResource resource, DeleteTagsInput input)
      throws RestApiException, IOException {
    if (input == null) {
      input = new DeleteTagsInput();
    }
    if (input.tags == null) {
      input.tags = new ArrayList<>(1);
    }
    try (Repository r = repoManager.openRepository(resource.getNameKey());
        Git git = new Git(r)) {
      StringBuilder errors = new StringBuilder();
      IdentifiedUser user = identifiedUser.get();

      // Step 1. Filter the tags in the input list to those that are valid tag
      // refs, that are allowed to be deleted according to the refcontrol. We
      // store them in a Set to implicitly remove duplicates.
      //
      // TODO: Can we use a Set in DeleteTagsInput? Then we can combine these
      //       two steps into one.
      Set<String> allowedToDelete = input.tags.stream().map(
          new Function<String, String>() {
            @Override
            public String apply(String tag) {
              try {
                String tagRef = RefUtil.validateTagRef(tag);
                if (resource.getControl().controlForRef(tagRef).canDelete()) {
                  return tagRef;
                }
              } catch (BadRequestException e) {
                // Not allowed - handled below
              }
              error(errors, tag, "Not allowed");
              return null;
            }
          }).filter(Objects::nonNull).collect(Collectors.toSet());

      // Step 2. Create RefUpdate instances and filter out the ones that are
      // rejected by ref deletion validators.
      List<RefUpdate> refsToDelete = allowedToDelete.stream().map(
          new Function<String, RefUpdate>() {
            @Override
            public RefUpdate apply(String tag) {
              try {
                RefUpdate update = r.updateRef(tag);
                update.setForceUpdate(true);
                refDeletionValidator.validateRefOperation(
                    resource.getName(), user, update);
                return update;
              } catch (ResourceConflictException e) {
                error(errors, tag, "Not allowed");
              } catch(IOException e) {
                error(errors, tag, e.getMessage());
              }
              return null;
            }
          }).filter(Objects::nonNull).collect(Collectors.toList());

      // Step 3. Extract the ref names back out of the reduced list
      // and build the delete command
      List<String> tagsToDelete = refsToDelete.stream()
          .map(ref -> ref.getName()).collect(Collectors.toList());
      DeleteTagCommand deleteTags =
          git.tagDelete().setTags(tagsToDelete.toArray(new String[0]));

      try {
        // DeleteTagCommand returns a list of tags that were deleted. Remove
        // those from the original list of tags that we gave; any that are still
        // in the original list did not get deleted.
        List<String> deletedTags = deleteTags.call();
        tagsToDelete.removeAll(deletedTags);
        for (String tag: tagsToDelete) {
          error(errors, tag, "Failed to delete");
        }
      } catch (GitAPIException e) {
        error(errors, null, e.getMessage());
      }
    }
    return Response.none();
  }

  private void error(StringBuilder errors, String tag, String error) {
    String message;
    if (tag != null) {
      message = String.format("Cannot delete %s: %s", tag, error);
    } else {
      message = String.format("Cannot delete tags: %s", error);
    }
    errors.append(message);
    log.error(message);
  }
}
