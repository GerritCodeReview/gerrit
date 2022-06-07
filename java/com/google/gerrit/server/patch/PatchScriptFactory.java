// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchScriptBuilder.IntraLineDiffCalculatorResult;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

public class PatchScriptFactory implements Callable<PatchScript> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {

    PatchScriptFactory create(
        ChangeNotes notes,
        String fileName,
        @Assisted("patchSetA") PatchSet.Id patchSetA,
        @Assisted("patchSetB") PatchSet.Id patchSetB,
        DiffPreferencesInfo diffPrefs,
        CurrentUser currentUser);

    PatchScriptFactory create(
        ChangeNotes notes,
        String fileName,
        int parentNum,
        PatchSet.Id patchSetB,
        DiffPreferencesInfo diffPrefs,
        CurrentUser currentUser);
  }

  private final GitRepositoryManager repoManager;
  private final PatchSetUtil psUtil;
  private final Provider<PatchScriptBuilder> builderFactory;
  private final PatchListCache patchListCache;

  private final String fileName;
  @Nullable private final PatchSet.Id psa;
  private final int parentNum;
  private final PatchSet.Id psb;
  private final DiffPreferencesInfo diffPrefs;
  private final CurrentUser currentUser;

  private final ChangeEditUtil editReader;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final DiffOperations diffOperations;

  private final Change.Id changeId;

  private ChangeNotes notes;

  @AssistedInject
  PatchScriptFactory(
      GitRepositoryManager grm,
      PatchSetUtil psUtil,
      Provider<PatchScriptBuilder> builderFactory,
      PatchListCache patchListCache,
      ChangeEditUtil editReader,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      DiffOperations diffOperations,
      @Assisted ChangeNotes notes,
      @Assisted String fileName,
      @Assisted("patchSetA") @Nullable PatchSet.Id patchSetA,
      @Assisted("patchSetB") PatchSet.Id patchSetB,
      @Assisted DiffPreferencesInfo diffPrefs,
      @Assisted CurrentUser currentUser) {
    this.repoManager = grm;
    this.psUtil = psUtil;
    this.builderFactory = builderFactory;
    this.patchListCache = patchListCache;
    this.notes = notes;
    this.editReader = editReader;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.diffOperations = diffOperations;

    this.fileName = fileName;
    this.psa = patchSetA;
    this.parentNum = 0;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;
    this.currentUser = currentUser;
    changeId = patchSetB.changeId();
  }

  @AssistedInject
  PatchScriptFactory(
      GitRepositoryManager grm,
      PatchSetUtil psUtil,
      Provider<PatchScriptBuilder> builderFactory,
      PatchListCache patchListCache,
      ChangeEditUtil editReader,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      DiffOperations diffOperations,
      @Assisted ChangeNotes notes,
      @Assisted String fileName,
      @Assisted int parentNum,
      @Assisted PatchSet.Id patchSetB,
      @Assisted DiffPreferencesInfo diffPrefs,
      @Assisted CurrentUser currentUser) {
    this.repoManager = grm;
    this.psUtil = psUtil;
    this.builderFactory = builderFactory;
    this.patchListCache = patchListCache;
    this.notes = notes;
    this.editReader = editReader;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.diffOperations = diffOperations;

    this.fileName = fileName;
    this.psa = null;
    this.parentNum = parentNum;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;
    this.currentUser = currentUser;
    changeId = patchSetB.changeId();
    checkArgument(parentNum > 0, "parentNum must be > 0");
  }

  @Override
  public PatchScript call()
      throws LargeObjectException, AuthException, InvalidChangeOperationException, IOException,
          PermissionBackendException {

    if (!permissionBackend.user(currentUser).change(notes).test(ChangePermission.READ)) {
      throw new NoSuchChangeException(changeId);
    }

    if (!projectCache
        .get(notes.getProjectName())
        .map(ProjectState::statePermitsRead)
        .orElse(false)) {
      throw new NoSuchChangeException(changeId);
    }

    try (Repository git = repoManager.openRepository(notes.getProjectName())) {
      try {
        validatePatchSetId(psa);
        validatePatchSetId(psb);

        ObjectId aId = getAId().orElse(null);
        ObjectId bId = getBId().orElse(null);
        if (bId == null) {
          // Change edit: create synthetic PatchSet corresponding to the edit.
          Optional<ChangeEdit> edit = editReader.byChange(notes);
          if (!edit.isPresent()) {
            throw new NoSuchChangeException(notes.getChangeId());
          }
          bId = edit.get().getEditCommit();
        }
        return getPatchScript(git, aId, bId);
      } catch (DiffNotAvailableException e) {
        throw new StorageException(e);
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("File content unavailable");
        throw new NoSuchChangeException(changeId, e);
      } catch (org.eclipse.jgit.errors.LargeObjectException err) {
        throw new LargeObjectException("File content is too large", err);
      }
    } catch (RepositoryNotFoundException e) {
      logger.atSevere().withCause(e).log("Repository %s not found", notes.getProjectName());
      throw new NoSuchChangeException(changeId, e);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot open repository %s", notes.getProjectName());
      throw new NoSuchChangeException(changeId, e);
    }
  }

  private PatchScript getPatchScript(Repository git, ObjectId aId, ObjectId bId)
      throws IOException, DiffNotAvailableException {
    FileDiffOutput fileDiffOutput =
        aId == null
            ? diffOperations.getModifiedFileAgainstParent(
                notes.getProjectName(), bId, parentNum, fileName, diffPrefs.ignoreWhitespace)
            : diffOperations.getModifiedFile(
                notes.getProjectName(), aId, bId, fileName, diffPrefs.ignoreWhitespace);
    return newBuilder().toPatchScript(git, fileDiffOutput);
  }

  private Optional<ObjectId> getAId() {
    if (psa == null) {
      return Optional.empty();
    }
    checkState(parentNum == 0, "expected no parentNum when psa is present");
    checkArgument(psa.get() != 0, "edit not supported for left side");
    return Optional.of(getCommitId(psa));
  }

  private Optional<ObjectId> getBId() {
    if (psb.get() == 0) {
      // Change edit
      return Optional.empty();
    }
    return Optional.of(getCommitId(psb));
  }

  private PatchScriptBuilder newBuilder() {
    final PatchScriptBuilder b = builderFactory.get();
    b.setDiffPrefs(diffPrefs);
    if (diffPrefs.intralineDifference) {
      b.setIntraLineDiffCalculator(
          new IntraLineDiffCalculator(patchListCache, notes.getProjectName(), diffPrefs));
    }
    return b;
  }

  private ObjectId getCommitId(PatchSet.Id psId) {
    PatchSet ps = psUtil.get(notes, psId);
    if (ps == null) {
      throw new NoSuchChangeException(psId.changeId());
    }
    return ps.commitId();
  }

  private void validatePatchSetId(PatchSet.Id psId) throws NoSuchChangeException {
    if (psId == null) { // OK, means use base;
    } else if (changeId.equals(psId.changeId())) { // OK, same change;
    } else {
      throw new NoSuchChangeException(changeId);
    }
  }

  private static class IntraLineDiffCalculator
      implements PatchScriptBuilder.IntraLineDiffCalculator {

    private final PatchListCache patchListCache;
    private final Project.NameKey projectKey;
    private final DiffPreferencesInfo diffPrefs;

    IntraLineDiffCalculator(
        PatchListCache patchListCache, Project.NameKey projectKey, DiffPreferencesInfo diffPrefs) {
      this.patchListCache = patchListCache;
      this.projectKey = projectKey;
      this.diffPrefs = diffPrefs;
    }

    @Override
    public IntraLineDiffCalculatorResult calculateIntraLineDiff(
        ImmutableList<Edit> edits,
        Set<Edit> editsDueToRebase,
        ObjectId aId,
        ObjectId bId,
        Text aSrc,
        Text bSrc,
        ObjectId bTreeId,
        String bPath) {
      IntraLineDiff d =
          patchListCache.getIntraLineDiff(
              IntraLineDiffKey.create(aId, bId, diffPrefs.ignoreWhitespace),
              IntraLineDiffArgs.create(
                  aSrc, bSrc, edits, editsDueToRebase, projectKey, bTreeId, bPath));
      if (d == null) {
        return IntraLineDiffCalculatorResult.FAILURE;
      }
      switch (d.getStatus()) {
        case EDIT_LIST:
          return IntraLineDiffCalculatorResult.success(d.getEdits());

        case ERROR:
          return IntraLineDiffCalculatorResult.FAILURE;

        case TIMEOUT:
          return IntraLineDiffCalculatorResult.TIMEOUT;

        case DISABLED:
        default:
          return IntraLineDiffCalculatorResult.NO_RESULT;
      }
    }
  }
}
