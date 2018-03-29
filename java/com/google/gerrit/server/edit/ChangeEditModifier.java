// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.edit;

import static com.google.gerrit.server.PatchSetUtil.isPatchSetLocked;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.edit.tree.DeleteFileModification;
import com.google.gerrit.server.edit.tree.RenameFileModification;
import com.google.gerrit.server.edit.tree.RestoreFileModification;
import com.google.gerrit.server.edit.tree.TreeCreator;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Utility functions to manipulate change edits.
 *
 * <p>This class contains methods to modify edit's content. For retrieving, publishing and deleting
 * edit see {@link ChangeEditUtil}.
 *
 * <p>
 */
@Singleton
public class ChangeEditModifier {

  private final TimeZone tz;
  private final ChangeIndexer indexer;
  private final Provider<ReviewDb> reviewDb;
  private final Provider<CurrentUser> currentUser;
  private final PermissionBackend permissionBackend;
  private final ChangeEditUtil changeEditUtil;
  private final PatchSetUtil patchSetUtil;
  private final ProjectCache projectCache;
  private final ApprovalsUtil approvalsUtil;

  @Inject
  ChangeEditModifier(
      @GerritPersonIdent PersonIdent gerritIdent,
      ChangeIndexer indexer,
      Provider<ReviewDb> reviewDb,
      Provider<CurrentUser> currentUser,
      PermissionBackend permissionBackend,
      ChangeEditUtil changeEditUtil,
      PatchSetUtil patchSetUtil,
      ProjectCache projectCache,
      ApprovalsUtil approvalsUtil) {
    this.indexer = indexer;
    this.reviewDb = reviewDb;
    this.currentUser = currentUser;
    this.permissionBackend = permissionBackend;
    this.tz = gerritIdent.getTimeZone();
    this.changeEditUtil = changeEditUtil;
    this.patchSetUtil = patchSetUtil;
    this.projectCache = projectCache;
    this.approvalsUtil = approvalsUtil;
  }

  /**
   * Creates a new change edit.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change for which the change edit should be created
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws InvalidChangeOperationException if a change edit already existed for the change
   * @throws PermissionBackendException
   */
  public void createEdit(Repository repository, ChangeNotes notes)
      throws AuthException, IOException, InvalidChangeOperationException, OrmException,
          PermissionBackendException, ResourceConflictException {
    assertCanEdit(notes);

    Optional<ChangeEdit> changeEdit = lookupChangeEdit(notes);
    if (changeEdit.isPresent()) {
      throw new InvalidChangeOperationException(
          String.format("A change edit already exists for change %s", notes.getChangeId()));
    }

    PatchSet currentPatchSet = lookupCurrentPatchSet(notes);
    ObjectId patchSetCommitId = getPatchSetCommitId(currentPatchSet);
    createEdit(repository, notes, currentPatchSet, patchSetCommitId, TimeUtil.nowTs());
  }

  /**
   * Rebase change edit on latest patch set
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change whose change edit should be rebased
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws InvalidChangeOperationException if a change edit doesn't exist for the specified
   *     change, the change edit is already based on the latest patch set, or the change represents
   *     the root commit
   * @throws MergeConflictException if rebase fails due to merge conflicts
   * @throws PermissionBackendException
   */
  public void rebaseEdit(Repository repository, ChangeNotes notes)
      throws AuthException, InvalidChangeOperationException, IOException, OrmException,
          MergeConflictException, PermissionBackendException, ResourceConflictException {
    assertCanEdit(notes);

    Optional<ChangeEdit> optionalChangeEdit = lookupChangeEdit(notes);
    if (!optionalChangeEdit.isPresent()) {
      throw new InvalidChangeOperationException(
          String.format("No change edit exists for change %s", notes.getChangeId()));
    }
    ChangeEdit changeEdit = optionalChangeEdit.get();

    PatchSet currentPatchSet = lookupCurrentPatchSet(notes);
    if (isBasedOn(changeEdit, currentPatchSet)) {
      throw new InvalidChangeOperationException(
          String.format(
              "Change edit for change %s is already based on latest patch set %s",
              notes.getChangeId(), currentPatchSet.getId()));
    }

    rebase(repository, changeEdit, currentPatchSet);
  }

