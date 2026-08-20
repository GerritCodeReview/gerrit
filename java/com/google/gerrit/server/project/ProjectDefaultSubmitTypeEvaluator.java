// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

public class ProjectDefaultSubmitTypeEvaluator implements SubmitTypeEvaluator {
  private final ProjectCache projectCache;

  @Inject
  private ProjectDefaultSubmitTypeEvaluator(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  @Override
  public SubmitTypeRecord evaluate(ChangeData cd) {
    SubmitType defaultSubmitType = getProjectState(cd).getSubmitType();
    return SubmitTypeRecord.OK(defaultSubmitType);
  }

  private ProjectState getProjectState(ChangeData cd) {
    return projectCache
        .get(cd.project())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to find project while evaluating submit rule",
                    new NoSuchProjectException(cd.project())));
  }
}
