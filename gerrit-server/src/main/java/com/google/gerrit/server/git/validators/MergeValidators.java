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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Repository;

import java.util.List;

public class MergeValidators {
  private final DynamicSet<MergeValidationListener> mergeValidationListeners;
  private final Repository repo;

  public interface Factory {
    MergeValidators create(Repository repo);
  }

  @Inject
  MergeValidators(DynamicSet<MergeValidationListener> mergeValidationListeners,
      @Assisted Repository repo) {
    this.mergeValidationListeners = mergeValidationListeners;
    this.repo = repo;
  }

  public void validatePreMerge(CodeReviewCommit commit,
      ProjectState destProject,
      Branch.NameKey destBranch)
      throws MergeValidationException {
    List<MergeValidationListener> validators = Lists.newLinkedList();

    validators.add(new PluginMergeValidationListener(mergeValidationListeners));

    for (MergeValidationListener validator : validators) {
      validator.onPreMerge(commit, destProject, destBranch);
    }
  }

  /** Execute commit validation plug-ins */
  public static class PluginMergeValidationListener implements
      MergeValidationListener {
    private final DynamicSet<MergeValidationListener> mergeValidationListeners;

    public PluginMergeValidationListener(
        DynamicSet<MergeValidationListener> commitValidationListeners) {
      this.mergeValidationListeners = commitValidationListeners;
    }

    @Override
    public void onPreMerge(CodeReviewCommit commit,
        ProjectState destProject, Branch.NameKey destBranch)
        throws MergeValidationException {
      for (MergeValidationListener validator : mergeValidationListeners) {
        validator.onPreMerge(commit, destProject, destBranch);
      }
    }
  }
}
