// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.query.approval;

import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/** Predicate that matches when the new patch-set includes the same files as the old patch-set. */
@Singleton
public class ListOfFilesUnchangedPredicate extends ApprovalPredicate {
  private final DiffOperations diffOperations;
  private final GitRepositoryManager repositoryManager;

  @Inject
  public ListOfFilesUnchangedPredicate(
      DiffOperations diffOperations, GitRepositoryManager repositoryManager) {
    this.diffOperations = diffOperations;
    this.repositoryManager = repositoryManager;
  }

  @Override
  public boolean match(ApprovalContext ctx) {
    PatchSet targetPatchSet = ctx.target();
    PatchSet sourcePatchSet =
        ctx.changeNotes().getPatchSets().get(ctx.patchSetApprovalKey().patchSetId());

    Integer parentNum =
        isInitialCommit(ctx.changeNotes().getProjectName(), targetPatchSet.commitId()) ? 0 : 1;
    try {
      Map<String, ModifiedFile> baseVsCurrent =
          diffOperations.loadModifiedFilesAgainstParent(
              ctx.changeNotes().getProjectName(),
              targetPatchSet.commitId(),
              parentNum,
              DiffOptions.DEFAULTS,
              ctx.revWalk(),
              ctx.repoConfig());
      Map<String, ModifiedFile> baseVsPrior =
          diffOperations.loadModifiedFilesAgainstParent(
              ctx.changeNotes().getProjectName(),
              sourcePatchSet.commitId(),
              parentNum,
              DiffOptions.DEFAULTS,
              ctx.revWalk(),
              ctx.repoConfig());
      Map<String, ModifiedFile> priorVsCurrent =
          diffOperations.loadModifiedFiles(
              ctx.changeNotes().getProjectName(),
              sourcePatchSet.commitId(),
              targetPatchSet.commitId(),
              DiffOptions.DEFAULTS,
              ctx.revWalk(),
              ctx.repoConfig());
      return match(baseVsCurrent, baseVsPrior, priorVsCurrent);
    } catch (DiffNotAvailableException ex) {
      throw new StorageException(
          "failed to compute difference in files, so won't copy"
              + " votes on labels even if list of files is the same and "
              + "copyAllIfListOfFilesDidNotChange",
          ex);
    }
  }

  /**
   * returns {@code true} if the files that were modified are the same in both inputs, and the
   * {@link ChangeType} matches for each modified file.
   */
  public boolean match(
      Map<String, ModifiedFile> baseVsCurrent,
      Map<String, ModifiedFile> baseVsPrior,
      Map<String, ModifiedFile> priorVsCurrent) {
    Set<String> allFiles = new HashSet<>();
    allFiles.addAll(baseVsCurrent.keySet());
    allFiles.addAll(baseVsPrior.keySet());
    for (String file : allFiles) {
      if (Patch.isMagic(file)) {
        continue;
      }
      ModifiedFile modifiedFile1 = baseVsCurrent.get(file);
      ModifiedFile modifiedFile2 = baseVsPrior.get(file);
      if (!priorVsCurrent.containsKey(file)) {
        // If the file is not modified between prior and current patchsets, then scan safely skip
        // it. The file might have been modified due to rebase.
        continue;
      }
      if (modifiedFile1 == null || modifiedFile2 == null) {
        return false;
      }
      if (!modifiedFile2.changeType().equals(modifiedFile1.changeType())) {
        return false;
      }
    }
    return true;
  }

  public boolean isInitialCommit(Project.NameKey project, ObjectId objectId) {
    try (Repository repo = repositoryManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      return revWalk.parseCommit(objectId).getParentCount() == 0;
    } catch (IOException ex) {
      throw new StorageException(ex);
    }
  }

  @Override
  public Predicate<ApprovalContext> copy(
      Collection<? extends Predicate<ApprovalContext>> children) {
    return new ListOfFilesUnchangedPredicate(diffOperations, repositoryManager);
  }

  @Override
  public int hashCode() {
    return Objects.hash(diffOperations, repositoryManager);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ListOfFilesUnchangedPredicate)) {
      return false;
    }
    ListOfFilesUnchangedPredicate o = (ListOfFilesUnchangedPredicate) other;
    return Objects.equals(o.diffOperations, diffOperations)
        && Objects.equals(o.repositoryManager, repositoryManager);
  }
}
