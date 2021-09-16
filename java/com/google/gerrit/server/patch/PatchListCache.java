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

import com.google.gerrit.entities.Project;

/** Provides a cached list of {@link PatchListEntry}. */
public interface PatchListCache {
  /**
   * Returns the patch list - list of modified files - between two commits.
   *
   * @param key identifies the old / new commits.
   * @param project name key identifying a specific git project (repository).
   * @return patch list containing the modified files between two commits.
   * @deprecated use {@link DiffOperations} instead.
   */
  @Deprecated
  PatchList get(PatchListKey key, Project.NameKey project) throws PatchListNotAvailableException;

  IntraLineDiff getIntraLineDiff(IntraLineDiffKey key, IntraLineDiffArgs args);

  DiffSummary getDiffSummary(DiffSummaryKey key, Project.NameKey project)
      throws PatchListNotAvailableException;
}
