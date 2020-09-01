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

package com.google.gerrit.server.patch.gitdiff;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.diff.ModifiedFilesCache;

/**
 * A cache interface for identifying the list of Git modified files between 2 different git trees.
 * This cache does not read the actual file contents, nor does it include the edits (modified
 * regions) of the file.
 *
 * <p>The other {@link ModifiedFilesCache} is similar to this cache, and includes other extra Gerrit
 * logic that we need to add with the list of modified files.
 */
public interface GitModifiedFilesCache {

  /**
   * Computes the list of of {@link ModifiedFile}s between the 2 git trees.
   *
   * @param key used to identify two git trees and contains other attributes to control the diff
   *     calculation.
   * @return the list of {@link ModifiedFile}s between the 2 git trees identified by the key.
   * @throws DiffNotAvailableException trees cannot be read or file contents cannot be read.
   */
  ImmutableList<ModifiedFile> get(GitModifiedFilesCacheImpl.Key key)
      throws DiffNotAvailableException;
}