  private void rebase(Repository repository, ChangeEdit changeEdit, PatchSet currentPatchSet)
      throws IOException, MergeConflictException, InvalidChangeOperationException, OrmException {
    RevCommit currentEditCommit = changeEdit.getEditCommit();
    if (currentEditCommit.getParentCount() == 0) {
      throw new InvalidChangeOperationException(
          "Rebase change edit against root commit not supported");
    }

    Change change = changeEdit.getChange();
    RevCommit basePatchSetCommit = lookupCommit(repository, currentPatchSet);
    RevTree basePatchSetTree = basePatchSetCommit.getTree();

    ObjectId newTreeId = merge(repository, changeEdit, basePatchSetTree);
    Timestamp nowTimestamp = TimeUtil.nowTs();
    String commitMessage = currentEditCommit.getFullMessage();
    ObjectId newEditCommitId =
        createCommit(repository, basePatchSetCommit, newTreeId, commitMessage, nowTimestamp);

    String newEditRefName = getEditRefName(change, currentPatchSet);
    updateReferenceWithNameChange(
        repository,
        changeEdit.getRefName(),
        currentEditCommit,
        newEditRefName,
        newEditCommitId,
        nowTimestamp);
    reindex(change);
  }

  /**
   * Modifies the commit message of a change edit. If the change edit doesn't exist, a new one will
   * be created based on the current patch set.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change whose change edit's message should be
   *     modified
   * @param newCommitMessage the new commit message
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws UnchangedCommitMessageException if the commit message is the same as before
   * @throws PermissionBackendException
   * @throws BadRequestException if the commit message is malformed
   */
  public void modifyMessage(Repository repository, ChangeNotes notes, String newCommitMessage)
      throws AuthException, IOException, UnchangedCommitMessageException, OrmException,
          PermissionBackendException, BadRequestException, ResourceConflictException {
    assertCanEdit(notes);
    newCommitMessage = CommitMessageUtil.checkAndSanitizeCommitMessage(newCommitMessage);

    Optional<ChangeEdit> optionalChangeEdit = lookupChangeEdit(notes);
    PatchSet basePatchSet = getBasePatchSet(optionalChangeEdit, notes);
    RevCommit basePatchSetCommit = lookupCommit(repository, basePatchSet);
    RevCommit baseCommit =
        optionalChangeEdit.map(ChangeEdit::getEditCommit).orElse(basePatchSetCommit);

    String currentCommitMessage = baseCommit.getFullMessage();
    if (newCommitMessage.equals(currentCommitMessage)) {
      throw new UnchangedCommitMessageException();
    }

    RevTree baseTree = baseCommit.getTree();
    Timestamp nowTimestamp = TimeUtil.nowTs();
    ObjectId newEditCommit =
        createCommit(repository, basePatchSetCommit, baseTree, newCommitMessage, nowTimestamp);

    if (optionalChangeEdit.isPresent()) {
      updateEdit(repository, optionalChangeEdit.get(), newEditCommit, nowTimestamp);
    } else {
      createEdit(repository, notes, basePatchSet, newEditCommit, nowTimestamp);
    }
  }

  /**
   * Modifies the contents of a file of a change edit. If the change edit doesn't exist, a new one
   * will be created based on the current patch set.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change whose change edit should be modified
   * @param filePath the path of the file whose contents should be modified
   * @param newContent the new file content
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws InvalidChangeOperationException if the file already had the specified content
   * @throws PermissionBackendException
   * @throws ResourceConflictException if the project state does not permit the operation
   */
  public void modifyFile(
      Repository repository, ChangeNotes notes, String filePath, RawInput newContent)
      throws AuthException, InvalidChangeOperationException, IOException, OrmException,
          PermissionBackendException, ResourceConflictException {
    modifyTree(repository, notes, new ChangeFileContentModification(filePath, newContent));
  }

