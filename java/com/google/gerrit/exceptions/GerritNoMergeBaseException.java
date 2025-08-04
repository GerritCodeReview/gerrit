// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.exceptions;

/**
 * Exception that represents a NoMergeBaseException from JGit that should be mapped to a user error
 * (e.g. 4xx), because it is triggered by the repository state or user input (e.g. the specified
 * merge strategy).
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>There are multiple merge bases and the merge strategy doesn't support multiple merge bases.
 *   <li>There are conflicts when merging the merge bases and the merge strategy doesn't support
 *       merging bases when there are conflicts.
 *   <li>The are too many merge bases.
 * </ul>
 *
 * <p>If this exception happens the intended merge is not support. The caller may retry using a
 * different merge strategy.
 */
public class GerritNoMergeBaseException extends Exception {
  private static final long serialVersionUID = 1L;

  public GerritNoMergeBaseException(
      org.eclipse.jgit.errors.NoMergeBaseException jgitNoMergeBaseException) {
    super(
        String.format("Cannot create merge commit: %s", jgitNoMergeBaseException.getMessage()),
        jgitNoMergeBaseException.getCause());
  }
}
