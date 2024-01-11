// Copyright (C) 2024 The Android Open Source Project
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

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;

/**
 * Class to get modified files in {@link
 * com.google.gerrit.server.git.validators.CommitValidationListener}s.
 *
 * <p>Computing the modified files for a merge commit may require the creation of the auto-merge
 * commit (usually the auto-merge commit is not created yet when the commit validators are invoked).
 * However commit validators should not write any commits (as the name {@code
 * CommitValidationListener} suggests they are only intended to validate and listen). In particular
 * commit validators must not write the auto-merge commit with a new {@link ObjectInserter} instance
 * that competes with the main {@link ObjectInserter} instance that is being used to create changes,
 * patch sets and auto-merge commits. This class wraps the computation of modified files and takes
 * care of creating any missing auto-merge commit with the main {@link ObjectInserter} instance, so
 * that the auto-merge commit is only created by this {@link ObjectInserter} instance and there is
 * no competing {@link ObjectInserter} instance that creates the same auto-merge commit. Creating
 * the same auto-merge commit with competing {@link ObjectInserter} instances must be avoided as it
 * can result issues during object quorum.
 */
public class DiffOperationsForCommitValidation {
  public interface Factory {
    DiffOperationsForCommitValidation create(RepoView repoView, ObjectInserter inserter);
  }

  private final DiffOperations diffOperations;
  private final RepoView repoView;
  private final ObjectInserter inserter;

  @Inject
  DiffOperationsForCommitValidation(
      DiffOperations diffOperations,
      @Assisted RepoView repoView,
      @Assisted ObjectInserter inserter) {
    this.diffOperations = diffOperations;
    this.repoView = repoView;
    this.inserter = inserter;
  }

  /**
   * Retrieves the modified files from the {@link
   * com.google.gerrit.server.patch.diff.ModifiedFilesCache} if they are already cached. If not, the
   * modified files are loaded directly (using the main {@link org.eclipse.jgit.revwalk.RevWalk}
   * instance that can see newly inserted objects) rather than loading them via the {@link
   * com.google.gerrit.server.patch.diff.ModifiedFilesCache} (that would open a new {@link
   * org.eclipse.jgit.revwalk.RevWalk} instance).
   *
   * <p>If the loading requires the creation of the auto-merge commit it is created with the main
   * {@link ObjectInserter} instance (also see the class javadoc).
   *
   * <p>The results will be stored in the {@link
   * com.google.gerrit.server.patch.diff.ModifiedFilesCache} so that calling this method multiple
   * times loads the modified files only once (for the first call, for further calls the cached
   * modified files are returned).
   */
  public Map<String, ModifiedFile> loadModifiedFilesAgainstParentIfNecessary(
      Project.NameKey project, ObjectId newCommit, int parentNum, boolean enableRenameDetection)
      throws DiffNotAvailableException {
    return diffOperations.loadModifiedFilesAgainstParentIfNecessary(
        project, newCommit, parentNum, repoView, inserter, enableRenameDetection);
  }
}