  /**
   * Deletes a file from the Git tree of a change edit. If the change edit doesn't exist, a new one
   * will be created based on the current patch set.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change whose change edit should be modified
   * @param file path of the file which should be deleted
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws InvalidChangeOperationException if the file does not exist
   * @throws PermissionBackendException
   * @throws ResourceConflictException if the project state does not permit the operation
   */
  public void deleteFile(Repository repository, ChangeNotes notes, String file)
      throws AuthException, InvalidChangeOperationException, IOException, OrmException,
          PermissionBackendException, ResourceConflictException {
    modifyTree(repository, notes, new DeleteFileModification(file));
  }

  /**
   * Renames a file of a change edit or moves it to another directory. If the change edit doesn't
   * exist, a new one will be created based on the current patch set.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change whose change edit should be modified
   * @param currentFilePath the current path/name of the file
   * @param newFilePath the desired path/name of the file
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws InvalidChangeOperationException if the file was already renamed to the specified new
   *     name
   * @throws PermissionBackendException
   * @throws ResourceConflictException if the project state does not permit the operation
   */
  public void renameFile(
      Repository repository, ChangeNotes notes, String currentFilePath, String newFilePath)
      throws AuthException, InvalidChangeOperationException, IOException, OrmException,
          PermissionBackendException, ResourceConflictException {
    modifyTree(repository, notes, new RenameFileModification(currentFilePath, newFilePath));
  }

  /**
   * Restores a file of a change edit to the state it was in before the patch set on which the
   * change edit is based. If the change edit doesn't exist, a new one will be created based on the
   * current patch set.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change whose change edit should be modified
   * @param file the path of the file which should be restored
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws InvalidChangeOperationException if the file was already restored
   * @throws PermissionBackendException
   */
  public void restoreFile(Repository repository, ChangeNotes notes, String file)
      throws AuthException, InvalidChangeOperationException, IOException, OrmException,
          PermissionBackendException, ResourceConflictException {
    modifyTree(repository, notes, new RestoreFileModification(file));
  }

  private void modifyTree(
      Repository repository, ChangeNotes notes, TreeModification treeModification)
      throws AuthException, IOException, OrmException, InvalidChangeOperationException,
          PermissionBackendException, ResourceConflictException {
    assertCanEdit(notes);

    Optional<ChangeEdit> optionalChangeEdit = lookupChangeEdit(notes);
    PatchSet basePatchSet = getBasePatchSet(optionalChangeEdit, notes);
    RevCommit basePatchSetCommit = lookupCommit(repository, basePatchSet);
    RevCommit baseCommit =
        optionalChangeEdit.map(ChangeEdit::getEditCommit).orElse(basePatchSetCommit);

    ObjectId newTreeId = createNewTree(repository, baseCommit, ImmutableList.of(treeModification));

    String commitMessage = baseCommit.getFullMessage();
    Timestamp nowTimestamp = TimeUtil.nowTs();
    ObjectId newEditCommit =
        createCommit(repository, basePatchSetCommit, newTreeId, commitMessage, nowTimestamp);

    if (optionalChangeEdit.isPresent()) {
      updateEdit(repository, optionalChangeEdit.get(), newEditCommit, nowTimestamp);
    } else {
      createEdit(repository, notes, basePatchSet, newEditCommit, nowTimestamp);
    }
  }

