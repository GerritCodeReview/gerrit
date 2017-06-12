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

import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.project.RefUtil.InvalidRevisionException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.TimeZone;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateTag implements RestModifyView<ProjectResource, TagInput> {
  private static final Logger log = LoggerFactory.getLogger(CreateTag.class);

  public interface Factory {
    CreateTag create(String ref);
  }

  private final Provider<IdentifiedUser> identifiedUser;
  private final GitRepositoryManager repoManager;
  private final TagCache tagCache;
  private final GitReferenceUpdated referenceUpdated;
  private String ref;

  @Inject
  CreateTag(
      Provider<IdentifiedUser> identifiedUser,
      GitRepositoryManager repoManager,
      TagCache tagCache,
      GitReferenceUpdated referenceUpdated,
      @Assisted String ref) {
    this.identifiedUser = identifiedUser;
    this.repoManager = repoManager;
    this.tagCache = tagCache;
    this.referenceUpdated = referenceUpdated;
    this.ref = ref;
  }

  @Override
  public TagInfo apply(ProjectResource resource, TagInput input)
      throws RestApiException, IOException {
    if (input == null) {
      input = new TagInput();
    }
    if (input.ref != null && !ref.equals(input.ref)) {
      throw new BadRequestException("ref must match URL");
    }
    if (input.revision == null) {
      input.revision = Constants.HEAD;
    }
    while (ref.startsWith("/")) {
      ref = ref.substring(1);
    }
    if (ref.startsWith(R_REFS) && !ref.startsWith(R_TAGS)) {
      throw new BadRequestException("invalid tag name \"" + ref + "\"");
    }
    if (!ref.startsWith(R_TAGS)) {
      ref = R_TAGS + ref;
    }
    if (!Repository.isValidRefName(ref)) {
      throw new BadRequestException("invalid tag name \"" + ref + "\"");
    }

    RefControl refControl = resource.getControl().controlForRef(ref);
    try (Repository repo = repoManager.openRepository(resource.getNameKey())) {
      ObjectId revid = RefUtil.parseBaseRevision(repo, resource.getNameKey(), input.revision);
      RevWalk rw = RefUtil.verifyConnected(repo, revid);
      RevObject object = rw.parseAny(revid);
      rw.reset();
      boolean isAnnotated = Strings.emptyToNull(input.message) != null;
      boolean isSigned = isAnnotated && input.message.contains("-----BEGIN PGP SIGNATURE-----\n");
      if (isSigned) {
        throw new MethodNotAllowedException("Cannot create signed tag \"" + ref + "\"");
      } else if (isAnnotated && !refControl.canPerform(Permission.CREATE_TAG)) {
        throw new AuthException("Cannot create annotated tag \"" + ref + "\"");
      } else if (!refControl.canPerform(Permission.CREATE)) {
        throw new AuthException("Cannot create tag \"" + ref + "\"");
      }
      if (repo.getRefDatabase().exactRef(ref) != null) {
        throw new ResourceConflictException("tag \"" + ref + "\" already exists");
      }

      try (Git git = new Git(repo)) {
        TagCommand tag =
            git.tag()
                .setObjectId(object)
                .setName(ref.substring(R_TAGS.length()))
                .setAnnotated(isAnnotated)
                .setSigned(isSigned);

        if (isAnnotated) {
          tag.setMessage(input.message)
              .setTagger(
                  identifiedUser.get().newCommitterIdent(TimeUtil.nowTs(), TimeZone.getDefault()));
        }

        Ref result = tag.call();
        tagCache.updateFastForward(
            resource.getNameKey(), ref, ObjectId.zeroId(), result.getObjectId());
        referenceUpdated.fire(
            resource.getNameKey(),
            ref,
            ObjectId.zeroId(),
            result.getObjectId(),
            identifiedUser.get().getAccount());
        try (RevWalk w = new RevWalk(repo)) {
          return ListTags.createTagInfo(result, w);
        }
      }
    } catch (InvalidRevisionException e) {
      throw new BadRequestException("Invalid base revision");
    } catch (GitAPIException e) {
      log.error("Cannot create tag \"" + ref + "\"", e);
      throw new IOException(e);
    }
  }
}
