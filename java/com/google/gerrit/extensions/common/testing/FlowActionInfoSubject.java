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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.FlowActionInfo;

/** A Truth subject for {@link FlowActionInfo} instances. */
public class FlowActionInfoSubject extends Subject {
  private final FlowActionInfo flowActionInfo;

  public static FlowActionInfoSubject assertThat(FlowActionInfo flowActionInfo) {
    return assertAbout(flowActions()).that(flowActionInfo);
  }

  public static Factory<FlowActionInfoSubject, FlowActionInfo> flowActions() {
    return FlowActionInfoSubject::new;
  }

  private FlowActionInfoSubject(FailureMetadata metadata, FlowActionInfo flowActionInfo) {
    super(metadata, flowActionInfo);
    this.flowActionInfo = flowActionInfo;
  }

  public StringSubject hasNameThat() {
    return check("name()").that(flowActionInfo().name);
  }

  public IterableSubject hasParametersThat() {
    return check("parameters()").that(flowActionInfo().parameters);
  }

  private FlowActionInfo flowActionInfo() {
    isNotNull();
    return flowActionInfo;
  }
}
