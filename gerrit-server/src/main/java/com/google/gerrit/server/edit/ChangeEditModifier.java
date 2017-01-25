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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
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
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

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

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Utility functions to manipulate change edits.
 * <p>
 * This class contains methods to modify edit's content.
 * For retrieving, publishing and deleting edit see
 * {@link ChangeEditUtil}.
 * <p>
 */
@Singleton
public class ChangeEditModifier {

  private final TimeZone tz;
  private final ChangeIndexer indexer;
  private final Provider<ReviewDb> reviewDb;
  private final Provider<CurrentUser> currentUser;
  private final ChangeEditUtil changeEditUtil;
  private final PatchSetUtil patchSetUtil;

  @Inject
  ChangeEditModifier(@GerritPersonIdent PersonIdent gerritIdent,
      ChangeIndexer indexer,
      Provider<ReviewDb> reviewDb,
      Provider<CurrentUser> currentUser,
      ChangeEditUtil changeEditUtil,
      PatchSetUtil patchSetUtil) {
    this.indexer = indexer;
    this.reviewDb = reviewDb;
    this.currentUser = currentUser;
    this.tz = gerritIdent.getTimeZone();
    this.changeEditUtil = changeEditUtil;
    this.patchSetUtil = patchSetUtil;
  }

  /**
   * Creates a new change edit.
   *
   * @param repository the affected Git repository
   * @param changeControl the {@code ChangeControl} of the change for which
   * the change edit should be created
   * @throws AuthException if the user isn't authenticated or not allowed to
   * use change edits
   * @throws InvalidChangeOperationException if a change edit already existed
   * for the change
   */
  public void createEdit(Repository repository, ChangeControl changeControl)
      throws AuthException, IOException, InvalidChangeOperationException,
      OrmException {
    ensureAuthenticatedAndPermitted(changeControl);

    Optional<ChangeEdit> changeEdit = lookupChangeEdit(changeControl);
    if (changeEdit.isPresent()) {
      throw new InvalidChangeOperationException(String.format("A change edit "
          + "already exists for change %s", changeControl.getId()));
    }

    PatchSet currentPatchSet = lookupCurrentPatchSet(changeControl);
    ObjectId patchSetCommitId = getPatchSetCommitId(currentPatchSet);
    createEditReference(repository, changeControl, currentPatchSet,
        patchSetCommitId, TimeUtil.nowTs());
  }

  /**
   * Rebase change edit on latest patch set
   *
   * @param repository the affected Git repository
   * @param changeControl the {@code ChangeControl} of the change whose change
   * edit should be rebased
   * @param changeEdit the change edit which should be rebased
   * @throws AuthException if the user isn't authenticated
   * @throws InvalidChangeOperationException if the change edit is already based
   * on the latest patch set, or the change represents the root commit
   * @throws MergeConflictException if rebase fails due to merge conflicts
   */
  public void rebaseEdit(Repository repository, ChangeControl changeControl,
      ChangeEdit changeEdit)
      throws AuthException, InvalidChangeOperationException, IOException,
      OrmException, MergeConflictException {
    ensureAuthenticated();

    PatchSet currentPatchSet = lookupCurrentPatchSet(changeControl);
    if (isBasedOn(changeEdit, currentPatchSet)) {
      throw new InvalidChangeOperationException(String.format(
          "Change edit for change %s is already based on latest patch set %s",
          changeControl.getId(), currentPatchSet.getId()));
    }

    rebase(repository, changeEdit, currentPatchSet);
  }

  private void rebase(Repository repository, ChangeEdit changeEdit,
      PatchSet currentPatchSet) throws IOException, MergeConflictException,
      InvalidChangeOperationException {
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
    ObjectId newEditCommitId = createCommit(repository, basePatchSetCommit,
        newTreeId, commitMessage, nowTimestamp);

    String newEditRefName = getEditRefName(change, currentPatchSet);
    updateReferenceWithNameChange(repository, changeEdit.getRefName(),
        currentEditCommit, newEditRefName, newEditCommitId, nowTimestamp);
  }

