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
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Optional;

/** Describe a applicability, blocking or override expression of a {@link SubmitRequirement}. */
@AutoValue
public abstract class SubmitRequirementExpression {

  public static SubmitRequirementExpression create(String expression) {
    return new AutoValue_SubmitRequirementExpression(expression);
  }

  /**
   * Creates a new {@link SubmitRequirementExpression}.
   *
   * @param expression String representation of the expression
   * @return empty {@link Optional} if the input expression is null or empty, or an Optional
   *     containing the expression otherwise.
   */
  public static Optional<SubmitRequirementExpression> of(@Nullable String expression) {
    return Optional.ofNullable(Strings.emptyToNull(expression))
        .map(SubmitRequirementExpression::create);
  }

  /** Returns the underlying String representing this {@link SubmitRequirementExpression}. */
  public abstract String expressionString();

  public static TypeAdapter<SubmitRequirementExpression> typeAdapter() {
    // return new AutoValue_SubmitRequirementExpression.GsonTypeAdapter(gson);
    return new GsonTypeAdapter();
  }

  static class GsonTypeAdapter extends TypeAdapter<SubmitRequirementExpression> {
    private static final String KEY_EXPRESSION_STRING = "expressionString";

    @Override
    public void write(JsonWriter out, SubmitRequirementExpression expression) throws IOException {
      out.beginObject();
      out.name("expressionString").value(expression.expressionString());
      out.endObject();
    }

    @Override
    public SubmitRequirementExpression read(JsonReader in) throws IOException {
      JsonObject parsed = new JsonParser().parse(in).getAsJsonObject();
      if (parsed.has("expressionString")) {
        return SubmitRequirementExpression.create(parsed.get("expressionString").getAsString());
      }
      return null;
    }
  }
}
