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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.extensions.common.LabelDefinitionInfo;
import com.google.gerrit.server.config.AllProjectsNameProvider;

public class LabelAssert {
  public static void assertCodeReviewLabel(LabelDefinitionInfo codeReviewLabel) {
    assertThat(codeReviewLabel.name).isEqualTo("Code-Review");
    assertThat(codeReviewLabel.projectName).isEqualTo(AllProjectsNameProvider.DEFAULT);
    assertThat(codeReviewLabel.function).isEqualTo(LabelFunction.MAX_WITH_BLOCK.getFunctionName());
    assertThat(codeReviewLabel.values)
        .containsExactly(
            "+2",
            "Looks good to me, approved",
            "+1",
            "Looks good to me, but someone else must approve",
            " 0",
            "No score",
            "-1",
            "I would prefer this is not merged as is",
            "-2",
            "This shall not be merged");
    assertThat(codeReviewLabel.defaultValue).isEqualTo(0);
    assertThat(codeReviewLabel.branches).isNull();
    assertThat(codeReviewLabel.canOverride).isTrue();
    assertThat(codeReviewLabel.copyAnyScore).isNull();
    assertThat(codeReviewLabel.copyMinScore).isTrue();
    assertThat(codeReviewLabel.copyMaxScore).isNull();
    assertThat(codeReviewLabel.copyAllScoresIfNoChange).isTrue();
    assertThat(codeReviewLabel.copyAllScoresIfNoCodeChange).isNull();
    assertThat(codeReviewLabel.copyAllScoresOnTrivialRebase).isTrue();
    assertThat(codeReviewLabel.copyAllScoresOnMergeFirstParentUpdate).isNull();
    assertThat(codeReviewLabel.copyValues).isNull();
    assertThat(codeReviewLabel.allowPostSubmit).isTrue();
    assertThat(codeReviewLabel.ignoreSelfApproval).isNull();
  }

  private LabelAssert() {}
}