  /**
   * Applies the indicated modifications to the specified patch set. If a change edit exists and is
   * based on the same patch set, the modified patch set tree is merged with the change edit. If the
   * change edit doesn't exist, a new one will be created.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change to which the patch set belongs
   * @param patchSet the {@code PatchSet} which should be modified
   * @param treeModifications the modifications which should be applied
   * @return the resulting {@code ChangeEdit}
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws InvalidChangeOperationException if the existing change edit is based on another patch
   *     set or no change edit exists but the specified patch set isn't the current one
   * @throws MergeConflictException if the modified patch set tree can't be merged with an existing
   *     change edit
   */
  public ChangeEdit combineWithModifiedPatchSetTree(
      Repository repository,
      ChangeNotes notes,
      PatchSet patchSet,
      List<TreeModification> treeModifications)
      throws AuthException, IOException, InvalidChangeOperationException, MergeConflictException,
          OrmException, PermissionBackendException, ResourceConflictException {
    assertCanEdit(notes);

    Optional<ChangeEdit> optionalChangeEdit = lookupChangeEdit(notes);
    ensureAllowedPatchSet(notes, optionalChangeEdit, patchSet);

    RevCommit patchSetCommit = lookupCommit(repository, patchSet);
    ObjectId newTreeId = createNewTree(repository, patchSetCommit, treeModifications);

    if (optionalChangeEdit.isPresent()) {
      ChangeEdit changeEdit = optionalChangeEdit.get();
      newTreeId = merge(repository, changeEdit, newTreeId);
      if (ObjectId.equals(newTreeId, changeEdit.getEditCommit().getTree())) {
        // Modifications are already contained in the change edit.
        return changeEdit;
      }
    }

    String commitMessage =
        optionalChangeEdit.map(ChangeEdit::getEditCommit).orElse(patchSetCommit).getFullMessage();
    Timestamp nowTimestamp = TimeUtil.nowTs();
    ObjectId newEditCommit =
        createCommit(repository, patchSetCommit, newTreeId, commitMessage, nowTimestamp);

    if (optionalChangeEdit.isPresent()) {
      return updateEdit(repository, optionalChangeEdit.get(), newEditCommit, nowTimestamp);
    }
    return createEdit(repository, notes, patchSet, newEditCommit, nowTimestamp);
  }

  private void assertCanEdit(ChangeNotes notes)
      throws AuthException, PermissionBackendException, IOException, ResourceConflictException,
          OrmException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    Change c = notes.getChange();
    if (!c.getStatus().isOpen()) {
      throw new ResourceConflictException(
          String.format(
              "change %s is %s", c.getChangeId(), c.getStatus().toString().toLowerCase()));
    }

    // Not allowed to edit if the current patch set is locked.
    if (isPatchSetLocked(approvalsUtil, projectCache, reviewDb.get(), notes, currentUser.get())) {
      throw new ResourceConflictException(
          String.format("The current patch set of change %s is locked", notes.getChangeId()));
    }

