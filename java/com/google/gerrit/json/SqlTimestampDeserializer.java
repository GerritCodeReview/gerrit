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

import com.google.gerrit.common.Nullable;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TimeZone;

class SqlTimestampDeserializer implements JsonDeserializer<Timestamp>, JsonSerializer<Timestamp> {
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS", Locale.US).withZone(ZoneOffset.UTC);

  /**
   * {@link #FORMAT} gives the same text as {@link #newFormat()} from this instant on: before it,
   * SimpleDateFormat uses the Julian calendar.
   */
  private static final Instant GREGORIAN_START = Instant.parse("1582-10-15T00:00:00Z");

  /** From this instant on, {@link #FORMAT} prints a '+' before the year. */
  private static final Instant YEAR_10000 = Instant.parse("+10000-01-01T00:00:00Z");

  @Nullable
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

    String input = p.getAsString();
    if (input.trim().isEmpty()) {
      // Magic timestamp to indicate no timestamp. (-> null object)
      // Always create a new object as timestamps are mutable. Don't use TimeUtil.never() to not
      // introduce an undesired dependency.
      return new Timestamp(0);
    }

    return JavaSqlTimestampHelper.parseTimestamp(input);
  }

  @Override
  public JsonElement serialize(Timestamp src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) {
      return JsonNull.INSTANCE;
    }
    Instant t = src.toInstant();
    String formatted =
        t.isBefore(GREGORIAN_START) || !t.isBefore(YEAR_10000)
            ? newFormat().format(src)
            : FORMAT.format(t);
    return new JsonPrimitive(formatted + "000000");
  }

  private static SimpleDateFormat newFormat() {
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    f.setTimeZone(UTC);
    f.setLenient(true);
    return f;
  }
}
