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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.FileInfo;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** An interface for all file diff related operations. */
public interface Diffs {

  /**
   * Returns the list of added, deleted or modified files between 2 commits (patchsets). The commit
   * message and merge list (for merge commits) are also returned.
   *
   * @param project a project name representing a git repository
   * @param key the key containing information about the 2 commits and other attributes
   * @return the list of modified files between the 2 commits.
   * @throws ExecutionException
   */
  Map<String, FileInfo> getModifiedFilesIn(Project.NameKey project, PatchListKey key)
      throws PatchListNotAvailableException;

  /**
   * Returns the diff for a single file between 2 commits (patchsets).
   *
   * @param project a project name representing a git repository
   * @param key the key containing information about the 2 commits
   * @param fileName the file name for which the diff should be evaluated
   * @return the diff for the file between the 2 commits
   * @throws IOException
   */
  PatchListEntry getOneModifiedFile(Project.NameKey project, PatchListKey key, String fileName)
      throws IOException;
}
