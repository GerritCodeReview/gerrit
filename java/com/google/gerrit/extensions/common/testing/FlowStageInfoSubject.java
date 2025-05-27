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

package com.google.gerrit.extensions.common.testing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.extensions.common.testing.FlowExpressionInfoSubject.flowExpressions;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.FlowStageInfo;
import com.google.gerrit.extensions.common.FlowStageStatus;

/** A Truth subject for {@link FlowStageInfo} instances. */
public class FlowStageInfoSubject extends Subject {
  private final FlowStageInfo flowStageInfo;

  public static FlowStageInfoSubject assertThat(FlowStageInfo flowStageInfo) {
    return assertAbout(flowStages()).that(flowStageInfo);
  }

  public static Factory<FlowStageInfoSubject, FlowStageInfo> flowStages() {
    return FlowStageInfoSubject::new;
  }

  private FlowStageInfoSubject(FailureMetadata metadata, FlowStageInfo flowStageInfo) {
    super(metadata, flowStageInfo);
    this.flowStageInfo = flowStageInfo;
  }

  public FlowExpressionInfoSubject hasExpressionThat() {
    return check("expression()").about(flowExpressions()).that(flowStageInfo().expression);
  }

  public ComparableSubject<FlowStageStatus> hasStatusThat() {
    return check("status()").that(flowStageInfo().status);
  }

  private FlowStageInfo flowStageInfo() {
    isNotNull();
    return flowStageInfo;
  }
}
