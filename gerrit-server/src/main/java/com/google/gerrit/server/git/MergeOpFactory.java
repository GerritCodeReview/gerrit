// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeOpFactory implements MergeOp.Factory {

  private final MergeArguments mergeArguments;

  private static final Logger log =
      LoggerFactory.getLogger(MergeOpFactory.class);

  @Inject
  MergeOpFactory(final MergeArguments margs) {
    mergeArguments = margs;
  }

  @Override
  public MergeOp create(Branch.NameKey branch) throws MergeException {

    final ProjectState pe =
        mergeArguments.projectCache.get(branch.getParentKey());
    if (pe == null) {
      final String errorMsg = "No such project: " + branch.getParentKey();
      log.error(errorMsg);
      throw new MergeException(errorMsg);
    }

    final Project destProject = pe.getProject();

    switch (destProject.getSubmitType()) {
      case CHERRY_PICK:
        return new CherryPick(mergeArguments, destProject, branch);
      case FAST_FORWARD_ONLY:
        return new FastForwardOnly(mergeArguments, destProject, branch);
      case MERGE_ALWAYS:
        return new MergeAlways(mergeArguments, destProject, branch);
      case MERGE_IF_NECESSARY:
        return new MergeIfNecessary(mergeArguments, destProject, branch);
      default:
        final String errorMsg = "No such submit type: " + destProject.getSubmitType();
        log.error(errorMsg);
        throw new MergeException(errorMsg);
    }
  }
}
