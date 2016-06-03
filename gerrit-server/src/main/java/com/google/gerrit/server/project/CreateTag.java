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

import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.RefUtil.InvalidRevisionException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.TimeZone;

public class CreateTag implements RestModifyView<ProjectResource, TagInput> {
  private static final Logger log = LoggerFactory.getLogger(CreateTag.class);

  public interface Factory {
    CreateTag create(String ref);
  }

  private final Provider<IdentifiedUser> identifiedUser;
  private final GitRepositoryManager repoManager;
  private final Provider<ReviewDb> db;
  private final GitReferenceUpdated referenceUpdated;
  private final ChangeHooks hooks;
  private String ref;

  @Inject
  CreateTag(Provider<IdentifiedUser> identifiedUser,
      GitRepositoryManager repoManager,
      Provider<ReviewDb> db,
      GitReferenceUpdated referenceUpdated,
      ChangeHooks hooks,
      @Assisted String ref) {
    this.identifiedUser = identifiedUser;
    this.repoManager = repoManager;
    this.db = db;
    this.referenceUpdated = referenceUpdated;
    this.hooks = hooks;
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
      if (!refControl.canCreate(db.get(), rw, object)) {
        throw new AuthException("Cannot create \"" + ref + "\"");
      }

      try {
        if (input.message != null) {
          Timestamp now = TimeUtil.nowTs();
          PersonIdent tagger =
              identifiedUser.get().newCommitterIdent(now, TimeZone.getDefault());
          try (Git git = new Git(repo)) {
            git.tag()
              .setName(input.ref)
              .setAnnotated(true)
              .setObjectId(object)
              .setMessage(input.message)
              .setTagger(tagger)
              .call();
            return new TagInfo("FAIL", "FAIL");
          } catch (GitAPIException e) {
            throw new IOException(e);
          }
        }

        RefUpdate u = repo.updateRef(ref);
        u.setExpectedOldObjectId(ObjectId.zeroId());
        u.setNewObjectId(object.copy());
        u.setRefLogIdent(identifiedUser.get().newRefLogIdent());
        u.setRefLogMessage("created via REST from " + input.revision, false);
        RefUpdate.Result result = u.update(rw);
        switch (result) {
          case FAST_FORWARD:
          case NEW:
          case NO_CHANGE:
            referenceUpdated.fire(
                resource.getNameKey(), u, ReceiveCommand.Type.CREATE,
                identifiedUser.get().getAccount());
            //hooks.doRefUpdatedHook(name, u, identifiedUser.get().getAccount());
            break;
          case LOCK_FAILURE:
            if (repo.getRefDatabase().exactRef(ref) != null) {
              throw new ResourceConflictException("branch \"" + ref
                  + "\" already exists");
            }
            String refPrefix = RefUtil.getRefPrefix(ref);
            while (!Constants.R_HEADS.equals(refPrefix)) {
              if (repo.getRefDatabase().exactRef(refPrefix) != null) {
                throw new ResourceConflictException("Cannot create branch \""
                    + ref + "\" since it conflicts with branch \"" + refPrefix
                    + "\".");
              }
              refPrefix = RefUtil.getRefPrefix(refPrefix);
            }
            //$FALL-THROUGH$
          case FORCED:
          case IO_FAILURE:
          case NOT_ATTEMPTED:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case RENAMED:
          default: {
            throw new IOException(result.name());
          }
        }
      } catch (IOException e) {
        log.error("Cannot create ref \"" + ref + "\"", e);
        throw e;
      }
    } catch (InvalidRevisionException e) {
      throw new BadRequestException("invalid revision \"" + input.revision + "\"");
    }

    return new TagInfo("FAIL", "FAIL");
  }

}
