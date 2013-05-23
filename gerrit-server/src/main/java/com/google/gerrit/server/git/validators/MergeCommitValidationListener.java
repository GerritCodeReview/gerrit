// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.project.ProjectState;

/**
 * Listener to provide validation of commits before merging.
 *
 * Invoked by Gerrit before a commit is merged.
 */
@ExtensionPoint
public interface MergeCommitValidationListener {
  /**
   * Commit validation.
   *
   * @param commit commit details
   * @param destProject the destination project
   * @param destBranch the destination branch
   * @throws MergeCommitValidationException if the commit fails to validate
   */
  public void onPreMergeCommit(final CodeReviewCommit commit,
      final ProjectState destProject,
      final Branch.NameKey destBranch)
      throws MergeCommitValidationException;
}
