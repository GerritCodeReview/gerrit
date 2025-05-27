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
import static com.google.gerrit.extensions.common.testing.FlowActionInfoSubject.flowActions;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.FlowExpressionInfo;

/** A Truth subject for {@link FlowExpressionInfo} instances. */
public class FlowExpressionInfoSubject extends Subject {
  private final FlowExpressionInfo flowExpressionInfo;

  public static FlowExpressionInfoSubject assertThat(FlowExpressionInfo flowExpressionInfo) {
    return assertAbout(flowExpressions()).that(flowExpressionInfo);
  }

  public static Factory<FlowExpressionInfoSubject, FlowExpressionInfo> flowExpressions() {
    return FlowExpressionInfoSubject::new;
  }

  private FlowExpressionInfoSubject(
      FailureMetadata metadata, FlowExpressionInfo flowExpressionInfo) {
    super(metadata, flowExpressionInfo);
    this.flowExpressionInfo = flowExpressionInfo;
  }

  public StringSubject hasConditionThat() {
    return check("condition()").that(flowExpressionInfo().condition);
  }

  public FlowActionInfoSubject hasActionThat() {
    return check("action()").about(flowActions()).that(flowExpressionInfo().action);
  }

  private FlowExpressionInfo flowExpressionInfo() {
    isNotNull();
    return flowExpressionInfo;
  }
}
