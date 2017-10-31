// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;

/**
 * JSON deserializer for {@link Event}s.
 *
 * <p>Deserialized objects are of an appropriate subclass based on the value of the top-level "type"
 * element.
 */
public class EventDeserializer implements JsonDeserializer<Event> {
  @Override
  public Event deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (!json.isJsonObject()) {
      throw new JsonParseException("Not an object");
    }
    JsonElement typeJson = json.getAsJsonObject().get("type");
    if (typeJson == null
        || !typeJson.isJsonPrimitive()
        || !typeJson.getAsJsonPrimitive().isString()) {
      throw new JsonParseException("Type is not a string: " + typeJson);
    }
    String type = typeJson.getAsJsonPrimitive().getAsString();
    Class<?> cls = EventTypes.getClass(type);
    if (cls == null) {
      throw new JsonParseException("Unknown event type: " + type);
    }
    return context.deserialize(json, cls);
  }
}
