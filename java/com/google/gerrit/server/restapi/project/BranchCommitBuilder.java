// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.BRANCH_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.CommitFilesInput;
import com.google.gerrit.extensions.api.projects.CommitFilesInput.FileChange;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.edit.tree.DeleteFileModification;
import com.google.gerrit.server.edit.tree.RenameFileModification;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.patch.DiffOperationsForCommitValidation;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.RefValidationHelper;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RepoView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.ChangeIdUtil;

/**
 * Shared engine that applies a {@link CommitFilesInput} (a set of file writes/deletes/renames) to a
 * branch as a single commit, either directly or as a change for review. Both branch-content REST
 * views delegate here, analogous to how PutConfig/PutConfigReview delegate to {@link
 * RepoMetaDataUpdater}.
 */
@Singleton
class BranchCommitBuilder {
  private final GitRepositoryManager repoManager;
  private final Provider<CurrentUser> user;
  private final Provider<IdentifiedUser> identifiedUser;
  private final Provider<PersonIdent> serverIdent;
  private final PermissionBackend permissionBackend;
  private final Sequences seq;
  private final BatchUpdate.Factory updateFactory;
  private final ChangeInserter.Factory changeInserterFactory;
  private final ChangeJson.Factory jsonFactory;
  private final GitReferenceUpdated referenceUpdated;
  private final RefValidationHelper refUpdateValidator;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final DiffOperationsForCommitValidation.Factory diffOperationsForCommitValidationFactory;

  @Inject
  BranchCommitBuilder(
      GitRepositoryManager repoManager,
      Provider<CurrentUser> user,
      Provider<IdentifiedUser> identifiedUser,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      PermissionBackend permissionBackend,
      Sequences seq,
      BatchUpdate.Factory updateFactory,
      ChangeInserter.Factory changeInserterFactory,
      ChangeJson.Factory jsonFactory,
      GitReferenceUpdated referenceUpdated,
      RefValidationHelper.Factory refValidationHelperFactory,
      CommitValidators.Factory commitValidatorsFactory,
      DiffOperationsForCommitValidation.Factory diffOperationsForCommitValidationFactory) {
    this.repoManager = repoManager;
    this.user = user;
    this.identifiedUser = identifiedUser;
    this.serverIdent = serverIdent;
    this.permissionBackend = permissionBackend;
    this.seq = seq;
    this.updateFactory = updateFactory;
    this.changeInserterFactory = changeInserterFactory;
    this.jsonFactory = jsonFactory;
    this.referenceUpdated = referenceUpdated;
    this.refUpdateValidator = refValidationHelperFactory.create(ReceiveCommand.Type.UPDATE);
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.diffOperationsForCommitValidationFactory = diffOperationsForCommitValidationFactory;
  }

