// Copyright 2008 Google Inc.
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
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

class SqlTimestampDeserializer implements JsonDeserializer<Timestamp>, JsonSerializer<Timestamp> {
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  @Override
  public Timestamp deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (json.isJsonNull()) {
      return null;
    }
    if (!json.isJsonPrimitive()) {
      throw new JsonParseException("Expected string for timestamp type");
    }
    JsonPrimitive p = (JsonPrimitive) json;
    if (!p.isString()) {
      throw new JsonParseException("Expected string for timestamp type");
    }

    return JavaSqlTimestampHelper.parseTimestamp(p.getAsString());
  }

  @Override
  public JsonElement serialize(Timestamp src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) {
      return JsonNull.INSTANCE;
    }
    return new JsonPrimitive(newFormat().format(src) + "000000");
  }

  private static SimpleDateFormat newFormat() {
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    f.setTimeZone(UTC);
    f.setLenient(true);
    return f;
  }
}
