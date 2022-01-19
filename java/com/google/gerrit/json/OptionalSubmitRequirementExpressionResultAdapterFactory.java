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

import com.google.gerrit.entities.SubmitRequirementExpressionResult;
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
 * <p>This is needed for the backwards-compatibility when de-serializing the previously non-optional
 * {@code SubmitRequirementResult#submittabilityExpressionResult} field.
 */
public class OptionalSubmitRequirementExpressionResultAdapterFactory implements TypeAdapterFactory {

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
    if (!typeToken.equals(
        TypeToken.get(
            new TypeLiteral<Optional<SubmitRequirementExpressionResult>>() {}.getType()))) {
      // Not an Optional<SubmitRequirementExpressionResult>.
      return null;
    }
    gson.getAdapter(SubmitRequirementExpressionResult.class);
    TypeAdapter<SubmitRequirementExpressionResult> submitRequirementExpressionResultAdapter =
        gson.getAdapter(SubmitRequirementExpressionResult.class).nullSafe();

    return (TypeAdapter<T>)
        new OptionalSubmitRequirementResultTypeAdapter(submitRequirementExpressionResultAdapter);
  }

  private static class OptionalSubmitRequirementResultTypeAdapter
      extends TypeAdapter<Optional<SubmitRequirementExpressionResult>> {

    private final TypeAdapter<SubmitRequirementExpressionResult>
        submitRequirementExpressionResultAdapter;

    public OptionalSubmitRequirementResultTypeAdapter(
        TypeAdapter<SubmitRequirementExpressionResult> submitRequirementResultAdapter) {
      this.submitRequirementExpressionResultAdapter = submitRequirementResultAdapter;
    }

    @Override
    public Optional<SubmitRequirementExpressionResult> read(JsonReader in) throws IOException {
      JsonElement parsed = JsonParser.parseReader(in);
      if (parsed == null) {
        return Optional.empty();
      }
      // If it does not have 'value' field, then it was serialized as
      // SubmitRequirementExpressionResult directly
      if (parsed.getAsJsonObject().has("value")) {
        parsed = parsed.getAsJsonObject().get("value");
      }
      if (parsed == null || parsed.isJsonNull() || parsed.getAsJsonObject().entrySet().isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(submitRequirementExpressionResultAdapter.fromJsonTree(parsed));
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
}
