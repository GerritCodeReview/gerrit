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

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.common.base.Charsets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.edit.tree.DeleteFileModification;
import com.google.gerrit.server.edit.tree.RenameFileModification;
import com.google.gerrit.server.edit.tree.RestoreFileModification;
import com.google.gerrit.server.edit.tree.TreeCreator;
import com.google.gerrit.server.edit.tree.TreeModification;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeAlgorithm;
import org.eclipse.jgit.merge.MergeChunk;
import org.eclipse.jgit.merge.MergeResult;
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

  private final ZoneId zoneId;
  private final Provider<CurrentUser> currentUser;
  private final PermissionBackend permissionBackend;
  private final ChangeEditUtil changeEditUtil;
  private final PatchSetUtil patchSetUtil;
  private final ProjectCache projectCache;
  private final NoteDbEdits noteDbEdits;
  private final DynamicItem<UrlFormatter> urlFormatter;

  @Inject
  ChangeEditModifier(
      @GerritPersonIdent PersonIdent gerritIdent,
      ChangeIndexer indexer,
      Provider<CurrentUser> currentUser,
      PermissionBackend permissionBackend,
      ChangeEditUtil changeEditUtil,
      PatchSetUtil patchSetUtil,
      ProjectCache projectCache,
      GitReferenceUpdated gitReferenceUpdated,
      DynamicItem<UrlFormatter> urlFormatter) {
    this.currentUser = currentUser;
    this.permissionBackend = permissionBackend;
    this.zoneId = gerritIdent.getZoneId();
    this.changeEditUtil = changeEditUtil;
    this.patchSetUtil = patchSetUtil;
    this.projectCache = projectCache;
    noteDbEdits = new NoteDbEdits(gitReferenceUpdated, zoneId, indexer, currentUser);
    this.urlFormatter = urlFormatter;
  }

  /**
   * Creates a new change edit.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change for which the change edit should be created
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws InvalidChangeOperationException if a change edit already existed for the change
   */
  public void createEdit(Repository repository, ChangeNotes notes)
      throws AuthException, IOException, InvalidChangeOperationException,
          PermissionBackendException, ResourceConflictException {
    assertCanEdit(notes);

    Optional<ChangeEdit> changeEdit = lookupChangeEdit(notes);
    if (changeEdit.isPresent()) {
      throw new InvalidChangeOperationException(
          String.format("A change edit already exists for change %s", notes.getChangeId()));
    }

    PatchSet currentPatchSet = lookupCurrentPatchSet(notes);
    ObjectId patchSetCommitId = currentPatchSet.commitId();
    noteDbEdits.createEdit(repository, notes, currentPatchSet, patchSetCommitId, TimeUtil.now());
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
   */
  public void rebaseEdit(Repository repository, ChangeNotes notes)
      throws AuthException, InvalidChangeOperationException, IOException,
          PermissionBackendException, ResourceConflictException {
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
              notes.getChangeId(), currentPatchSet.id()));
    }

    rebase(notes.getProjectName(), repository, changeEdit, currentPatchSet);
  }

  private void rebase(
      Project.NameKey project,
      Repository repository,
      ChangeEdit changeEdit,
      PatchSet currentPatchSet)
      throws IOException, MergeConflictException, InvalidChangeOperationException {
    RevCommit currentEditCommit = changeEdit.getEditCommit();
    if (currentEditCommit.getParentCount() == 0) {
      throw new InvalidChangeOperationException(
          "Rebase change edit against root commit not supported");
    }

    RevCommit basePatchSetCommit = NoteDbEdits.lookupCommit(repository, currentPatchSet.commitId());
    RevTree basePatchSetTree = basePatchSetCommit.getTree();

    ObjectId newTreeId = merge(repository, changeEdit, basePatchSetTree);
    Instant nowTimestamp = TimeUtil.now();
    String commitMessage = currentEditCommit.getFullMessage();
    ObjectId newEditCommitId =
        createCommit(repository, basePatchSetCommit, newTreeId, commitMessage, nowTimestamp);

    noteDbEdits.baseEditOnDifferentPatchset(
        project,
        repository,
        changeEdit,
        currentPatchSet,
        currentEditCommit,
        newEditCommitId,
        nowTimestamp);
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
   * @throws InvalidChangeOperationException if the commit message is the same as before
   * @throws BadRequestException if the commit message is malformed
   */
  public void modifyMessage(Repository repository, ChangeNotes notes, String newCommitMessage)
      throws AuthException, IOException, InvalidChangeOperationException,
          PermissionBackendException, BadRequestException, ResourceConflictException {
    modifyCommit(
        repository,
        notes,
        new ModificationIntention.LatestCommit(),
        CommitModification.builder().newCommitMessage(newCommitMessage).build());
  }

  /**
   * Modifies the contents of a file of a change edit. If the change edit doesn't exist, a new one
   * will be created based on the current patch set.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change whose change edit should be modified
   * @param filePath the path of the file whose contents should be modified
   * @param newContent the new file content
   * @param newGitFileMode the new file mode in octal format. {@code null} indicates no change
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws BadRequestException if the user provided bad input (e.g. invalid file paths)
   * @throws InvalidChangeOperationException if the file already had the specified content
   * @throws ResourceConflictException if the project state does not permit the operation
   */
  public void modifyFile(
      Repository repository,
      ChangeNotes notes,
      String filePath,
      RawInput newContent,
      @Nullable Integer newGitFileMode)
      throws AuthException, BadRequestException, InvalidChangeOperationException, IOException,
          PermissionBackendException, ResourceConflictException {
    modifyCommit(
        repository,
        notes,
        new ModificationIntention.LatestCommit(),
        CommitModification.builder()
            .addTreeModification(
                new ChangeFileContentModification(filePath, newContent, newGitFileMode))
            .build());
  }

  /**
   * Deletes a file from the Git tree of a change edit. If the change edit doesn't exist, a new one
   * will be created based on the current patch set.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change whose change edit should be modified
   * @param file path of the file which should be deleted
   * @throws AuthException if the user isn't authenticated or not allowed to use change edits
   * @throws BadRequestException if the user provided bad input (e.g. invalid file paths)
   * @throws InvalidChangeOperationException if the file does not exist
   * @throws ResourceConflictException if the project state does not permit the operation
   */
  public void deleteFile(Repository repository, ChangeNotes notes, String file)
      throws AuthException, BadRequestException, InvalidChangeOperationException, IOException,
          PermissionBackendException, ResourceConflictException {
    modifyCommit(
        repository,
        notes,
        new ModificationIntention.LatestCommit(),
        CommitModification.builder().addTreeModification(new DeleteFileModification(file)).build());
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
   * @throws BadRequestException if the user provided bad input (e.g. invalid file paths)
   * @throws InvalidChangeOperationException if the file was already renamed to the specified new
   *     name
   * @throws ResourceConflictException if the project state does not permit the operation
   */
  public void renameFile(
      Repository repository, ChangeNotes notes, String currentFilePath, String newFilePath)
      throws AuthException, BadRequestException, InvalidChangeOperationException, IOException,
          PermissionBackendException, ResourceConflictException {
    modifyCommit(
        repository,
        notes,
        new ModificationIntention.LatestCommit(),
        CommitModification.builder()
            .addTreeModification(new RenameFileModification(currentFilePath, newFilePath))
            .build());
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
   */
  public void restoreFile(Repository repository, ChangeNotes notes, String file)
      throws AuthException, BadRequestException, InvalidChangeOperationException, IOException,
          PermissionBackendException, ResourceConflictException {
    modifyCommit(
        repository,
        notes,
        new ModificationIntention.LatestCommit(),
        CommitModification.builder()
            .addTreeModification(new RestoreFileModification(file))
            .build());
  }

  /**
   * Applies the indicated modifications to the specified patch set. If a change edit exists and is
   * based on the same patch set, the modified patch set tree is merged with the change edit. If the
   * change edit doesn't exist, a new one will be created.
   *
   * @param repository the affected Git repository
   * @param notes the {@link ChangeNotes} of the change to which the patch set belongs
   * @param patchSet the {@code PatchSet} which should be modified
   * @param commitModification the modifications which should be applied
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
      CommitModification commitModification)
      throws AuthException, BadRequestException, IOException, InvalidChangeOperationException,
          PermissionBackendException, ResourceConflictException {
    return modifyCommit(
        repository, notes, new ModificationIntention.PatchsetCommit(patchSet), commitModification);
  }

  private ChangeEdit modifyCommit(
      Repository repository,
      ChangeNotes notes,
      ModificationIntention modificationIntention,
      CommitModification commitModification)
      throws AuthException, BadRequestException, IOException, InvalidChangeOperationException,
          PermissionBackendException, ResourceConflictException {
    assertCanEdit(notes);

    Optional<ChangeEdit> optionalChangeEdit = lookupChangeEdit(notes);
    EditBehavior editBehavior =
        optionalChangeEdit
            .<EditBehavior>map(changeEdit -> new ExistingEditBehavior(changeEdit, noteDbEdits))
            .orElseGet(() -> new NewEditBehavior(noteDbEdits));
    ModificationTarget modificationTarget =
        editBehavior.getModificationTarget(notes, modificationIntention);

    RevCommit commitToModify = modificationTarget.getCommit(repository);
    ObjectId newTreeId =
        createNewTree(repository, commitToModify, commitModification.treeModifications());
    newTreeId = editBehavior.mergeTreesIfNecessary(repository, newTreeId, commitToModify);

    PatchSet basePatchset = modificationTarget.getBasePatchset();
    RevCommit basePatchsetCommit = NoteDbEdits.lookupCommit(repository, basePatchset.commitId());

    boolean changeIdRequired =
        projectCache
            .get(notes.getChange().getProject())
            .orElseThrow(illegalState(notes.getChange().getProject()))
            .is(BooleanProjectConfig.REQUIRE_CHANGE_ID);
    String currentChangeId = notes.getChange().getKey().get();
    String newCommitMessage =
        createNewCommitMessage(
            changeIdRequired, currentChangeId, editBehavior, commitModification, commitToModify);
    newCommitMessage = editBehavior.mergeCommitMessageIfNecessary(newCommitMessage, commitToModify);

    Optional<ChangeEdit> unmodifiedEdit =
        editBehavior.getEditIfNoModification(newTreeId, newCommitMessage);
    if (unmodifiedEdit.isPresent()) {
      return unmodifiedEdit.get();
    }

    Instant nowTimestamp = TimeUtil.now();
    ObjectId newEditCommit =
        createCommit(repository, basePatchsetCommit, newTreeId, newCommitMessage, nowTimestamp);

    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      return editBehavior.updateEditInStorage(
          repository, notes, basePatchset, newEditCommit, nowTimestamp);
    }
  }

  private void assertCanEdit(ChangeNotes notes)
      throws AuthException, PermissionBackendException, ResourceConflictException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    Change c = notes.getChange();
    if (!c.isNew()) {
      throw new ResourceConflictException(
          String.format("change %s is %s", c.getChangeId(), ChangeUtil.status(c)));
    }

    // Not allowed to edit if the current patch set is locked.
    patchSetUtil.checkPatchSetNotLocked(notes);
    boolean canEdit =
        permissionBackend.currentUser().change(notes).test(ChangePermission.ADD_PATCH_SET);
    canEdit &=
        projectCache
            .get(notes.getProjectName())
            .orElseThrow(illegalState(notes.getProjectName()))
            .statePermitsWrite();
    if (!canEdit) {
      throw new AuthException("edit not permitted");
    }
  }

  private Optional<ChangeEdit> lookupChangeEdit(ChangeNotes notes)
      throws AuthException, IOException {
    return changeEditUtil.byChange(notes);
  }

  private PatchSet lookupCurrentPatchSet(ChangeNotes notes) {
    return patchSetUtil.current(notes);
  }

  private static boolean isBasedOn(ChangeEdit changeEdit, PatchSet patchSet) {
    PatchSet editBasePatchSet = changeEdit.getBasePatchSet();
    return editBasePatchSet.id().equals(patchSet.id());
  }

  private static ObjectId createNewTree(
      Repository repository, RevCommit baseCommit, List<TreeModification> treeModifications)
      throws BadRequestException, IOException, InvalidChangeOperationException {
    if (treeModifications.isEmpty()) {
      return baseCommit.getTree();
    }

    ObjectId newTreeId;
    try {
      TreeCreator treeCreator = TreeCreator.basedOn(baseCommit);
      treeCreator.addTreeModifications(treeModifications);
      newTreeId = treeCreator.createNewTreeAndGetId(repository);
    } catch (InvalidPathException e) {
      throw new BadRequestException(e.getMessage());
    }

    if (ObjectId.isEqual(newTreeId, baseCommit.getTree())) {
      throw new InvalidChangeOperationException("no changes were made");
    }
    return newTreeId;
  }

  private static ObjectId merge(Repository repository, ChangeEdit changeEdit, ObjectId newTreeId)
      throws IOException, MergeConflictException {
    PatchSet basePatchSet = changeEdit.getBasePatchSet();
    ObjectId basePatchSetCommitId = basePatchSet.commitId();
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

  private String createNewCommitMessage(
      boolean requireChangeId,
      String currentChangeId,
      EditBehavior editBehavior,
      CommitModification commitModification,
      RevCommit commitToModify)
      throws InvalidChangeOperationException, BadRequestException, ResourceConflictException {
    if (!commitModification.newCommitMessage().isPresent()) {
      return editBehavior.getUnmodifiedCommitMessage(commitToModify);
    }

    String newCommitMessage =
        CommitMessageUtil.checkAndSanitizeCommitMessage(
            commitModification.newCommitMessage().get());

    if (newCommitMessage.equals(commitToModify.getFullMessage())) {
      throw new InvalidChangeOperationException(
          "New commit message cannot be same as existing commit message");
    }

    ChangeUtil.ensureChangeIdIsCorrect(
        requireChangeId, currentChangeId, newCommitMessage, urlFormatter.get());

    return newCommitMessage;
  }

  private ObjectId createCommit(
      Repository repository,
      RevCommit basePatchsetCommit,
      ObjectId tree,
      String commitMessage,
      Instant timestamp)
      throws IOException {
    try (ObjectInserter objectInserter = repository.newObjectInserter()) {
      CommitBuilder builder = new CommitBuilder();
      builder.setTreeId(tree);
      builder.setParentIds(basePatchsetCommit.getParents());
      builder.setAuthor(basePatchsetCommit.getAuthorIdent());
      builder.setCommitter(getCommitterIdent(timestamp));
      builder.setMessage(commitMessage);
      ObjectId newCommitId = objectInserter.insert(builder);
      objectInserter.flush();
      return newCommitId;
    }
  }

  private PersonIdent getCommitterIdent(Instant commitTimestamp) {
    IdentifiedUser user = currentUser.get().asIdentifiedUser();
    return user.newCommitterIdent(commitTimestamp, zoneId);
  }

  /**
   * Strategy to apply depending on the current situation regarding change edits (e.g. creating a
   * new edit requires different storage modifications than updating an existing edit).
   */
  private interface EditBehavior {

    ModificationTarget getModificationTarget(
        ChangeNotes notes, ModificationIntention targetIntention)
        throws InvalidChangeOperationException;

    ObjectId mergeTreesIfNecessary(
        Repository repository, ObjectId newTreeId, ObjectId commitToModify)
        throws IOException, MergeConflictException;

    String getUnmodifiedCommitMessage(RevCommit commitToModify);

    String mergeCommitMessageIfNecessary(String newCommitMessage, RevCommit commitToModify)
        throws MergeConflictException;

    Optional<ChangeEdit> getEditIfNoModification(ObjectId newTreeId, String newCommitMessage);

    ChangeEdit updateEditInStorage(
        Repository repository,
        ChangeNotes notes,
        PatchSet basePatchSet,
        ObjectId newEditCommitId,
        Instant timestamp)
        throws IOException;
  }

  private static class ExistingEditBehavior implements EditBehavior {

    private final ChangeEdit changeEdit;
    private final NoteDbEdits noteDbEdits;

    ExistingEditBehavior(ChangeEdit changeEdit, NoteDbEdits noteDbEdits) {
      this.changeEdit = changeEdit;
      this.noteDbEdits = noteDbEdits;
    }

    @Override
    public ModificationTarget getModificationTarget(
        ChangeNotes notes, ModificationIntention targetIntention)
        throws InvalidChangeOperationException {
      ModificationTarget modificationTarget = targetIntention.getTargetWhenEditExists(changeEdit);
      // It would be better to do this validation in the implementation of the REST endpoints
      // before calling any write actions on ChangeEditModifier.
      modificationTarget.ensureTargetMayBeModifiedDespiteExistingEdit(changeEdit);
      return modificationTarget;
    }

    @Override
    public ObjectId mergeTreesIfNecessary(
        Repository repository, ObjectId newTreeId, ObjectId commitToModify)
        throws IOException, MergeConflictException {
      if (ObjectId.isEqual(changeEdit.getEditCommit(), commitToModify)) {
        return newTreeId;
      }
      return merge(repository, changeEdit, newTreeId);
    }

    @Override
    public String getUnmodifiedCommitMessage(RevCommit commitToModify) {
      return changeEdit.getEditCommit().getFullMessage();
    }

    @Override
    public String mergeCommitMessageIfNecessary(String newCommitMessage, RevCommit commitToModify)
        throws MergeConflictException {
      if (ObjectId.isEqual(changeEdit.getEditCommit(), commitToModify)) {
        return newCommitMessage;
      }
      String editCommitMessage = changeEdit.getEditCommit().getFullMessage();
      if (editCommitMessage.equals(newCommitMessage)) {
        return editCommitMessage;
      }
      return mergeCommitMessage(newCommitMessage, commitToModify, editCommitMessage);
    }

    private String mergeCommitMessage(
        String newCommitMessage, RevCommit commitToModify, String editCommitMessage)
        throws MergeConflictException {
      MergeAlgorithm mergeAlgorithm =
          new MergeAlgorithm(DiffAlgorithm.getAlgorithm(SupportedAlgorithm.MYERS));
      RawText baseMessage = new RawText(commitToModify.getFullMessage().getBytes(Charsets.UTF_8));
      RawText oldMessage = new RawText(editCommitMessage.getBytes(Charsets.UTF_8));
      RawText newMessage = new RawText(newCommitMessage.getBytes(Charsets.UTF_8));
      RawTextComparator textComparator = RawTextComparator.DEFAULT;
      MergeResult<RawText> mergeResult =
          mergeAlgorithm.merge(textComparator, baseMessage, oldMessage, newMessage);
      if (mergeResult.containsConflicts()) {
        throw new MergeConflictException(
            "The chosen modification adjusted the commit message. However, the new commit message"
                + " could not be merged with the commit message of the existing change edit."
                + " Please manually apply the desired changes to the commit message of the change"
                + " edit.");
      }

      StringBuilder resultingCommitMessage = new StringBuilder();
      for (MergeChunk mergeChunk : mergeResult) {
        RawText mergedMessagePart = mergeResult.getSequences().get(mergeChunk.getSequenceIndex());
        resultingCommitMessage.append(
            mergedMessagePart.getString(mergeChunk.getBegin(), mergeChunk.getEnd(), false));
      }
      return resultingCommitMessage.toString();
    }

    @Override
    public Optional<ChangeEdit> getEditIfNoModification(
        ObjectId newTreeId, String newCommitMessage) {
      if (!ObjectId.isEqual(newTreeId, changeEdit.getEditCommit().getTree())) {
        return Optional.empty();
      }
      if (!Objects.equals(newCommitMessage, changeEdit.getEditCommit().getFullMessage())) {
        return Optional.empty();
      }
      // Modifications are already contained in the change edit.
      return Optional.of(changeEdit);
    }

    @Override
    public ChangeEdit updateEditInStorage(
        Repository repository,
        ChangeNotes notes,
        PatchSet basePatchSet,
        ObjectId newEditCommitId,
        Instant timestamp)
        throws IOException {
      return noteDbEdits.updateEdit(
          notes.getProjectName(), repository, changeEdit, newEditCommitId, timestamp);
    }
  }

  private static class NewEditBehavior implements EditBehavior {

    private final NoteDbEdits noteDbEdits;

    NewEditBehavior(NoteDbEdits noteDbEdits) {
      this.noteDbEdits = noteDbEdits;
    }

    @Override
    public ModificationTarget getModificationTarget(
        ChangeNotes notes, ModificationIntention targetIntention)
        throws InvalidChangeOperationException {
      ModificationTarget modificationTarget = targetIntention.getTargetWhenNoEdit(notes);
      // It would be better to do this validation in the implementation of the REST endpoints
      // before calling any write actions on ChangeEditModifier.
      modificationTarget.ensureNewEditMayBeBasedOnTarget(notes.getChange());
      return modificationTarget;
    }

    @Override
    public ObjectId mergeTreesIfNecessary(
        Repository repository, ObjectId newTreeId, ObjectId commitToModify) {
      return newTreeId;
    }

    @Override
    public String getUnmodifiedCommitMessage(RevCommit commitToModify) {
      return commitToModify.getFullMessage();
    }

    @Override
    public String mergeCommitMessageIfNecessary(String newCommitMessage, RevCommit commitToModify) {
      return newCommitMessage;
    }

    @Override
    public Optional<ChangeEdit> getEditIfNoModification(
        ObjectId newTreeId, String newCommitMessage) {
      return Optional.empty();
    }

    @Override
    public ChangeEdit updateEditInStorage(
        Repository repository,
        ChangeNotes notes,
        PatchSet basePatchSet,
        ObjectId newEditCommitId,
        Instant timestamp)
        throws IOException {
      return noteDbEdits.createEdit(repository, notes, basePatchSet, newEditCommitId, timestamp);
    }
  }

  private static class NoteDbEdits {
    private final ZoneId zoneId;
    private final ChangeIndexer indexer;
    private final Provider<CurrentUser> currentUser;
    private final GitReferenceUpdated gitReferenceUpdated;

    NoteDbEdits(
        GitReferenceUpdated gitReferenceUpdated,
        ZoneId zoneId,
        ChangeIndexer indexer,
        Provider<CurrentUser> currentUser) {
      this.zoneId = zoneId;
      this.indexer = indexer;
      this.currentUser = currentUser;
      this.gitReferenceUpdated = gitReferenceUpdated;
    }

    ChangeEdit createEdit(
        Repository repository,
        ChangeNotes notes,
        PatchSet basePatchset,
        ObjectId newEditCommitId,
        Instant timestamp)
        throws IOException {
      Change change = notes.getChange();
      String editRefName = getEditRefName(change, basePatchset);
      updateReference(
          notes.getProjectName(),
          repository,
          editRefName,
          ObjectId.zeroId(),
          newEditCommitId,
          timestamp);
      reindex(notes);

      RevCommit newEditCommit = lookupCommit(repository, newEditCommitId);
      return new ChangeEdit(change, editRefName, newEditCommit, basePatchset);
    }

    private String getEditRefName(Change change, PatchSet basePatchset) {
      IdentifiedUser me = currentUser.get().asIdentifiedUser();
      return RefNames.refsEdit(me.getAccountId(), change.getId(), basePatchset.id());
    }

    private AccountState getUpdater() {
      return currentUser.get().asIdentifiedUser().state();
    }

    ChangeEdit updateEdit(
        Project.NameKey projectName,
        Repository repository,
        ChangeEdit changeEdit,
        ObjectId newEditCommitId,
        Instant timestamp)
        throws IOException {
      String editRefName = changeEdit.getRefName();
      RevCommit currentEditCommit = changeEdit.getEditCommit();
      updateReference(
          projectName, repository, editRefName, currentEditCommit, newEditCommitId, timestamp);
      reindex(changeEdit.getChange());

      RevCommit newEditCommit = lookupCommit(repository, newEditCommitId);
      return new ChangeEdit(
          changeEdit.getChange(), editRefName, newEditCommit, changeEdit.getBasePatchSet());
    }

    private void updateReference(
        Project.NameKey projectName,
        Repository repository,
        String refName,
        ObjectId currentObjectId,
        ObjectId targetObjectId,
        Instant timestamp)
        throws IOException {
      try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
        RefUpdate ru = repository.updateRef(refName);
        ru.setExpectedOldObjectId(currentObjectId);
        ru.setNewObjectId(targetObjectId);
        ru.setRefLogIdent(getRefLogIdent(timestamp));
        ru.setRefLogMessage("inline edit (amend)", false);
        ru.setForceUpdate(true);
        try (RevWalk revWalk = new RevWalk(repository)) {
          RefUpdate.Result res = ru.update(revWalk);
          String message = "cannot update " + ru.getName() + " in " + projectName + ": " + res;
          if (res == RefUpdate.Result.LOCK_FAILURE) {
            throw new LockFailureException(message, ru);
          }
          if (res != RefUpdate.Result.NEW && res != RefUpdate.Result.FORCED) {
            throw new IOException(message);
          }
        }
        gitReferenceUpdated.fire(projectName, ru, getUpdater());
      }
    }

    void baseEditOnDifferentPatchset(
        Project.NameKey project,
        Repository repository,
        ChangeEdit changeEdit,
        PatchSet currentPatchSet,
        ObjectId currentEditCommit,
        ObjectId newEditCommitId,
        Instant nowTimestamp)
        throws IOException {
      String newEditRefName = getEditRefName(changeEdit.getChange(), currentPatchSet);
      updateReferenceWithNameChange(
          project,
          repository,
          changeEdit.getRefName(),
          currentEditCommit,
          newEditRefName,
          newEditCommitId,
          nowTimestamp);
      reindex(changeEdit.getChange());
    }

    private void updateReferenceWithNameChange(
        Project.NameKey projectName,
        Repository repository,
        String currentRefName,
        ObjectId currentObjectId,
        String newRefName,
        ObjectId targetObjectId,
        Instant timestamp)
        throws IOException {
      try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
        BatchRefUpdate batchRefUpdate = repository.getRefDatabase().newBatchUpdate();
        batchRefUpdate.addCommand(
            new ReceiveCommand(ObjectId.zeroId(), targetObjectId, newRefName));
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
        gitReferenceUpdated.fire(projectName, batchRefUpdate, getUpdater());
      }
    }

    static RevCommit lookupCommit(Repository repository, ObjectId commitId) throws IOException {
      try (RevWalk revWalk = new RevWalk(repository)) {
        return revWalk.parseCommit(commitId);
      }
    }

    private PersonIdent getRefLogIdent(Instant timestamp) {
      IdentifiedUser user = currentUser.get().asIdentifiedUser();
      return user.newRefLogIdent(timestamp, zoneId);
    }

    private void reindex(Change change) {
      indexer.index(change.getProject(), change.getId());
    }

    private void reindex(ChangeNotes notes) {
      indexer.index(notes);
    }
  }
}
