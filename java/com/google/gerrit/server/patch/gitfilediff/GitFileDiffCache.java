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

package com.google.gerrit.server.patch.gitfilediff;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.patch.DiffNotAvailableException;

/** This cache computes pure git diff for a single file path according to a git tree diff. */
public interface GitFileDiffCache {

  /**
   * Returns the git file diff for a single file path identified by its key.
   *
   * @param key identifies 2 git trees, a specific file path and other diff parameters.
   * @return the file diff for a single file path identified by its key.
   * @throws DiffNotAvailableException if the tree IDs of the key are invalid for this project or if
   *     file contents could not be read.
   */
  GitFileDiff get(GitFileDiffCacheKey key) throws DiffNotAvailableException;

  /**
   * Returns the file diff for a collection of file paths identified by their keys.
   *
   * @param keys identifying different file paths of different projects.
   * @return a map of the input keys to their corresponding git file diffs.
   * @throws DiffNotAvailableException if the diff failed to be evaluated for one or more of the
   *     input keys due to invalid tree IDs or if file contents could not be read.
   */
  ImmutableMap<GitFileDiffCacheKey, GitFileDiff> getAll(Iterable<GitFileDiffCacheKey> keys)
      throws DiffNotAvailableException;
}
