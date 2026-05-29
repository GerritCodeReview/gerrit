// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

public class MergeInput {
  /**
   * {@code source} can be any Git object reference expression.
   *
   * @see <a
   *     href="https://www.kernel.org/pub/software/scm/git/docs/gitrevisions.html">gitrevisions(7)</a>
   */
  public String source;

  /**
   * If specified, visibility of the {@code source} commit will only be checked against {@code
   * source_branch}, rather than all visible branches.
   */
  public String sourceBranch;

  /**
   * {@code strategy} name of the merge strategy.
   *
   * @see org.eclipse.jgit.merge.MergeStrategy
   */
  public String strategy;

  /**
   * Whether the creation of the merge should succeed if there are conflicts.
   *
   * <p>If there are conflicts the file contents of the created change contain git conflict markers
   * to indicate the conflicts.
   */
  public boolean allowConflicts;
}
