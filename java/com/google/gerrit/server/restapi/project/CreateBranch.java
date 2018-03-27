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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.CreateRefControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.RefUtil;
import com.google.gerrit.server.project.RefValidationHelper;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateBranch implements RestModifyView<ProjectResource, BranchInput> {
  private static final Logger log = LoggerFactory.getLogger(CreateBranch.class);

  public interface Factory {
    CreateBranch create(String ref);
  }

  private final Provider<IdentifiedUser> identifiedUser;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated referenceUpdated;
  private final RefValidationHelper refCreationValidator;
  private final CreateRefControl createRefControl;
  private String ref;

  @Inject
  CreateBranch(
      Provider<IdentifiedUser> identifiedUser,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      GitReferenceUpdated referenceUpdated,
      RefValidationHelper.Factory refHelperFactory,
      CreateRefControl createRefControl,
      @Assisted String ref) {
    this.identifiedUser = identifiedUser;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.referenceUpdated = referenceUpdated;
    this.refCreationValidator = refHelperFactory.create(ReceiveCommand.Type.CREATE);
    this.createRefControl = createRefControl;
    this.ref = ref;
  }

  @Override
  public BranchInfo apply(ProjectResource rsrc, BranchInput input)
      throws BadRequestException, AuthException, ResourceConflictException, IOException,
          PermissionBackendException, NoSuchProjectException {
    if (input == null) {
      input = new BranchInput();
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
    ref = RefNames.fullName(ref);
    if (!Repository.isValidRefName(ref)) {
      throw new BadRequestException("invalid branch name \"" + ref + "\"");
    }
    if (MagicBranch.isMagicBranch(ref)) {
      throw new BadRequestException(
          "not allowed to create branches under \""
              + MagicBranch.getMagicRefNamePrefix(ref)
              + "\"");
    }

    final Branch.NameKey name = new Branch.NameKey(rsrc.getNameKey(), ref);
    try (Repository repo = repoManager.openRepository(rsrc.getNameKey())) {
      ObjectId revid = RefUtil.parseBaseRevision(repo, rsrc.getNameKey(), input.revision);
      RevWalk rw = RefUtil.verifyConnected(repo, revid);
      RevObject object = rw.parseAny(revid);

      if (ref.startsWith(Constants.R_HEADS)) {
        // Ensure that what we start the branch from is a commit. If we
        // were given a tag, deference to the commit instead.
        //
        try {
          object = rw.parseCommit(object);
        } catch (IncorrectObjectTypeException notCommit) {
          throw new BadRequestException("\"" + input.revision + "\" not a commit");
        }
      }

      createRefControl.checkCreateRef(identifiedUser, repo, name, object);

      try {
        final RefUpdate u = repo.updateRef(ref);
        u.setExpectedOldObjectId(ObjectId.zeroId());
        u.setNewObjectId(object.copy());
        u.setRefLogIdent(identifiedUser.get().newRefLogIdent());
        u.setRefLogMessage("created via REST from " + input.revision, false);
        refCreationValidator.validateRefOperation(rsrc.getName(), identifiedUser.get(), u);
        final RefUpdate.Result result = u.update(rw);
        switch (result) {
          case FAST_FORWARD:
          case NEW:
          case NO_CHANGE:
            referenceUpdated.fire(
                name.getParentKey(), u, ReceiveCommand.Type.CREATE, identifiedUser.get().state());
            break;
          case LOCK_FAILURE:
            if (repo.getRefDatabase().exactRef(ref) != null) {
              throw new ResourceConflictException("branch \"" + ref + "\" already exists");
            }
            String refPrefix = RefUtil.getRefPrefix(ref);
            while (!Constants.R_HEADS.equals(refPrefix)) {
              if (repo.getRefDatabase().exactRef(refPrefix) != null) {
                throw new ResourceConflictException(
                    "Cannot create branch \""
                        + ref
                        + "\" since it conflicts with branch \""
                        + refPrefix
                        + "\".");
              }
              refPrefix = RefUtil.getRefPrefix(refPrefix);
            }
            // fall through
            // $FALL-THROUGH$
          case FORCED:
          case IO_FAILURE:
          case NOT_ATTEMPTED:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case RENAMED:
          case REJECTED_MISSING_OBJECT:
          case REJECTED_OTHER_REASON:
          default:
            {
              throw new IOException(result.name());
            }
        }

        BranchInfo info = new BranchInfo();
        info.ref = ref;
        info.revision = revid.getName();
        info.canDelete =
            permissionBackend.currentUser().ref(name).testOrFalse(RefPermission.DELETE)
                    && rsrc.getProjectState().statePermitsWrite()
                ? true
                : null;
        return info;
      } catch (IOException err) {
        log.error("Cannot create branch \"" + name + "\"", err);
        throw err;
      }
    } catch (RefUtil.InvalidRevisionException e) {
      throw new BadRequestException("invalid revision \"" + input.revision + "\"");
    }
  }
}
