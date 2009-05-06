// Copyright (C) 2009 The Android Open Source Project
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

package org.spearce.jgit.diff;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class EditDeserializer implements JsonDeserializer<Edit>,
    JsonSerializer<Edit> {
  public Edit deserialize(final JsonElement json, final Type typeOfT,
      final JsonDeserializationContext context) throws JsonParseException {
    if (json.isJsonNull()) {
      return null;
    }
    if (!json.isJsonArray()) {
      throw new JsonParseException("Expected array of 4for Edit type");
    }

    final JsonArray a = (JsonArray) json;
    if (a.size() != 4) {
      throw new JsonParseException("Expected array of 4 for Edit type");
    }
    return new Edit(get(a, 0), get(a, 1), get(a, 2), get(a, 3));
  }

  private static int get(final JsonArray a, final int idx)
      throws JsonParseException {
    final JsonElement v = a.get(idx);
    if (!v.isJsonPrimitive()) {
      throw new JsonParseException("Expected array of 4 for Edit type");
    }
    final JsonPrimitive p = (JsonPrimitive) v;
    if (!p.isNumber()) {
      throw new JsonParseException("Expected array of 4 for Edit type");
    }
    return p.getAsInt();
  }

  public JsonElement serialize(final Edit src, final Type typeOfSrc,
      final JsonSerializationContext context) {
    if (src == null) {
      return new JsonNull();
    }
    final JsonArray a = new JsonArray();
    a.add(new JsonPrimitive(src.getBeginA()));
    a.add(new JsonPrimitive(src.getEndA()));
    a.add(new JsonPrimitive(src.getBeginB()));
    a.add(new JsonPrimitive(src.getEndB()));
    return a;
  }
}
