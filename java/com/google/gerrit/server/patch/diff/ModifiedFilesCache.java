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

package com.google.gerrit.server.patch.diff;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCache;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheImpl;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheKey;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;

/**
 * A cache for the list of Git modified files between 2 commits (patchsets) with extra Gerrit logic.
 *
 * <p>The loader uses the underlying {@link GitModifiedFilesCacheImpl} to retrieve the git modified
 * files.
 *
 * <p>If the {@link ModifiedFilesCacheImpl.Key#aCommit()} is equal to {@link
 * org.eclipse.jgit.lib.Constants#EMPTY_TREE_ID}, the diff will be evaluated against the empty tree,
 * and the result will be exactly the same as the caller can get from {@link
 * GitModifiedFilesCache#get(GitModifiedFilesCacheKey)}
 */
public interface ModifiedFilesCache {

  /**
   * @param key used to identify two git commits and contains other attributes to control the diff
   *     calculation.
   * @return the list of {@link ModifiedFile}s between the 2 git commits identified by the key.
   * @throws DiffNotAvailableException the supplied commits IDs of the key do no exist, are not IDs
   *     of a commit, or an exception occurred while reading a pack file.
   */
  ImmutableList<ModifiedFile> get(ModifiedFilesCacheImpl.Key key) throws DiffNotAvailableException;
}
