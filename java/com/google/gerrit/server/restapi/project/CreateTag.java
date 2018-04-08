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

package com.google.gerrit.server.restapi.project;

import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.git.TagCache;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.RefUtil;
import com.google.gerrit.server.project.RefUtil.InvalidRevisionException;
import com.google.inject.Inject;
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

  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final TagCache tagCache;
  private final GitReferenceUpdated referenceUpdated;
  private final WebLinks links;
  private String ref;

  @Inject
  CreateTag(
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      TagCache tagCache,
      GitReferenceUpdated referenceUpdated,
      WebLinks webLinks,
      @Assisted String ref) {
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.tagCache = tagCache;
    this.referenceUpdated = referenceUpdated;
    this.links = webLinks;
    this.ref = ref;
  }

  @Override
  public TagInfo apply(ProjectResource resource, TagInput input)
      throws RestApiException, IOException, PermissionBackendException, NoSuchProjectException {
    if (input == null) {
      input = new TagInput();
    }
    if (input.ref != null && !ref.equals(input.ref)) {
      throw new BadRequestException("ref must match URL");
    }
    if (input.revision == null) {
      input.revision = Constants.HEAD;
    }

    ref = RefUtil.normalizeTagRef(ref);
    PermissionBackend.ForRef perm =
        permissionBackend.currentUser().project(resource.getNameKey()).ref(ref);

    try (Repository repo = repoManager.openRepository(resource.getNameKey())) {
      ObjectId revid = RefUtil.parseBaseRevision(repo, resource.getNameKey(), input.revision);
      RevWalk rw = RefUtil.verifyConnected(repo, revid);
      RevObject object = rw.parseAny(revid);
      rw.reset();
      boolean isAnnotated = Strings.emptyToNull(input.message) != null;
      boolean isSigned = isAnnotated && input.message.contains("-----BEGIN PGP SIGNATURE-----\n");
      if (isSigned) {
        throw new MethodNotAllowedException("Cannot create signed tag \"" + ref + "\"");
      } else if (isAnnotated && !check(perm, RefPermission.CREATE_TAG)) {
        throw new AuthException("Cannot create annotated tag \"" + ref + "\"");
      } else {
        perm.check(RefPermission.CREATE);
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
                  resource
                      .getUser()
                      .asIdentifiedUser()
                      .newCommitterIdent(TimeUtil.nowTs(), TimeZone.getDefault()));
        }

        Ref result = tag.call();
        tagCache.updateFastForward(
            resource.getNameKey(), ref, ObjectId.zeroId(), result.getObjectId());
        referenceUpdated.fire(
            resource.getNameKey(),
            ref,
            ObjectId.zeroId(),
            result.getObjectId(),
            resource.getUser().asIdentifiedUser().state());
        try (RevWalk w = new RevWalk(repo)) {
          return ListTags.createTagInfo(perm, result, w, resource.getProjectState(), links);
        }
      }
    } catch (InvalidRevisionException e) {
      throw new BadRequestException("Invalid base revision");
    } catch (GitAPIException e) {
      log.error("Cannot create tag \"" + ref + "\"", e);
      throw new IOException(e);
    }
  }

  private static boolean check(PermissionBackend.ForRef perm, RefPermission permission)
      throws PermissionBackendException {
    try {
      perm.check(permission);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }
}
