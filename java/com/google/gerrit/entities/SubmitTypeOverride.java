// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

/**
 * Entity describing a submit type and the set of refs it should be applied to. It will override the
 * project-wide setting when submitting to matching refs.
 */
@ExtensionPoint
@AutoValue
public abstract class SubmitTypeOverride {
  /** Submit type. */
  public abstract SubmitType type();

  /**
   * Expression of the condition that makes the submit type applicable. The expression should be
   * evaluated for a specific {@link Change} and if it returns false, the project-wide submit type
   * will be used.
   */
  public abstract SubmitTypeOverrideExpression applicabilityExpression();

  public static Builder builder() {
    return new AutoValue_SubmitTypeOverride.Builder();
  }

  public abstract Builder toBuilder();

  public static TypeAdapter<SubmitTypeOverride> typeAdapter(Gson gson) {
    return new AutoValue_SubmitTypeOverride.GsonTypeAdapter(gson);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setType(SubmitType type);

    public abstract Builder setApplicabilityExpression(
        SubmitTypeOverrideExpression applicabilityExpression);

    public abstract SubmitTypeOverride build();
  }
}
