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
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.logging.Metadata;
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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
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

  /** These metrics are temporary for launching the new redesigned diff cache. */
  @Singleton
  static class Metrics {
    final Counter1<String> diffs;
    static final String MATCH = "match";
    static final String MISMATCH = "mismatch";
    static final String ERROR = "error";

    @Inject
    Metrics(MetricMaker metricMaker) {
      diffs =
          metricMaker.newCounter(
              "diff/get_diff/dark_launch",
              new Description(
                      "Total number of matching, non-matching, or error in diffs in the old and new diff cache implementations.")
                  .setRate()
                  .setUnit("count"),
              Field.ofString("type", Metadata.Builder::eventType).build());
    }
  }

  private final GitRepositoryManager repoManager;
  private final PatchSetUtil psUtil;
  private final Provider<PatchScriptBuilder> builderFactory;
  private final PatchListCache patchListCache;
  private final Metrics metrics;
  private final ExecutorService executor;

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

  private final boolean runNewDiffCache;

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
      Metrics metrics,
      @DiffExecutor ExecutorService executor,
      @GerritServerConfig Config cfg,
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
    this.metrics = metrics;
    this.executor = executor;

    this.fileName = fileName;
    this.psa = patchSetA;
    this.parentNum = -1;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;
    this.currentUser = currentUser;

    this.runNewDiffCache = cfg.getBoolean("cache", "diff_cache", "runNewDiffCache_GetDiff", false);

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
      Metrics metrics,
      @DiffExecutor ExecutorService executor,
      @GerritServerConfig Config cfg,
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
    this.metrics = metrics;
    this.executor = executor;

    this.fileName = fileName;
    this.psa = null;
    this.parentNum = parentNum;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;
    this.currentUser = currentUser;

    this.runNewDiffCache = cfg.getBoolean("cache", "diff_cache", "runNewDiffCache_GetDiff", false);

    changeId = patchSetB.changeId();
    checkArgument(parentNum >= 0, "parentNum must be >= 0");
  }

  @Override
  public PatchScript call()
      throws LargeObjectException, AuthException, InvalidChangeOperationException, IOException,
          PermissionBackendException {

    try {
      permissionBackend.user(currentUser).change(notes).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new NoSuchChangeException(changeId, e);
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
        if (runNewDiffCache) {
          PatchScript patchScript = getPatchScriptWithNewDiffCache(git, aId, bId);
          // TODO(ghareeb): remove the async run. This is temporarily used to keep sanity checking
          // the results while rolling out the new diff cache.
          runOldDiffCacheAsyncAndExportMetrics(git, aId, bId, patchScript);
          return patchScript;
        } else {
          return getPatchScriptWithOldDiffCache(git, aId, bId);
        }
      } catch (PatchListNotAvailableException e) {
        throw new NoSuchChangeException(changeId, e);
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

  private void runOldDiffCacheAsyncAndExportMetrics(
      Repository git, ObjectId aId, ObjectId bId, PatchScript expected) {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        executor.submit(
            () -> {
              try {
                PatchScript patchScript = getPatchScriptWithOldDiffCache(git, aId, bId);
                if (areEqualPatchscripts(patchScript, expected)) {
                  metrics.diffs.increment(Metrics.MATCH);
                } else {
                  metrics.diffs.increment(Metrics.MISMATCH);
                  logger.atWarning().atMostEvery(10, TimeUnit.SECONDS).log(
                      "Mismatching diff for change %s, old commit ID: %s, new commit ID: %s, file name: %s.",
                      changeId.toString(), aId, bId, fileName);
                }
              } catch (PatchListNotAvailableException | IOException e) {
                metrics.diffs.increment(Metrics.ERROR);
                logger.atSevere().atMostEvery(10, TimeUnit.SECONDS).log(
                    String.format(
                            "Error computing new diff for change %s, old commit ID: %s, new commit ID: %s.\n",
                            changeId.toString(), aId, bId)
                        + ExceptionUtils.getStackTrace(e));
              }
            });
  }

  private PatchScript getPatchScriptWithOldDiffCache(Repository git, ObjectId aId, ObjectId bId)
      throws IOException, PatchListNotAvailableException {
    PatchScriptBuilder patchScriptBuilder = newBuilder();
    PatchList list = listFor(keyFor(aId, bId, diffPrefs.ignoreWhitespace));
    PatchListEntry content = list.get(fileName);
    return patchScriptBuilder.toPatchScriptOld(git, list, content);
  }

  private PatchScript getPatchScriptWithNewDiffCache(Repository git, ObjectId aId, ObjectId bId)
      throws IOException, DiffNotAvailableException {
    FileDiffOutput fileDiffOutput =
        aId == null
            ? diffOperations.getModifiedFileAgainstParent(
                notes.getProjectName(),
                bId,
                parentNum == -1 ? null : parentNum + 1,
                fileName,
                diffPrefs.ignoreWhitespace)
            : diffOperations.getModifiedFile(
                notes.getProjectName(), aId, bId, fileName, diffPrefs.ignoreWhitespace);
    return newBuilder().toPatchScriptNew(git, fileDiffOutput);
  }

  /**
   * The comparison is not exhaustive but is using the most important fields. Comparing all fields
   * will require some work in {@link PatchScript} to, e.g., convert it to autovalue. This
   * comparison method shall give a strong signal that both patchscripts are almost identical.
   */
  private static boolean areEqualPatchscripts(PatchScript ps1, PatchScript ps2) {
    boolean equal = true;
    if (!ps1.getChangeType().equals(ps2.getChangeType())) {
      equal = false;
      logger.atWarning().log(
          "Mismatching change type: old = %s, new = %s.", ps1.getChangeType(), ps2.getChangeType());
    }
    if (!ps1.getPatchHeader().equals(ps2.getPatchHeader())) {
      equal = false;
      logger.atWarning().log(
          "Mismatching patch header: old = %s, new = %s.",
          ps1.getPatchHeader(), ps2.getPatchHeader());
    }
    if (!Objects.equals(ps1.getOldName(), ps2.getOldName())) {
      equal = false;
      logger.atWarning().log(
          "Mismatching old name: old = %s, new = %s.", ps1.getOldName(), ps2.getOldName());
    }
    if (!Objects.equals(ps1.getNewName(), ps2.getNewName())) {
      equal = false;
      logger.atWarning().log(
          "Mismatching new name: old = %s, new = %s.", ps1.getNewName(), ps2.getNewName());
    }
    if (!ps1.getEdits().containsAll(ps2.getEdits())) {
      equal = false;
      logger.atWarning().log(
          "Mismatching edits: old = %s, new = %s.", ps1.getEdits(), ps2.getEdits());
    }
    if (!ps2.getEdits().containsAll(ps1.getEdits())) {
      equal = false;
      logger.atWarning().log(
          "Mismatching edits: old = %s, new = %s.", ps1.getEdits(), ps2.getEdits());
    }
    if (!ps1.getEditsDueToRebase().equals(ps2.getEditsDueToRebase())) {
      equal = false;
      logger.atWarning().log(
          "Mismatching edits due to rebase: old = %s, new = %s.",
          ps1.getEditsDueToRebase(), ps2.getEditsDueToRebase());
    }
    if (!ps1.getA().equals(ps2.getA())) {
      equal = false;
      logger.atWarning().log("Mismatching sparse file content in old commit.");
    }
    if (!ps1.getB().equals(ps2.getB())) {
      equal = false;
      logger.atWarning().log("Mismatching sparse file content in new commit.");
    }
    return equal;
  }

  private Optional<ObjectId> getAId() {
    if (psa == null) {
      return Optional.empty();
    }
    checkState(parentNum < 0, "expected no parentNum when psa is present");
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

  private PatchListKey keyFor(ObjectId aId, ObjectId bId, Whitespace whitespace) {
    if (parentNum < 0) {
      return PatchListKey.againstCommit(aId, bId, whitespace);
    }
    return PatchListKey.againstParentNum(parentNum + 1, bId, whitespace);
  }

  private PatchList listFor(PatchListKey key) throws PatchListNotAvailableException {
    return patchListCache.get(key, notes.getProjectName());
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
