//  Copyright (C) 2021 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Optional;

/** Entity describing a requirement that should be met for a change to become submittable. */
@AutoValue
public abstract class SubmitRequirement {
  /** Requirement name. */
  public abstract String name();

  /** Description of what this requirement means. */
  public abstract Optional<String> description();

  /**
   * Expression of the condition that makes the requirement applicable. The expression should be
   * evaluated for a specific {@link Change} and if it returns false, the requirement becomes
   * irrelevant for the change (i.e. {@link #submittabilityExpression()} and {@link
   * #overrideExpression()} become irrelevant).
   *
   * <p>An empty {@link Optional} indicates that the requirement is applicable for any change.
   */
  public abstract Optional<SubmitRequirementExpression> applicabilityExpression();

  /**
   * Expression of the condition that allows the submission of a change. The expression should be
   * evaluated for a specific {@link Change} and if it returns true, the requirement becomes
   * fulfilled for the change.
   */
  public abstract SubmitRequirementExpression submittabilityExpression();

  /**
   * Expression that, if evaluated to true, causes the submit requirement to be fulfilled,
   * regardless of the submittability expression. This expression should be evaluated for a specific
   * {@link Change}.
   *
   * <p>An empty {@link Optional} indicates that the requirement is not overridable.
   */
  public abstract Optional<SubmitRequirementExpression> overrideExpression();

  /**
   * Boolean value indicating if the {@link SubmitRequirement} definition can be overridden in child
   * projects. Default is false.
   */
  public abstract boolean allowOverrideInChildProjects();

  public static SubmitRequirement.Builder builder() {
    return new AutoValue_SubmitRequirement.Builder();
  }

  public static TypeAdapter<SubmitRequirement> typeAdapter() {
    return new GsonTypeAdapter();
  }

  /** Json serializer for {@link SubmitRequirement}. */
  static class GsonTypeAdapter extends TypeAdapter<SubmitRequirement> {
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_APPLICABILITY_EXPRESSION = "applicabilityExpression";
    private static final String KEY_SUBMITTABILITY_EXPRESSION = "submittabilityExpression";
    private static final String KEY_OVERRIDE_EXPRESSION = "overrideExpression";
    private static final String KEY_ALLLOW_OVERRIDE_IN_CHILD_PROJECTS =
        "allowOverrideInChildProjects";

    @Override
    public void write(JsonWriter out, SubmitRequirement req) throws IOException {
      TypeAdapter<SubmitRequirementExpression> expressionAdapter =
          SubmitRequirementExpression.typeAdapter();
      out.beginObject();
      out.name(KEY_NAME).value(req.name());
      if (req.description().isPresent()) {
        out.name(KEY_DESCRIPTION).value(req.description().get());
      }
      if (req.applicabilityExpression().isPresent()) {
        out.name(KEY_APPLICABILITY_EXPRESSION)
            .jsonValue(expressionAdapter.toJson(req.applicabilityExpression().get()));
      }
      out.name(KEY_SUBMITTABILITY_EXPRESSION)
          .jsonValue(expressionAdapter.toJson(req.submittabilityExpression()));
      if (req.overrideExpression().isPresent()) {
        out.name(KEY_OVERRIDE_EXPRESSION)
            .jsonValue(expressionAdapter.toJson(req.overrideExpression().get()));
      }
      out.name(KEY_ALLLOW_OVERRIDE_IN_CHILD_PROJECTS).value(req.allowOverrideInChildProjects());
      out.endObject();
    }

    @Override
    public SubmitRequirement read(JsonReader in) throws IOException {
      JsonObject parsed = new JsonParser().parse(in).getAsJsonObject();
      Builder builder = SubmitRequirement.builder();
      if (parsed.has(KEY_NAME)) {
        builder.setName(unpack(parsed.get(KEY_NAME)).getAsString());
      }
      if (parsed.has(KEY_DESCRIPTION)) {
        builder.setDescription(Optional.of(unpack(parsed.get(KEY_DESCRIPTION)).getAsString()));
      }
      if (parsed.has(KEY_APPLICABILITY_EXPRESSION)) {
        builder.setApplicabilityExpression(
            Optional.of(deserializeExpression(parsed.get(KEY_APPLICABILITY_EXPRESSION))));
      }
      builder.setSubmittabilityExpression(
          deserializeExpression(parsed.get(KEY_SUBMITTABILITY_EXPRESSION)));
      if (parsed.has(KEY_OVERRIDE_EXPRESSION)) {
        builder.setOverrideExpression(
            Optional.of(deserializeExpression(parsed.get(KEY_OVERRIDE_EXPRESSION))));
      }
      builder.setAllowOverrideInChildProjects(
          unpack(parsed.get(KEY_ALLLOW_OVERRIDE_IN_CHILD_PROJECTS)).getAsBoolean());
      return builder.build();
    }

    private static SubmitRequirementExpression deserializeExpression(JsonElement elem) {
      return SubmitRequirementExpression.typeAdapter().fromJsonTree(unpack(elem));
    }

    /**
     * Unpack the {@code in} {@link JsonElement}, i.e. if the element has a single "value" child
     * return it. We've previously used the default Gson serializer for serializing submit
     * requirements entities. This unpacking is needed to preserve backward compatibility while
     * deserializing entities that were previously serialized by the default serializer.
     */
    private static JsonElement unpack(JsonElement in) {
      if (!in.isJsonObject()) {
        return in;
      }
      JsonObject asJsonObject = in.getAsJsonObject();
      return asJsonObject.has("value") && asJsonObject.size() == 1 ? asJsonObject.get("value") : in;
    }
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(String name);

    public abstract Builder setDescription(Optional<String> description);

    public abstract Builder setApplicabilityExpression(
        Optional<SubmitRequirementExpression> applicabilityExpression);

    public abstract Builder setSubmittabilityExpression(
        SubmitRequirementExpression submittabilityExpression);

    public abstract Builder setOverrideExpression(
        Optional<SubmitRequirementExpression> overrideExpression);

    public abstract Builder setAllowOverrideInChildProjects(boolean allowOverrideInChildProjects);

    public abstract SubmitRequirement build();
  }
}
