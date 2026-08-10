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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.CreateCommitInput;
import com.google.gerrit.extensions.api.projects.CreateCommitInput.FileChange;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ValidationOptionsUtil;
import com.google.gerrit.server.edit.ChangeEditModifier;
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
import com.google.gerrit.server.update.RepoView;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.FileMode;
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
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Applies a {@link CreateCommitInput} (a set of file writes/deletes/renames) directly to a branch
 * as a single commit. Backs the {@link CreateCommit} REST view.
 *
 * <p>The reusable {@link CreateCommitInput}-to-{@link TreeModification} conversion lives in {@link
 * CommitFileModifications}; this class owns the branch ref update, reusing {@link
 * ChangeEditModifier#createNewTree} for the tree and {@link CommitUtil} for the commit.
 */
@Singleton
class BranchCommitBuilder {
  private final GitRepositoryManager repoManager;
  private final Provider<IdentifiedUser> identifiedUser;
  private final Provider<PersonIdent> serverIdent;
  private final PermissionBackend permissionBackend;
  private final GitReferenceUpdated referenceUpdated;
  private final RefValidationHelper refUpdateValidator;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final DiffOperationsForCommitValidation.Factory diffOperationsForCommitValidationFactory;

  @Inject
  BranchCommitBuilder(
      GitRepositoryManager repoManager,
      Provider<IdentifiedUser> identifiedUser,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      PermissionBackend permissionBackend,
      GitReferenceUpdated referenceUpdated,
      RefValidationHelper.Factory refValidationHelperFactory,
      CommitValidators.Factory commitValidatorsFactory,
      DiffOperationsForCommitValidation.Factory diffOperationsForCommitValidationFactory) {
    this.repoManager = repoManager;
    this.identifiedUser = identifiedUser;
    this.serverIdent = serverIdent;
    this.permissionBackend = permissionBackend;
    this.referenceUpdated = referenceUpdated;
    this.refUpdateValidator = refValidationHelperFactory.create(ReceiveCommand.Type.UPDATE);
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.diffOperationsForCommitValidationFactory = diffOperationsForCommitValidationFactory;
  }

  /** Commits the file operations directly to the branch. Requires {@link RefPermission#UPDATE}. */
  CommitInfo createCommit(BranchResource rsrc, CreateCommitInput input)
      throws RestApiException, PermissionBackendException, IOException {
    requireInput(input);
    BranchNameKey branch = rsrc.getBranchKey();
    checkWritableBranch(rsrc, branch);
    permissionBackend
        .currentUser()
        .project(branch.project())
        .ref(branch.branch())
        .check(RefPermission.UPDATE);

    String message = commitMessage(input);
    ImmutableList<TreeModification> modifications = CommitFileModifications.fromInput(input);
    ImmutableListMultimap<String, String> validationOptions =
        ValidationOptionsUtil.getValidateOptionsAsMultimap(input.validationOptions);

    try (Repository repo = repoManager.openRepository(branch.project());
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader)) {
      Ref ref = requireBranchRef(repo, branch);
      ObjectId expectedOld = resolveExpectedOldObjectId(input, ref, branch);
      RevCommit base = rw.parseCommit(ref.getObjectId());
      requireSourcePathsExist(reader, base, input);
      ObjectId treeId = buildTree(repo, base, modifications);
      ObjectId newCommitId = insertCommit(oi, base, treeId, message);
      validateCommit(rsrc, repo, rw, oi, ref.getObjectId(), newCommitId, validationOptions);

      try (RefUpdateContext refCtx = RefUpdateContext.open(BRANCH_MODIFICATION)) {
        RefUpdate u = repo.updateRef(branch.branch());
        u.setExpectedOldObjectId(expectedOld);
        u.setNewObjectId(newCommitId);
        u.setRefLogIdent(identifiedUser.get().newRefLogIdent());
        u.setRefLogMessage("commit files via REST", false);
        refUpdateValidator.validateRefOperation(
            branch.project().get(), identifiedUser.get(), u, validationOptions);
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
   * Inserts a commit with {@code treeId} on top of {@code base}. Per Gerrit convention for
   * server-created commits, both the author and the committer are the calling user (full name and
   * preferred email, or a generic {@code username@host} identity if no preferred email is set),
   * consistent with a change edit publish. Author and committer share the server's timestamp and
   * time zone, i.e. the server time at which the commit is created; the identities cannot be
   * overridden via {@link CreateCommitInput}.
   */
  private ObjectId insertCommit(ObjectInserter oi, RevCommit base, ObjectId treeId, String message)
      throws IOException {
    PersonIdent committer = identifiedUser.get().newCommitterIdent(serverIdent.get());
    ObjectId commitId =
        CommitUtil.createCommitWithTree(
            oi, committer, committer, ImmutableList.of(base), message, treeId);
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
      ObjectId newCommitId,
      ImmutableListMultimap<String, String> validationOptions)
      throws ResourceConflictException, IOException {
    BranchNameKey branch = rsrc.getBranchKey();
    ReceiveCommand cmd = new ReceiveCommand(oldId, newCommitId, branch.branch());
    try (RepoView repoView = new RepoView(repo, rw, oi);
        CommitReceivedEvent event =
            new CommitReceivedEvent(
                cmd,
                rsrc.getProjectState().getProject(),
                branch.branch(),
                validationOptions,
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

  private String commitMessage(CreateCommitInput input) throws BadRequestException {
    String message = input.commitMessage == null ? "" : input.commitMessage.trim();
    if (message.isEmpty()) {
      throw new BadRequestException("commit message must be non-empty");
    }
    if (!message.endsWith("\n")) {
      message = message + "\n";
    }
    return message;
  }

  /**
   * Rejects operations whose source path is absent from the base tree. A delete of a missing path
   * or a rename from a missing source would otherwise be silently dropped (see {@link
   * com.google.gerrit.server.edit.tree.DeleteFileModification} / {@link
   * com.google.gerrit.server.edit.tree.RenameFileModification}) while the surrounding commit still
   * succeeds. This needs the base tree, so it runs here rather than in {@link
   * CommitFileModifications}.
   */
  private static void requireSourcePathsExist(
      ObjectReader reader, RevCommit base, CreateCommitInput input)
      throws BadRequestException, IOException {
    for (Map.Entry<String, FileChange> entry : input.files.entrySet()) {
      FileChange change = entry.getValue();
      if (change.delete) {
        requireFileExists(reader, base, entry.getKey());
      } else if (change.renameFrom != null) {
        requireFileExists(reader, base, change.renameFrom);
      }
    }
  }

  /**
   * Rejects {@code path} unless it resolves to an existing file (blob), not a missing path or a
   * directory.
   */
  private static void requireFileExists(ObjectReader reader, RevCommit base, String path)
      throws BadRequestException, IOException {
    try (TreeWalk tw = TreeWalk.forPath(reader, path, base.getTree())) {
      if (tw == null) {
        throw new BadRequestException("path does not exist: " + path);
      }
      if (tw.getFileMode(0) == FileMode.TREE) {
        throw new BadRequestException("path is a directory, not a file: " + path);
      }
    }
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
   * Resolves the expected old object id: the caller-provided {@code base_revision} when set,
   * otherwise the current branch tip.
   *
   * <p>If {@code base_revision} is set, it must match the current branch tip. The final ref update
   * still performs the same compare-and-swap check to protect against races.
   */
  private ObjectId resolveExpectedOldObjectId(
      CreateCommitInput input, Ref ref, BranchNameKey branch)
      throws BadRequestException, ResourceConflictException {
    ObjectId currentTip = ref.getObjectId();
    if (input.baseRevision == null) {
      return currentTip;
    }
    ObjectId expectedOld = parseBaseRevision(input.baseRevision);
    if (!expectedOld.equals(currentTip)) {
      throw new ResourceConflictException(
          "branch \"" + branch.branch() + "\" changed concurrently or base_revision is stale");
    }
    return expectedOld;
  }

  private static void requireInput(@Nullable CreateCommitInput input) throws BadRequestException {
    if (input == null) {
      throw new BadRequestException("input is required");
    }
  }

  /**
   * Rejects branches this endpoint must not write to: {@code HEAD} (a symbolic ref, not an ordinary
   * branch), read-only projects, Gerrit-internal refs, tags, and the {@code refs/meta/*} namespace
   * (project config, schema version, dashboards, etc.).
   */
  private void checkWritableBranch(BranchResource rsrc, BranchNameKey branch)
      throws MethodNotAllowedException, ResourceConflictException, AuthException {
    if (RefNames.HEAD.equals(branch.branch())) {
      throw new MethodNotAllowedException("not allowed to write to HEAD");
    }
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
