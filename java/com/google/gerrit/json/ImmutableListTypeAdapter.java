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
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.inject.TypeLiteral;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ImmutableListTypeAdapter implements JsonDeserializer<ImmutableList<?>> {

  @Override
  public ImmutableList<?> deserialize(
      JsonElement jsonArrayElement,
      Type type,
      JsonDeserializationContext jsonDeserializationContext)
      throws JsonParseException {
    if (type instanceof ParameterizedType parameterizedType) {
      // handle the situation when someone uses ImmutableList<?>
      ParameterizedType immutableListOfUnknown =
          (ParameterizedType) new TypeLiteral<ImmutableList<?>>() {}.getType();

      ImmutableList.Builder<Object> builder = ImmutableList.builder();
      Type elementType =
          parameterizedType.getActualTypeArguments().length == 1
              ? parameterizedType.getActualTypeArguments()[0]
              : immutableListOfUnknown.getActualTypeArguments()[0];

      jsonArrayElement
          .getAsJsonArray()
          .forEach(
              (JsonElement jsonElement) -> {
                Object element = jsonDeserializationContext.deserialize(jsonElement, elementType);
                builder.add(element);
              });
      return builder.build();
    } else {
      throw new JsonParseException("ImmutableList<E> expected to have a parametrized type");
    }
  }
}
