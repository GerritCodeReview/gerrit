//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.patch;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;

/**
 * An interface for all file diff related operations. Clients should use this interface to request:
 *
 * <ul>
 *   <li>The list of modified files between two commits.
 *   <li>The list of modified files between a commit and its parent or the auto-merge.
 *   <li>The detailed file diff for a single file path.
 *   <li>The Intra-line diffs for a single file path (TODO:ghareeb).
 * </ul>
 */
public interface DiffOperations {

  /**
   * Returns the list of added, deleted or modified files between a commit against its base. The
   * {@link Patch#COMMIT_MSG} and {@link Patch#MERGE_LIST} (for merge commits) are also returned.
   *
   * <p>If parentNum is set, it is used as the old commit in the diff. Otherwise, if the {@code
   * newCommit} has only one parent, it is used as base. If {@code newCommit} has two parents, the
   * auto-merge commit is computed and used as base. The auto-merge for more than two parents is not
   * supported.
   *
   * @param project a project name representing a git repository.
   * @param newCommit 20 bytes SHA-1 of the new commit used in the diff.
   * @param parentNum 1-based integer specifying which parent to use as base. If zero, the only
   *     parent will be used or the auto-merge if {@code newCommit} is a merge commit.
   * @return map of file paths to the file diffs. The map key is the new file path for all {@link
   *     ChangeType} file diffs except {@link ChangeType#DELETED} entries where the map key contains
   *     the old file path. The map entries are not sorted by key.
   * @throws DiffNotAvailableException if auto-merge is requested for a commit having more than two
   *     parents, if the {@code newCommit} could not be parsed for extracting the base commit, or if
   *     an internal error occurred in Git while evaluating the diff.
   */
  Map<String, FileDiffOutput> listModifiedFilesAgainstParent(
      Project.NameKey project, ObjectId newCommit, int parentNum, DiffOptions diffOptions)
      throws DiffNotAvailableException;

  /**
   * Returns the list of added, deleted or modified files between two commits (patchsets). The
   * commit message and merge list (for merge commits) are also returned.
   *
   * @param project a project name representing a git repository.
   * @param oldCommit 20 bytes SHA-1 of the old commit used in the diff.
   * @param newCommit 20 bytes SHA-1 of the new commit used in the diff.
   * @return map of file paths to the file diffs. The map key is the new file path for all {@link
   *     ChangeType} file diffs except {@link ChangeType#DELETED} entries where the map key contains
   *     the old file path. The map entries are not sorted by key.
   * @throws DiffNotAvailableException if an internal error occurred in Git while evaluating the
   *     diff.
   */
  Map<String, FileDiffOutput> listModifiedFiles(
      Project.NameKey project, ObjectId oldCommit, ObjectId newCommit, DiffOptions diffOptions)
      throws DiffNotAvailableException;

  /**
   * Returns the diff for a single file between a patchset commit against its parent or the
   * auto-merge commit. For deleted files, the {@code fileName} parameter should contain the old
   * name of the file. This method will return {@link FileDiffOutput#empty(String, ObjectId,
   * ObjectId)} if the requested file identified by {@code fileName} has unchanged content or does
   * not exist at both commits.
   *
   * @param project a project name representing a git repository.
   * @param newCommit 20 bytes SHA-1 of the new commit used in the diff.
   * @param parentNum 1-based integer specifying which parent to use as base. If zero, the only
   *     parent will be used or the auto-merge if {@code newCommit} is a merge commit.
   * @param fileName the file name for which the diff should be evaluated.
   * @param whitespace preference controlling whitespace effect in diff computation.
   * @return the diff for the single file between the two commits.
   * @throws DiffNotAvailableException if an internal error occurred in Git while evaluating the
   *     diff, or if an exception happened while parsing the base commit.
   */
  FileDiffOutput getModifiedFileAgainstParent(
      Project.NameKey project,
      ObjectId newCommit,
      int parentNum,
      String fileName,
      @Nullable DiffPreferencesInfo.Whitespace whitespace)
      throws DiffNotAvailableException;

  /**
   * Returns the diff for a single file between two patchset commits. For deleted files, the {@code
   * fileName} parameter should contain the old name of the file. This method will return {@link
   * FileDiffOutput#empty(String, ObjectId, ObjectId)} if the requested file identified by {@code
   * fileName} has unchanged content or does not exist at both commits.
   *
   * @param project a project name representing a git repository.
   * @param oldCommit 20 bytes SHA-1 of the old commit used in the diff.
   * @param newCommit 20 bytes SHA-1 of the new commit used in the diff.
   * @param fileName the file name for which the diff should be evaluated.
   * @param whitespace preference controlling whitespace effect in diff computation.
   * @return the diff for the single file between the two commits.
   * @throws DiffNotAvailableException if an internal error occurred in Git while evaluating the
   *     diff.
   */
  FileDiffOutput getModifiedFile(
      Project.NameKey project,
      ObjectId oldCommit,
      ObjectId newCommit,
      String fileName,
      @Nullable DiffPreferencesInfo.Whitespace whitespace)
      throws DiffNotAvailableException;
}
