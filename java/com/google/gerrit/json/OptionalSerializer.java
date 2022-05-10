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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public class OptionalSerializer
    implements JsonSerializer<Optional<?>>, JsonDeserializer<Optional<?>> {

  @Override
  public JsonElement serialize(Optional<?> src, Type typeOfSrc, JsonSerializationContext context) {
    Optional<?> optional = src == null ? Optional.empty() : src;
    JsonObject json = new JsonObject();
    json.add("value", optional.map(context::serialize).orElse(JsonNull.INSTANCE));
    return json;
  }

  @Override
  public Optional<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    checkArgument(typeOfT instanceof ParameterizedType);
    ParameterizedType parameterizedType = (ParameterizedType) typeOfT;
    if (parameterizedType.getActualTypeArguments().length != 1) {
      throw new JsonParseException("Expected one parameter type in Optional.");
    }

    Type optionalOf = parameterizedType.getActualTypeArguments()[0];
    if (!json.getAsJsonObject().has("value")) {
      return Optional.empty();
    }

    JsonElement value = json.getAsJsonObject().get("value");
    if (value == null || value.isJsonNull()) {
      return Optional.empty();
    }

    return Optional.of(context.deserialize(value, optionalOf));
  }
}
