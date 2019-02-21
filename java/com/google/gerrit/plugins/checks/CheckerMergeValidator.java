// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Repository;

public class CheckerMergeValidator implements MergeValidationListener {

  private final AllProjectsName allProjectsName;

  @Inject
  public CheckerMergeValidator(AllProjectsName allProjectsName) {
    this.allProjectsName = allProjectsName;
  }

  @Override
  public void onPreMerge(
      Repository repo,
      CodeReviewCommit commit,
      ProjectState destProject,
      Branch.NameKey destBranch,
      PatchSet.Id patchSetId,
      IdentifiedUser caller)
      throws MergeValidationException {
    if (!allProjectsName.equals(destProject.getNameKey())
        || !CheckerRef.isRefsCheckers(destBranch.get())) {
      return;
    }

    throw new MergeValidationException("submit to checker ref not allowed");
  }
}