    try {
      permissionBackend
          .currentUser()
          .database(reviewDb)
          .change(notes)
          .check(ChangePermission.ADD_PATCH_SET);
      projectCache.checkedGet(notes.getProjectName()).checkStatePermitsWrite();
    } catch (AuthException denied) {
      throw new AuthException("edit not permitted", denied);
    }
  }

  private static void ensureAllowedPatchSet(
      ChangeNotes notes, Optional<ChangeEdit> optionalChangeEdit, PatchSet patchSet)
      throws InvalidChangeOperationException {
    if (optionalChangeEdit.isPresent()) {
      ChangeEdit changeEdit = optionalChangeEdit.get();
      if (!isBasedOn(changeEdit, patchSet)) {
        throw new InvalidChangeOperationException(
            String.format(
                "Only the patch set %s on which the existing change edit is based may be modified "
                    + "(specified patch set: %s)",
                changeEdit.getBasePatchSet().getId(), patchSet.getId()));
      }
    } else {
      PatchSet.Id patchSetId = patchSet.getId();
      PatchSet.Id currentPatchSetId = notes.getChange().currentPatchSetId();
      if (!patchSetId.equals(currentPatchSetId)) {
        throw new InvalidChangeOperationException(
            String.format(
                "A change edit may only be created for the current patch set %s (and not for %s)",
                currentPatchSetId, patchSetId));
      }
    }
  }

  private Optional<ChangeEdit> lookupChangeEdit(ChangeNotes notes)
      throws AuthException, IOException {
    return changeEditUtil.byChange(notes);
  }

  private PatchSet getBasePatchSet(Optional<ChangeEdit> optionalChangeEdit, ChangeNotes notes)
      throws OrmException {
    Optional<PatchSet> editBasePatchSet = optionalChangeEdit.map(ChangeEdit::getBasePatchSet);
    return editBasePatchSet.isPresent() ? editBasePatchSet.get() : lookupCurrentPatchSet(notes);
  }

  private PatchSet lookupCurrentPatchSet(ChangeNotes notes) throws OrmException {
    return patchSetUtil.current(reviewDb.get(), notes);
  }

  private static boolean isBasedOn(ChangeEdit changeEdit, PatchSet patchSet) {
    PatchSet editBasePatchSet = changeEdit.getBasePatchSet();
    return editBasePatchSet.getId().equals(patchSet.getId());
  }

  private static RevCommit lookupCommit(Repository repository, PatchSet patchSet)
      throws IOException {
    ObjectId patchSetCommitId = getPatchSetCommitId(patchSet);
    return lookupCommit(repository, patchSetCommitId);
  }

  private static RevCommit lookupCommit(Repository repository, ObjectId commitId)
      throws IOException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      return revWalk.parseCommit(commitId);
    }
  }

  private static ObjectId createNewTree(
      Repository repository, RevCommit baseCommit, List<TreeModification> treeModifications)
      throws IOException, InvalidChangeOperationException {
    TreeCreator treeCreator = new TreeCreator(baseCommit);
    treeCreator.addTreeModifications(treeModifications);
    ObjectId newTreeId = treeCreator.createNewTreeAndGetId(repository);

    if (ObjectId.equals(newTreeId, baseCommit.getTree())) {
      throw new InvalidChangeOperationException("no changes were made");
    }
    return newTreeId;
  }

  private static ObjectId merge(Repository repository, ChangeEdit changeEdit, ObjectId newTreeId)
      throws IOException, MergeConflictException {
    PatchSet basePatchSet = changeEdit.getBasePatchSet();
    ObjectId basePatchSetCommitId = getPatchSetCommitId(basePatchSet);
    ObjectId editCommitId = changeEdit.getEditCommit();

    ThreeWayMerger threeWayMerger = MergeStrategy.RESOLVE.newMerger(repository, true);
    threeWayMerger.setBase(basePatchSetCommitId);
    boolean successful = threeWayMerger.merge(newTreeId, editCommitId);

    if (!successful) {
      throw new MergeConflictException(
          "The existing change edit could not be merged with another tree.");
    }
    return threeWayMerger.getResultTreeId();
  }

  private ObjectId createCommit(
      Repository repository,
      RevCommit basePatchSetCommit,
      ObjectId tree,
      String commitMessage,
      Timestamp timestamp)
      throws IOException {
    try (ObjectInserter objectInserter = repository.newObjectInserter()) {
      CommitBuilder builder = new CommitBuilder();
      builder.setTreeId(tree);
      builder.setParentIds(basePatchSetCommit.getParents());
      builder.setAuthor(basePatchSetCommit.getAuthorIdent());
      builder.setCommitter(getCommitterIdent(timestamp));
      builder.setMessage(commitMessage);
      ObjectId newCommitId = objectInserter.insert(builder);
      objectInserter.flush();
      return newCommitId;
    }
  }

  private PersonIdent getCommitterIdent(Timestamp commitTimestamp) {
    IdentifiedUser user = currentUser.get().asIdentifiedUser();
    return user.newCommitterIdent(commitTimestamp, tz);
  }

  private static ObjectId getPatchSetCommitId(PatchSet patchSet) {
    return ObjectId.fromString(patchSet.getRevision().get());
  }

  private ChangeEdit createEdit(
      Repository repository,
      ChangeNotes notes,
      PatchSet basePatchSet,
      ObjectId newEditCommitId,
      Timestamp timestamp)
      throws IOException, OrmException {
    Change change = notes.getChange();
    String editRefName = getEditRefName(change, basePatchSet);
    updateReference(repository, editRefName, ObjectId.zeroId(), newEditCommitId, timestamp);
    reindex(change);

    RevCommit newEditCommit = lookupCommit(repository, newEditCommitId);
    return new ChangeEdit(change, editRefName, newEditCommit, basePatchSet);
  }

  private String getEditRefName(Change change, PatchSet basePatchSet) {
    IdentifiedUser me = currentUser.get().asIdentifiedUser();
    return RefNames.refsEdit(me.getAccountId(), change.getId(), basePatchSet.getId());
  }

  private ChangeEdit updateEdit(
      Repository repository, ChangeEdit changeEdit, ObjectId newEditCommitId, Timestamp timestamp)
      throws IOException, OrmException {
    String editRefName = changeEdit.getRefName();
    RevCommit currentEditCommit = changeEdit.getEditCommit();
    updateReference(repository, editRefName, currentEditCommit, newEditCommitId, timestamp);
    reindex(changeEdit.getChange());

    RevCommit newEditCommit = lookupCommit(repository, newEditCommitId);
    return new ChangeEdit(
        changeEdit.getChange(), editRefName, newEditCommit, changeEdit.getBasePatchSet());
  }

  private void updateReference(
      Repository repository,
      String refName,
      ObjectId currentObjectId,
      ObjectId targetObjectId,
      Timestamp timestamp)
      throws IOException {
    RefUpdate ru = repository.updateRef(refName);
    ru.setExpectedOldObjectId(currentObjectId);
    ru.setNewObjectId(targetObjectId);
    ru.setRefLogIdent(getRefLogIdent(timestamp));
    ru.setRefLogMessage("inline edit (amend)", false);
    ru.setForceUpdate(true);
    try (RevWalk revWalk = new RevWalk(repository)) {
      RefUpdate.Result res = ru.update(revWalk);
      if (res != RefUpdate.Result.NEW && res != RefUpdate.Result.FORCED) {
        throw new IOException(
            "cannot update "
                + ru.getName()
                + " in "
                + repository.getDirectory()
                + ": "
                + ru.getResult());
      }
    }
  }

  private void updateReferenceWithNameChange(
      Repository repository,
      String currentRefName,
      ObjectId currentObjectId,
      String newRefName,
      ObjectId targetObjectId,
      Timestamp timestamp)
      throws IOException {
    BatchRefUpdate batchRefUpdate = repository.getRefDatabase().newBatchUpdate();
    batchRefUpdate.addCommand(new ReceiveCommand(ObjectId.zeroId(), targetObjectId, newRefName));
    batchRefUpdate.addCommand(
        new ReceiveCommand(currentObjectId, ObjectId.zeroId(), currentRefName));
    batchRefUpdate.setRefLogMessage("rebase edit", false);
    batchRefUpdate.setRefLogIdent(getRefLogIdent(timestamp));
    try (RevWalk revWalk = new RevWalk(repository)) {
      batchRefUpdate.execute(revWalk, NullProgressMonitor.INSTANCE);
    }
    for (ReceiveCommand cmd : batchRefUpdate.getCommands()) {
      if (cmd.getResult() != ReceiveCommand.Result.OK) {
        throw new IOException("failed: " + cmd);
      }
    }
  }

  private PersonIdent getRefLogIdent(Timestamp timestamp) {
    IdentifiedUser user = currentUser.get().asIdentifiedUser();
    return user.newRefLogIdent(timestamp, tz);
  }

  private void reindex(Change change) throws IOException, OrmException {
    indexer.index(reviewDb.get(), change);
  }
}
