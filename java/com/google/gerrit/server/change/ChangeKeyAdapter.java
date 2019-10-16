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

package com.google.gerrit.server.change;

import com.google.gerrit.entities.Change;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

/**
 * Adapter that serializes {@link com.google.gerrit.entities.Change.Key}'s {@code key} field as
 * {@code id}, for backwards compatibility in stream-events.
 */
// TODO(dborowitz): auto-value-gson should support this directly using @SerializedName on the
// AutoValue method.
public class ChangeKeyAdapter implements JsonSerializer<Change.Key>, JsonDeserializer<Change.Key> {
  @Override
  public JsonElement serialize(Change.Key src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject obj = new JsonObject();
    obj.addProperty("id", src.get());
    return obj;
  }

  @Override
  public Change.Key deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    JsonElement keyJson = json.getAsJsonObject().get("id");
    if (keyJson == null || !keyJson.isJsonPrimitive() || !keyJson.getAsJsonPrimitive().isString()) {
      throw new JsonParseException("Key is not a string: " + keyJson);
    }
    String key = keyJson.getAsJsonPrimitive().getAsString();
    return Change.key(key);
  }
}