  /**
   * Modify commit message in existing change edit.
   *
   * @param repository the affected Git repository
   * @param newCommitMessage the new commit message
   * @param changeEdit the change edit whose commit message should be modified
   * @throws AuthException if the user isn't authenticated
   * @throws UnchangedCommitMessageException if the commit message is the same
   * as before
   */
  public void modifyMessage(Repository repository, String newCommitMessage,
      ChangeEdit changeEdit) throws AuthException, IOException,
      UnchangedCommitMessageException {
    ensureAuthenticated();
    newCommitMessage = getWellFormedCommitMessage(newCommitMessage);

    PatchSet basePatchSet = changeEdit.getBasePatchSet();
    RevCommit basePatchSetCommit = lookupCommit(repository, basePatchSet);
    RevCommit editCommit = changeEdit.getEditCommit();

    String currentCommitMessage = editCommit.getFullMessage();
    if (newCommitMessage.equals(currentCommitMessage)) {
      throw new UnchangedCommitMessageException();
    }

    RevTree editTree = editCommit.getTree();
    Timestamp nowTimestamp = TimeUtil.nowTs();
    ObjectId newEditCommit = createCommit(repository, basePatchSetCommit,
        editTree, newCommitMessage, nowTimestamp);

    updateEditReference(repository, changeEdit, newEditCommit, nowTimestamp);
  }

  /**
   * Modify file in existing change edit from its base commit.
   *
   * @param repository the affected Git repository
   * @param changeEdit the change edit which should be modified
   * @param filePath the path of the file whose contents should be modified
   * @param newContent the new file content
   * @throws AuthException if the user isn't authenticated
   * @throws InvalidChangeOperationException if the file already had the
   * specified content
   */
  public void modifyFile(Repository repository, ChangeEdit changeEdit,
      String filePath, RawInput newContent) throws AuthException,
      InvalidChangeOperationException, IOException {
    modifyTree(repository, changeEdit,
        new ChangeFileContentModification(filePath, newContent));
  }

  /**
   * Delete file in existing change edit.
   *
   * @param repository the affected Git repository
   * @param changeEdit the change edit which should be modified
   * @param file path of the file which should be deleted
   * @throws AuthException if the user isn't authenticated
   * @throws InvalidChangeOperationException if the file does not exist
   */
  public void deleteFile(Repository repository, ChangeEdit changeEdit,
      String file) throws AuthException, InvalidChangeOperationException,
      IOException {
    modifyTree(repository, changeEdit, new DeleteFileModification(file));
  }

  /**
   * Rename file in existing change edit or move it to another directory.
   *
   * @param repository the affected Git repository
   * @param edit the change edit which should be modified
   * @param currentFilePath the current path/name of the file
   * @param newFilePath the desired path/name of the file
   * @throws AuthException if the user isn't authenticated
   * @throws InvalidChangeOperationException if the file was already renamed
   * to the specified new name
   */
  public void renameFile(Repository repository, ChangeEdit edit,
      String currentFilePath, String newFilePath) throws AuthException,
      InvalidChangeOperationException, IOException {
    RevCommit editCommit = edit.getEditCommit();
    modifyTree(repository, edit,
        new RenameFileModification(editCommit, currentFilePath, newFilePath));
  }

  /**
   * Restore file in existing change edit.
   *
   * @param repository the affected Git repository
   * @param edit the change edit which should be modified
   * @param file the path of the file which should be restored
   * @throws AuthException if the user isn't authenticated
   * @throws InvalidChangeOperationException if the file was already restored
   */
  public void restoreFile(Repository repository, ChangeEdit edit, String file)
      throws AuthException, InvalidChangeOperationException, IOException {
    RevCommit editCommit = edit.getEditCommit();
    modifyTree(repository, edit, new RestoreFileModification(editCommit, file));
  }

  private void modifyTree(Repository repository, ChangeEdit changeEdit,
      TreeModification treeModification) throws AuthException, IOException,
      InvalidChangeOperationException {
    ensureAuthenticated();

    PatchSet basePatchSet = changeEdit.getBasePatchSet();
    RevCommit basePatchSetCommit = lookupCommit(repository, basePatchSet);
    RevCommit editCommit = changeEdit.getEditCommit();

    ObjectId newTreeId = createNewTree(repository, editCommit,
        treeModification);

    String commitMessage = editCommit.getFullMessage();
    Timestamp nowTimestamp = TimeUtil.nowTs();
    ObjectId newEditCommit = createCommit(repository, basePatchSetCommit,
        newTreeId, commitMessage, nowTimestamp);

    updateEditReference(repository, changeEdit, newEditCommit, nowTimestamp);
  }

  private void ensureAuthenticatedAndPermitted(ChangeControl changeControl)
      throws AuthException, OrmException {
    ensureAuthenticated();
    ensurePermitted(changeControl);
  }

