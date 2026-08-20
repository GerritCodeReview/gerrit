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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

/** Describe a applicability of a {@link SubmitTypeOverride}. */
@AutoValue
public abstract class SubmitTypeOverrideExpression {

  public static SubmitTypeOverrideExpression create(String expression) {
    return new AutoValue_SubmitTypeOverrideExpression(expression);
  }

  /**
   * Creates a new {@link SubmitTypeOverrideExpression}.
   *
   * @param expression String representation of the expression
   * @return the applicableIf expression.
   */
  public static SubmitTypeOverrideExpression of(@Nullable String expression) {
    return SubmitTypeOverrideExpression.create(expression);
  }

  /** Returns the underlying String representing this {@link SubmitTypeOverrideExpression}. */
  public abstract String expressionString();

  public static TypeAdapter<SubmitTypeOverrideExpression> typeAdapter(Gson gson) {
    return new AutoValue_SubmitTypeOverrideExpression.GsonTypeAdapter(gson);
  }
}
