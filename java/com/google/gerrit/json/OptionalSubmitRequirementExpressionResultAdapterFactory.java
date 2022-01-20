// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.json;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.util.Optional;

/**
 * A {@code TypeAdapterFactory} for Optional {@code SubmitRequirementExpressionResult}.
 *
 * <p>{@link SubmitRequirementResult#submittabilityExpressionResult} was previously serialized as a
 * mandatory field, but was later on migrated to an optional field. The server needs to handle
 * deserializing of both formats.
 */
public class OptionalSubmitRequirementExpressionResultAdapterFactory implements TypeAdapterFactory {

  private static final TypeToken<?> OPTIONAL_SR_EXPRESSION_RESULT_TOKEN =
      TypeToken.get(new TypeLiteral<Optional<SubmitRequirementExpressionResult>>() {}.getType());

  private static final TypeToken<?> SR_EXPRESSION_RESULT_TOKEN =
      TypeToken.get(SubmitRequirementExpressionResult.class);

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
    if (typeToken.equals(OPTIONAL_SR_EXPRESSION_RESULT_TOKEN)) {
      return (TypeAdapter<T>)
          new OptionalSubmitRequirementExpressionResultTypeAdapter(
              SubmitRequirementExpressionResult.typeAdapter(gson));
    } else if (typeToken.equals(SR_EXPRESSION_RESULT_TOKEN)) {
      return (TypeAdapter<T>)
          new SubmitRequirementExpressionResultTypeAdapter(
              SubmitRequirementExpressionResult.typeAdapter(gson));
    }
    return null;
  }

  /**
   * Reads json representation of either {@code Optional<SubmitRequirementExpressionResult>} or
   * {@code SubmitRequirementExpressionResult}, converting it to {@code Nullable} {@code
   * SubmitRequirementExpressionResult}.
   */
  private static @Nullable SubmitRequirementExpressionResult readOptionalOrMandatory(
      TypeAdapter<SubmitRequirementExpressionResult> submitRequirementExpressionResultAdapter,
      JsonReader in) {
    JsonElement parsed = JsonParser.parseReader(in);
    if (parsed == null) {
      return null;
    }
    // If it does not have 'value' field, then it was serialized as
    // SubmitRequirementExpressionResult directly
    if (parsed.getAsJsonObject().has("value")) {
      parsed = parsed.getAsJsonObject().get("value");
    }
    if (parsed == null || parsed.isJsonNull() || parsed.getAsJsonObject().entrySet().isEmpty()) {
      return null;
    }
    return submitRequirementExpressionResultAdapter.fromJsonTree(parsed);
  }

  /**
   * A {@code TypeAdapter} that provides backward compatibility for reading previously non-optional
   * {@code SubmitRequirementExpressionResult} field.
   */
  private static class OptionalSubmitRequirementExpressionResultTypeAdapter
      extends TypeAdapter<Optional<SubmitRequirementExpressionResult>> {

    private final TypeAdapter<SubmitRequirementExpressionResult>
        submitRequirementExpressionResultAdapter;

    public OptionalSubmitRequirementExpressionResultTypeAdapter(
        TypeAdapter<SubmitRequirementExpressionResult> submitRequirementResultAdapter) {
      this.submitRequirementExpressionResultAdapter = submitRequirementResultAdapter;
    }

    @Override
    public Optional<SubmitRequirementExpressionResult> read(JsonReader in) throws IOException {
      return Optional.ofNullable(
          readOptionalOrMandatory(submitRequirementExpressionResultAdapter, in));
    }

    @Override
    public void write(JsonWriter out, Optional<SubmitRequirementExpressionResult> value)
        throws IOException {
      // Serialize the field using the same format used by the AutoValue's default Gson serializer.
      out.beginObject();
      out.name("value");
      if (value.isPresent()) {
        out.jsonValue(submitRequirementExpressionResultAdapter.toJson(value.get()));
      } else {
        out.nullValue();
      }
      out.endObject();
    }
  }

  /**
   * A {@code TypeAdapter} that provides forward compatibility for reading the optional {@code
   * SubmitRequirementExpressionResult} field.
   *
   * <p>TODO(mariasavtchouk): Remove once updated to read the new format only.
   */
  private static class SubmitRequirementExpressionResultTypeAdapter
      extends TypeAdapter<SubmitRequirementExpressionResult> {

    private final TypeAdapter<SubmitRequirementExpressionResult>
        submitRequirementExpressionResultAdapter;

    public SubmitRequirementExpressionResultTypeAdapter(
        TypeAdapter<SubmitRequirementExpressionResult> submitRequirementResultAdapter) {
      this.submitRequirementExpressionResultAdapter = submitRequirementResultAdapter;
    }

    @Override
    public SubmitRequirementExpressionResult read(JsonReader in) throws IOException {
      return readOptionalOrMandatory(submitRequirementExpressionResultAdapter, in);
    }

    @Override
    public void write(JsonWriter out, SubmitRequirementExpressionResult value) throws IOException {
      // Serialize the field using the same format used by the AutoValue's default Gson serializer.
      out.jsonValue(submitRequirementExpressionResultAdapter.toJson(value));
    }
  }
}
