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

package com.google.gerrit.json;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.inject.TypeLiteral;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ImmutableListTypeAdapter implements JsonDeserializer<ImmutableList<?>> {

  // handle the situation when someone uses ImmutableList<?>
  private static final ParameterizedType IMMUTABLE_LIST_OF_UNKNOWN =
      (ParameterizedType) new TypeLiteral<ImmutableList<?>>() {}.getType();

  @Override
  public ImmutableList<?> deserialize(
      JsonElement jsonArrayElement,
      Type type,
      JsonDeserializationContext jsonDeserializationContext)
      throws JsonParseException {

    if (!jsonArrayElement.isJsonArray()) {
      throw new JsonParseException(
          "Expected a JSON Array being deserialized to an ImmutableList<>");
    }

    Type elementType =
        type instanceof ParameterizedType parameterizedType
            ? parameterizedType.getActualTypeArguments()[0]
            : IMMUTABLE_LIST_OF_UNKNOWN.getActualTypeArguments()[0];

    return getObjectBuilder(
            jsonArrayElement.getAsJsonArray(), jsonDeserializationContext, elementType)
        .build();
  }

  private static ImmutableList.Builder<Object> getObjectBuilder(
      JsonArray jsonArray,
      JsonDeserializationContext jsonDeserializationContext,
      Type elementType) {
    ImmutableList.Builder<Object> builder = ImmutableList.builder();
    jsonArray.forEach(
        el -> {
          if (el.isJsonNull()) {
            throw new JsonParseException(
                "ImmutableList<?> does not accept the null elements coming from Json array");
          }
          Object element = jsonDeserializationContext.deserialize(el, elementType);
          builder.add(element);
        });
    return builder;
  }
}
