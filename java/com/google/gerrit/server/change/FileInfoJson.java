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

package com.google.gerrit.server.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;

/** Compute and return the list of modified files between two commits. */
public interface FileInfoJson {

  /**
   * Computes the list of modified files for a given change and patchset against the parent commit.
   *
   * @param change a Gerrit change.
   * @param patchSet a single revision of the change.
   * @return a mapping of the file paths to their related diff information.
   */
  default Map<String, FileInfo> getFileInfoMap(Change change, PatchSet patchSet)
      throws ResourceConflictException, PatchListNotAvailableException {
    return getFileInfoMap(change, patchSet.commitId(), null);
  }

  /**
   * Computes the list of modified files for a given change and patchset against the specified
   * parent. For merge commits, callers can use 0, 1, 2, etc... to choose a specific parent. The
   * first parent is 0. A value of -1 for parent can be passed to use the default base commit, which
   * is the only parent for commits having only one parent, or the auto-merge otherwise.
   *
   * @param change a Gerrit change.
   * @param patchSet a single revision of the change.
   * @param parentNum 1-based integer identifying the parent number used for comparison. If zero,
   *     the only parent will be used or the auto-merge if {@code newCommit} is a merge commit.
   * @return a mapping of the file paths to their related diff information.
   */
  default Map<String, FileInfo> getFileInfoMap(Change change, PatchSet patchSet, int parentNum)
      throws ResourceConflictException, PatchListNotAvailableException {
    return getFileInfoMap(change, patchSet.commitId(), parentNum);
  }

  /**
   * Computes the list of modified files for a given change and patchset against its parent. For
   * merge commits, callers can use 0, 1, 2, etc... to choose a specific parent. The first parent is
   * 0.
   *
   * @param change a Gerrit change.
   * @param objectId a commit SHA-1 identifying a patchset commit.
   * @param parentNum 1-based integer identifying the parent number used for comparison. If zero,
   *     the only parent will be used or the auto-merge if {@code newCommit} is a merge commit.
   * @return a mapping of the file paths to their related diff information.
   */
  default Map<String, FileInfo> getFileInfoMap(Change change, ObjectId objectId, int parentNum)
      throws ResourceConflictException, PatchListNotAvailableException {
    return getFileInfoMap(change.getProject(), objectId, parentNum);
  }

  /**
   * Computes the list of modified files for a given change and patchset identified by its {@code
   * objectId} against a specified base patchset.
   *
   * @param change a Gerrit change.
   * @param objectId a commit SHA-1 identifying a patchset commit.
   * @param base a base patchset to compare the commit identified by {@code objectId} against.
   * @return a mapping of the file paths to their related diff information.
   */
  Map<String, FileInfo> getFileInfoMap(Change change, ObjectId objectId, @Nullable PatchSet base)
      throws ResourceConflictException, PatchListNotAvailableException;

  /**
   * Computes the list of modified files for a given project and commit against its parent. For
   * merge commits, callers can use 0, 1, 2, etc... to choose a specific parent. The first parent is
   * 0. A value of -1 for parent can be passed to use the default base commit, which is the only
   * parent for commits having only one parent, or the auto-merge otherwise.
   *
   * @param project a project identifying a repository.
   * @param objectId a commit SHA-1 identifying a patchset commit.
   * @param parentNum 1-based integer identifying the parent number used for comparison. If zero,
   *     the only parent will be used or the auto-merge if {@code newCommit} is a merge commit.
   * @return a mapping of the file paths to their related diff information.
   */
  Map<String, FileInfo> getFileInfoMap(Project.NameKey project, ObjectId objectId, int parentNum)
      throws ResourceConflictException, PatchListNotAvailableException;
}
