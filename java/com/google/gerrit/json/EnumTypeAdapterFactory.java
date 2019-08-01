// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/**
 * A {@code TypeAdapterFactory} for enums.
 *
 * <p>This factory introduces a wrapper around Gson's own default enum handler to add the following
 * special behavior: log when input which doesn't match any existing enum value is encountered.
 */
public class EnumTypeAdapterFactory implements TypeAdapterFactory {

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
    TypeAdapter<T> defaultEnumAdapter = TypeAdapters.ENUM_FACTORY.create(gson, typeToken);
    if (defaultEnumAdapter == null) {
      // Not an enum. -> Enum type adapter doesn't apply.
      return null;
    }

    return new EnumTypeAdapter(defaultEnumAdapter, typeToken);
  }

  private static class EnumTypeAdapter<T extends Enum<T>> extends TypeAdapter<T> {

    private final TypeAdapter<T> defaultEnumAdapter;
    private final TypeToken<T> typeToken;

    public EnumTypeAdapter(TypeAdapter<T> defaultEnumAdapter, TypeToken<T> typeToken) {
      this.defaultEnumAdapter = defaultEnumAdapter;
      this.typeToken = typeToken;
    }

    @Override
    public T read(JsonReader in) throws IOException {
      // Still handle null values. -> Check them first.
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      T enumValue = defaultEnumAdapter.read(in);
      if (enumValue == null) {
        throw new JsonSyntaxException(
            String.format("Expected an existing value for enum %s.", typeToken));
      }
      return enumValue;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
      defaultEnumAdapter.write(out, value);
    }
  }
}