  private void ensureAuthenticated() throws AuthException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
  }

  private void ensurePermitted(ChangeControl changeControl)
      throws OrmException, AuthException {
    if (!changeControl.canAddPatchSet(reviewDb.get())) {
      throw new AuthException("Not allowed to edit a change.");
    }
  }

  private String getWellFormedCommitMessage(String commitMessage) {
    String wellFormedMessage = Strings.nullToEmpty(commitMessage).trim();
    checkState(!wellFormedMessage.isEmpty(),
        "Commit message cannot be null or empty");
    wellFormedMessage = wellFormedMessage + "\n";
    return wellFormedMessage;
  }

  private Optional<ChangeEdit> lookupChangeEdit(ChangeControl changeControl)
      throws AuthException, IOException {
    return changeEditUtil.byChange(changeControl);
  }

  private PatchSet lookupCurrentPatchSet(ChangeControl changeControl)
      throws OrmException {
    return patchSetUtil.current(reviewDb.get(), changeControl.getNotes());
  }

  private static boolean isBasedOn(ChangeEdit changeEdit, PatchSet patchSet) {
    PatchSet editBasePatchSet = changeEdit.getBasePatchSet();
    return editBasePatchSet.getId().equals(patchSet.getId());
  }

  private static RevCommit lookupCommit(Repository repository,
      PatchSet patchSet) throws IOException {
    ObjectId patchSetCommitId = getPatchSetCommitId(patchSet);
    return lookup(repository, patchSetCommitId);
  }

  private static RevCommit lookup(Repository repository, ObjectId commitId)
      throws IOException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      return revWalk.parseCommit(commitId);
    }
  }

  private static ObjectId createNewTree(Repository repository,
      RevCommit baseCommit, TreeModification treeModification)
      throws IOException, InvalidChangeOperationException {
    TreeCreator treeCreator = new TreeCreator(baseCommit.getTree());
    treeCreator.addTreeModification(treeModification);
    ObjectId newTreeId = treeCreator.createNewTreeAndGetId(repository);

    if (ObjectId.equals(newTreeId, baseCommit.getTree())) {
      throw new InvalidChangeOperationException("no changes were made");
    }
    return newTreeId;
  }

  private ObjectId merge(Repository repository, ChangeEdit changeEdit,
      ObjectId newTreeId) throws IOException, MergeConflictException {
    PatchSet basePatchSet = changeEdit.getBasePatchSet();
    ObjectId basePatchSetCommitId = getPatchSetCommitId(basePatchSet);
    ObjectId editCommitId = changeEdit.getEditCommit();

    ThreeWayMerger threeWayMerger =
        MergeStrategy.RESOLVE.newMerger(repository, true);
    threeWayMerger.setBase(basePatchSetCommitId);
    boolean successful = threeWayMerger.merge(newTreeId, editCommitId);

    if (!successful) {
      throw new MergeConflictException(
          "The existing change edit could not be merged with another tree.");
    }
    return threeWayMerger.getResultTreeId();
  }

  private ObjectId createCommit(Repository repository,
      RevCommit basePatchSetCommit, ObjectId tree, String commitMessage,
      Timestamp timestamp) throws IOException {
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

  private void createEditReference(Repository repository,
      ChangeControl changeControl, PatchSet basePatchSet,
      ObjectId newEditCommit, Timestamp timestamp)
      throws IOException, OrmException {
    Change change = changeControl.getChange();
    String editRefName = getEditRefName(change, basePatchSet);
    updateReference(repository, editRefName, ObjectId.zeroId(), newEditCommit,
        timestamp);
    reindex(change);
  }

  private void reindex(Change change) throws IOException, OrmException {
    indexer.index(reviewDb.get(), change);
  }

  private String getEditRefName(Change change, PatchSet basePatchSet) {
    IdentifiedUser me = currentUser.get().asIdentifiedUser();
    return RefNames.refsEdit(me.getAccountId(), change.getId(),
        basePatchSet.getId());
  }

  private void updateEditReference(Repository repository, ChangeEdit changeEdit,
      ObjectId newEditCommit, Timestamp timestamp) throws IOException {
    String editRefName = changeEdit.getRefName();
    RevCommit currentEditCommit = changeEdit.getEditCommit();
    updateReference(repository, editRefName, currentEditCommit, newEditCommit,
        timestamp);
  }

  private void updateReference(Repository repository, String refName,
      ObjectId currentObjectId, ObjectId targetObjectId, Timestamp timestamp)
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
        throw new IOException("update failed: " + ru);
      }
    }
  }

  private void updateReferenceWithNameChange(Repository repository,
      String currentRefName, ObjectId currentObjectId, String newRefName,
      ObjectId targetObjectId, Timestamp timestamp)
      throws IOException {
    BatchRefUpdate batchRefUpdate =
        repository.getRefDatabase().newBatchUpdate();
    batchRefUpdate.addCommand(new ReceiveCommand(ObjectId.zeroId(),
        targetObjectId, newRefName));
    batchRefUpdate.addCommand(new ReceiveCommand(currentObjectId,
        ObjectId.zeroId(), currentRefName));
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
}