  /** Commits the file operations directly to the branch. Requires {@link RefPermission#UPDATE}. */
  CommitInfo commitFiles(BranchResource rsrc, CommitFilesInput input)
      throws RestApiException, PermissionBackendException, IOException {
    requireInput(input);
    BranchNameKey branch = rsrc.getBranchKey();
    checkWritableBranch(rsrc, branch);
    permissionBackend
        .currentUser()
        .project(branch.project())
        .ref(branch.branch())
        .check(RefPermission.UPDATE);

    String message = commitMessage(input, /* ensureChangeId= */ false);
    List<TreeModification> modifications = buildModifications(input);

    try (Repository repo = repoManager.openRepository(branch.project());
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader)) {
      Ref ref = requireBranchRef(repo, branch);
      ObjectId expectedOld = resolveBaseObjectId(input, ref);
      RevCommit base = rw.parseCommit(ref.getObjectId());
      ObjectId treeId = buildTree(repo, base, modifications);
      ObjectId newCommitId = insertCommit(oi, base, treeId, message);
      validateCommit(rsrc, repo, rw, oi, ref.getObjectId(), newCommitId);

      try (RefUpdateContext refCtx = RefUpdateContext.open(BRANCH_MODIFICATION)) {
        RefUpdate u = repo.updateRef(branch.branch());
        u.setExpectedOldObjectId(expectedOld);
        u.setNewObjectId(newCommitId);
        u.setRefLogIdent(identifiedUser.get().newRefLogIdent());
        u.setRefLogMessage("commit files via REST", false);
        refUpdateValidator.validateRefOperation(
            branch.project().get(), identifiedUser.get(), u, ImmutableListMultimap.of());
        RefUpdate.Result result = u.update(rw);
        switch (result) {
          case FAST_FORWARD:
          case NEW:
          case NO_CHANGE:
            referenceUpdated.fire(
                branch.project(), u, ReceiveCommand.Type.UPDATE, identifiedUser.get().state());
            break;
          case LOCK_FAILURE:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case REJECTED_MISSING_OBJECT:
          case REJECTED_OTHER_REASON:
            throw new ResourceConflictException(
                "branch \""
                    + branch.branch()
                    + "\" changed concurrently or base_revision is stale");
          case FORCED:
          case IO_FAILURE:
          case NOT_ATTEMPTED:
          case RENAMED:
          default:
            throw new IOException("Failed to update " + branch.branch() + ": " + result.name());
        }
        return CommitUtil.toCommitInfo(rw.parseCommit(newCommitId), rw);
      }
    }
  }

  /**
   * Creates a change for review containing the file operations. Requires {@link RefPermission#READ}
   * and {@link RefPermission#CREATE_CHANGE}.
   */
  Response<ChangeInfo> createChange(BranchResource rsrc, CommitFilesInput input)
      throws RestApiException, PermissionBackendException, IOException, UpdateException {
    requireInput(input);
    BranchNameKey branch = rsrc.getBranchKey();
    checkWritableBranch(rsrc, branch);
    PermissionBackend.ForRef forRef =
        permissionBackend.currentUser().project(branch.project()).ref(branch.branch());
    if (!forRef.test(RefPermission.READ)) {
      throw new ResourceNotFoundException("ref " + branch.branch() + " not found");
    }
    forRef.check(RefPermission.CREATE_CHANGE);

    String message = commitMessage(input, /* ensureChangeId= */ true);
    List<TreeModification> modifications = buildModifications(input);

    try (Repository repo = repoManager.openRepository(branch.project());
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader)) {
      Ref ref = requireBranchRef(repo, branch);
      ObjectId baseId = resolveBaseObjectId(input, ref);
      RevCommit base = parseBaseCommit(rw, baseId);
      ObjectId treeId = buildTree(repo, base, modifications);
      RevCommit newCommit = rw.parseCommit(insertCommit(oi, base, treeId, message));

      Change.Id changeId = Change.id(seq.nextChangeId());
      ChangeInserter ins = changeInserterFactory.create(changeId, newCommit, branch.branch());
      try (RefUpdateContext refCtx = RefUpdateContext.open(CHANGE_MODIFICATION);
          BatchUpdate bu = updateFactory.create(branch.project(), user.get(), TimeUtil.now())) {
        bu.setRepository(repo, rw, oi);
        bu.insertChange(ins);
        bu.execute();
      }
      return Response.created(jsonFactory.noOptions().format(ins.getChange()));
    }
  }

  /**
   * Inserts a commit with {@code treeId} on top of {@code base}. Per Gerrit convention for
   * server-created commits, the author is the calling user and the committer is the server.
   */
  private ObjectId insertCommit(ObjectInserter oi, RevCommit base, ObjectId treeId, String message)
      throws IOException {
    PersonIdent committer = serverIdent.get();
    PersonIdent author = identifiedUser.get().newCommitterIdent(committer);
    ObjectId commitId =
        CommitUtil.createCommitWithTree(
            oi, author, committer, ImmutableList.of(base), message, treeId);
    oi.flush();
    return commitId;
  }

  /**
   * Runs Gerrit's commit validators on the new commit, the same validation applied to
   * server-created commits (e.g. the create-a-change path). This ensures the direct-commit endpoint
   * does not bypass commit-content policy (file-count limits, config validation, plugin
   * commit-validation listeners, etc.). Change-Id enforcement does not apply here because the
   * target is a branch ref, not a magic/change ref.
   */
  private void validateCommit(
      BranchResource rsrc,
      Repository repo,
      RevWalk rw,
      ObjectInserter oi,
      ObjectId oldId,
      ObjectId newCommitId)
      throws ResourceConflictException, IOException {
    BranchNameKey branch = rsrc.getBranchKey();
    ReceiveCommand cmd = new ReceiveCommand(oldId, newCommitId, branch.branch());
    try (RepoView repoView = new RepoView(repo, rw, oi);
        CommitReceivedEvent event =
            new CommitReceivedEvent(
                cmd,
                rsrc.getProjectState().getProject(),
                branch.branch(),
                ImmutableListMultimap.of(),
                repo.getConfig(),
                rw.getObjectReader(),
                newCommitId,
                identifiedUser.get(),
                /* cherryPickOf= */ null,
                diffOperationsForCommitValidationFactory.create(repoView, oi))) {
      commitValidatorsFactory
          .forGerritCommits(
              permissionBackend.currentUser().project(branch.project()),
              branch,
              identifiedUser.get(),
              rw,
              /* change= */ null)
          .validate(event);
    } catch (CommitValidationException e) {
      throw new ResourceConflictException(e.getFullMessage());
    }
  }

  private static ObjectId buildTree(
      Repository repo, RevCommit base, List<TreeModification> modifications)
      throws BadRequestException, IOException {
    try {
      return ChangeEditModifier.createNewTree(repo, base, modifications);
    } catch (InvalidChangeOperationException e) {
      // Raised when the result tree is identical to the base tree (no effective change).
      throw new BadRequestException(e.getMessage());
    }
  }

  /**
   * Translates the requested file operations into {@link TreeModification}s while validating the
   * caller-supplied input, so that malformed requests fail with {@code 400 Bad Request} rather than
   * leaking through as a server error.
   */
  private static List<TreeModification> buildModifications(CommitFilesInput input)
      throws BadRequestException {
    if (input.files == null || input.files.isEmpty()) {
      throw new BadRequestException("files is required");
    }
    List<TreeModification> modifications = new ArrayList<>(input.files.size());
    // Two operations that touch the same path (including a rename's source path) cannot be applied
    // together; detect that here instead of letting TreeCreator throw an IllegalStateException that
    // would surface as a 500.
    Set<String> touchedPaths = new HashSet<>();
    for (Map.Entry<String, FileChange> entry : input.files.entrySet()) {
      String path = entry.getKey();
      if (Strings.isNullOrEmpty(path)) {
        throw new BadRequestException("file path must not be empty");
      }
      FileChange change = entry.getValue();
      if (change == null) {
        throw new BadRequestException("no operation given for " + path);
      }
      int ops =
          (change.content != null ? 1 : 0)
              + (change.delete ? 1 : 0)
              + (change.renameFrom != null ? 1 : 0);
      if (ops != 1) {
        throw new BadRequestException(
            "exactly one of content, delete, or rename_from is required for " + path);
      }
      TreeModification modification;
      if (change.delete) {
        modification = new DeleteFileModification(path);
      } else if (change.renameFrom != null) {
        if (change.renameFrom.isEmpty()) {
          throw new BadRequestException("rename_from must not be empty for " + path);
        }
        if (change.renameFrom.equals(path)) {
          throw new BadRequestException("rename_from must differ from the target path " + path);
        }
        modification = new RenameFileModification(change.renameFrom, path);
      } else {
        modification =
            new ChangeFileContentModification(
                path,
                RawInputUtil.create(decodeBase64(change.content, path)),
                octalToBits(change.fileMode));
      }
      for (String touched : modification.getFilePaths()) {
        if (!touchedPaths.add(touched)) {
          throw new BadRequestException("multiple operations affect the same path: " + touched);
        }
      }
      modifications.add(modification);
    }
    return modifications;
  }

  private static byte[] decodeBase64(String content, String path) throws BadRequestException {
    try {
      return org.eclipse.jgit.util.Base64.decode(content);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("content for " + path + " is not valid base64", e);
    }
  }

  @Nullable
  private static Integer octalToBits(@Nullable Integer octalFileMode) throws BadRequestException {
    if (octalFileMode == null) {
      return null;
    }
    try {
      return Integer.parseInt(Integer.toString(octalFileMode), 8);
    } catch (NumberFormatException e) {
      throw new BadRequestException("invalid file_mode: " + octalFileMode, e);
    }
  }

  private String commitMessage(CommitFilesInput input, boolean ensureChangeId) {
    String trimmed = input.commitMessage == null ? "" : input.commitMessage.trim();
    String message = trimmed.isEmpty() ? "Update files" : trimmed;
    if (!message.endsWith("\n")) {
      message = message + "\n";
    }
    if (ensureChangeId && ChangeIdUtil.indexOfChangeId(message, "\n") == -1) {
      message = ChangeIdUtil.insertId(message, CommitMessageUtil.generateChangeId());
    }
    return message;
  }

  private ObjectId parseBaseRevision(String baseRevision) throws BadRequestException {
    if (!ObjectId.isId(baseRevision)) {
      throw new BadRequestException("base_revision must be a full 40-character SHA-1");
    }
    return ObjectId.fromString(baseRevision);
  }

  /** Loads the target branch ref, rejecting with a 409 if it is missing. */
  private Ref requireBranchRef(Repository repo, BranchNameKey branch)
      throws ResourceConflictException, IOException {
    Ref ref = repo.exactRef(branch.branch());
    if (ref == null || ref.getObjectId() == null) {
      throw new ResourceConflictException("branch \"" + branch.branch() + "\" does not exist");
    }
    return ref;
  }

  /**
   * Resolves the base object id: the caller-provided {@code base_revision} when set, otherwise the
   * current branch tip.
   */
  private ObjectId resolveBaseObjectId(CommitFilesInput input, Ref ref) throws BadRequestException {
    return input.baseRevision != null ? parseBaseRevision(input.baseRevision) : ref.getObjectId();
  }

  /**
   * Resolves {@code baseId} to a commit, rejecting a well-formed but missing/non-commit SHA as a
   * 409 rather than a server error.
   */
  private RevCommit parseBaseCommit(RevWalk rw, ObjectId baseId)
      throws ResourceConflictException, IOException {
    try {
      return rw.parseCommit(baseId);
    } catch (MissingObjectException | IncorrectObjectTypeException e) {
      throw new ResourceConflictException("base_revision " + baseId.name() + " not found", e);
    }
  }

  private static void requireInput(@Nullable CommitFilesInput input) throws BadRequestException {
    if (input == null) {
      throw new BadRequestException("input is required");
    }
  }

  /**
   * Rejects branches this endpoint must not write to: read-only projects, Gerrit-internal refs,
   * tags, and the {@code refs/meta/*} namespace (project config, schema version, dashboards, etc.).
   */
  private void checkWritableBranch(BranchResource rsrc, BranchNameKey branch)
      throws ResourceConflictException, AuthException {
    if (!rsrc.getProjectState().statePermitsWrite()) {
      throw new ResourceConflictException("project state does not permit write");
    }
    if (RefNames.isGerritRef(branch.branch())
        || branch.branch().startsWith(RefNames.REFS_TAGS)
        || branch.branch().startsWith(RefNames.REFS_META)) {
      throw new AuthException("not allowed to write to " + branch.branch() + " via this endpoint");
    }
  }
}
