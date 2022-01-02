// Copyright 2022 Google Inc.
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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class InstantDeserializer implements JsonDeserializer<Instant>, JsonSerializer<Instant> {
  private static final String YYYY_MM_DD_HH_MM_SS_SSS = "yyyy-MM-dd HH:mm:ss.SSS";

  @Override
  public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (json.isJsonNull()) {
      return null;
    }
    if (!json.isJsonPrimitive()) {
      throw new JsonParseException("Expected string for instant type");
    }
    JsonPrimitive p = (JsonPrimitive) json;
    if (!p.isString()) {
      throw new JsonParseException("Expected string for instant type");
    }

    String input = p.getAsString();
    if (input.trim().isEmpty()) {
      return Instant.ofEpochMilli(0);
    }

    return DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS_SSS)
        .withZone(ZoneId.of("UTC"))
        .parse(input, Instant::from);
  }

  @Override
  public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) {
      return JsonNull.INSTANCE;
    }
    return new JsonPrimitive(
        DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS_SSS)
            .withZone(ZoneId.of("UTC"))
            .format(src));
  }
}
