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

import static com.google.gerrit.entities.RefNames.isConfigRef;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.BRANCH_MODIFICATION;

import com.google.common.base.Strings;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ValidationOptionsUtil;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.CreateRefControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.RefUtil;
import com.google.gerrit.server.project.RefValidationHelper;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

@Singleton
public class CreateBranch
    implements RestCollectionCreateView<ProjectResource, BranchResource, BranchInput> {
  private final Provider<IdentifiedUser> identifiedUser;
  private final Provider<PersonIdent> serverIdent;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated referenceUpdated;
  private final RefValidationHelper refCreationValidator;
  private final CreateRefControl createRefControl;

  @Inject
  CreateBranch(
      Provider<IdentifiedUser> identifiedUser,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      GitReferenceUpdated referenceUpdated,
      RefValidationHelper.Factory refHelperFactory,
      CreateRefControl createRefControl) {
    this.identifiedUser = identifiedUser;
    this.serverIdent = serverIdent;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.referenceUpdated = referenceUpdated;
    this.refCreationValidator = refHelperFactory.create(ReceiveCommand.Type.CREATE);
    this.createRefControl = createRefControl;
  }

  @Override
  public Response<BranchInfo> apply(ProjectResource rsrc, IdString id, BranchInput input)
      throws BadRequestException,
          AuthException,
          ResourceConflictException,
          UnprocessableEntityException,
          IOException,
          PermissionBackendException,
          NoSuchProjectException {
    try (RefUpdateContext ctx = RefUpdateContext.open(BRANCH_MODIFICATION)) {
      String ref = id.get();
      if (input == null) {
        input = new BranchInput();
      }
      if (input.ref != null && !ref.equals(input.ref)) {
        throw new BadRequestException("ref must match URL");
      }
      if (input.revision != null) {
        input.revision = input.revision.trim();
      }
      if (input.sourceRef != null) {
        input.sourceRef = input.sourceRef.trim();
      }
      if (input.createEmptyCommit) {
        if (!Strings.isNullOrEmpty(input.revision) || !Strings.isNullOrEmpty(input.sourceRef)) {
          throw new BadRequestException(
              "create_empty_commit and revision/source_ref are mutually exclusive");
        }
      } else {
        if (Strings.isNullOrEmpty(input.revision)) {
          if (!Strings.isNullOrEmpty(input.sourceRef)) {
            throw new BadRequestException("must not provide source_ref if not providing revision");
          }
          input.revision = Constants.HEAD;
        } else if (!Strings.isNullOrEmpty(input.sourceRef)) {
          if (input.revision.startsWith(RefNames.REFS)) {
            throw new BadRequestException("must not provide source_ref if revision is a ref name");
          }
        }
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
      if (!isBranchAllowed(ref)) {
        throw new BadRequestException(
            "Cannot create a branch with name \""
                + ref
                + "\". Not allowed to create branches under Gerrit internal or tags refs.");
      }

      BranchNameKey branchNameKey = BranchNameKey.create(rsrc.getNameKey(), ref);
      try (Repository repo = repoManager.openRepository(rsrc.getNameKey())) {
        ObjectId revid;
        if (input.createEmptyCommit) {
          revid = createEmptyCommit(repo);
        } else {
          revid = RefUtil.parseBaseRevision(repo, input.revision);
        }
        RevWalk rw = RefUtil.verifyConnected(repo, revid);
        RevObject revObject = rw.parseAny(revid);

        if (ref.startsWith(Constants.R_HEADS)) {
          // Ensure that what we start the branch from is a commit. If we
          // were given a tag, dereference to the commit instead.
          //
          revObject = rw.parseCommit(revObject);
        }

        checkCreateRefPermissions(input, repo, branchNameKey, revObject);

        RefUpdate u = repo.updateRef(ref);
        u.setExpectedOldObjectId(ObjectId.zeroId());
        u.setNewObjectId(revObject.copy());
        u.setRefLogIdent(identifiedUser.get().newRefLogIdent());
        u.setRefLogMessage("created via REST from " + input.revision, false);
        refCreationValidator.validateRefOperation(
            rsrc.getName(),
            identifiedUser.get(),
            u,
            ValidationOptionsUtil.getValidateOptionsAsMultimap(input.validationOptions));
        RefUpdate.Result result = u.update(rw);
        switch (result) {
          case FAST_FORWARD:
          case NEW:
          case NO_CHANGE:
            referenceUpdated.fire(
                branchNameKey.project(),
                u,
                ReceiveCommand.Type.CREATE,
                identifiedUser.get().state());
            break;
          case LOCK_FAILURE:
            if (repo.getRefDatabase().exactRef(ref) != null) {
              throw new ResourceConflictException("branch \"" + ref + "\" already exists");
            }
            Collection<String> conflicting = repo.getRefDatabase().getConflictingNames(ref);
            if (conflicting.size() > 0) {
              throw new ResourceConflictException(
                  "Cannot create branch \""
                      + ref
                      + "\" since it conflicts with branch \""
                      + conflicting.stream().collect(Collectors.joining(", "))
                      + "\".");
            }
            throw new LockFailureException(String.format("Failed to create %s", ref), u);
          case FORCED:
          case IO_FAILURE:
          case NOT_ATTEMPTED:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case RENAMED:
          case REJECTED_MISSING_OBJECT:
          case REJECTED_OTHER_REASON:
          default:
            throw new IOException(String.format("Failed to create %s: %s", ref, result.name()));
        }

        BranchInfo info = new BranchInfo();
        info.ref = ref;
        info.revision = revid.getName();

        if (isConfigRef(branchNameKey.branch())) {
          // Never allow to delete the meta config branch.
          info.canDelete = null;
        } else {
          info.canDelete =
              (permissionBackend.currentUser().ref(branchNameKey).testOrFalse(RefPermission.DELETE)
                      && rsrc.getProjectState().statePermitsWrite())
                  ? true
                  : null;
        }
        return Response.created(info);
      }
    }
  }

  /**
   * Checks whether the user is allowed to create the branch based on the given RevObject.
   *
   * <p>This checks whether the user has the {@code Create} permission to create the branch and that
   * the commit on which the branch is being created is visible to the user (through any visible
   * branch or open change).
   *
   * <p>If the branch is created for an empty initial commit the visibility check for the empty
   * commit is skipped (since we just created it there is no ref yet through which the user could
   * see this commit and hence the visibility check for this commit would always fail).
   *
   * @param input the input for the branch creation
   * @param repo the repository in which the branch should be created
   * @param branchNameKey the name key of the branch that should be created
   * @param revObject the RevObject that should be used as base for creating the branch
   */
  private void checkCreateRefPermissions(
      BranchInput input, Repository repo, BranchNameKey branchNameKey, RevObject revObject)
      throws AuthException,
          PermissionBackendException,
          ResourceConflictException,
          IOException,
          NoSuchProjectException {
    if (input.createEmptyCommit) {
      permissionBackend.user(identifiedUser.get()).ref(branchNameKey).check(RefPermission.CREATE);
    } else {
      Ref sourceRef;
      if (!Strings.isNullOrEmpty(input.sourceRef)) {
        sourceRef = repo.exactRef(input.sourceRef);
      } else {
        sourceRef = repo.exactRef(input.revision);
      }
      if (sourceRef == null) {
        createRefControl.checkCreateRef(
            identifiedUser, repo, branchNameKey, revObject, /* forPush= */ false);
      } else {
        if (sourceRef.isSymbolic()) {
          sourceRef = sourceRef.getTarget();
        }
        createRefControl.checkCreateRef(
            identifiedUser,
            repo,
            branchNameKey,
            revObject,
            /* forPush= */ false,
            BranchNameKey.create(branchNameKey.project(), sourceRef.getName()));
      }
    }
  }

  private ObjectId createEmptyCommit(Repository repo) throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(oi.insert(Constants.OBJ_TREE, new byte[] {}));
      cb.setCommitter(serverIdent.get());
      cb.setAuthor(identifiedUser.get().newCommitterIdent(cb.getCommitter()));
      cb.setMessage("Initial empty branch\n");
      ObjectId commitId = oi.insert(cb);
      oi.flush();
      return commitId;
    }
  }

  /** Branches cannot be created under any Gerrit internal or tags refs. */
  private boolean isBranchAllowed(String branch) {
    return !RefNames.isGerritRef(branch) && !branch.startsWith(RefNames.REFS_TAGS);
  }
}
